"""
MCP（Model Context Protocol）stdio 服务器。

将 src.tools 中的自动化测试工具暴露为 MCP Server，
支持被 Claude Desktop、Cursor、Trae 等 MCP Client 调用。
"""

import asyncio
import json
from typing import Any

from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import CallToolResult, TextContent, Tool

from src.tools import automation
from src.tools.schemas import AUTOMATION_TOOLS


# 构建 name -> function 映射，用于处理 call_tool
_TOOL_FUNCTIONS: dict[str, Any] = {
    name: getattr(automation, name)
    for name in (
        "backend_login",
        "run_automation_test",
        "get_automation_task",
        "retry_automation_task",
        "delete_automation_task",
        "list_automation_tasks",
        "direct_test",
        "list_api_cases",
        "debug_api_case",
    )
}


def create_mcp_server() -> Server:
    """创建并配置 MCP Server 实例。"""
    server = Server("agent")

    @server.list_tools()
    async def list_tools() -> list[Tool]:
        return [Tool(**schema) for schema in AUTOMATION_TOOLS]

    @server.call_tool()
    async def call_tool(name: str, arguments: dict[str, Any]) -> list[TextContent]:
        if name not in _TOOL_FUNCTIONS:
            return [
                TextContent(
                    type="text",
                    text=json.dumps(
                        {"code": 404, "message": f"未知工具: {name}"},
                        ensure_ascii=False,
                    ),
                )
            ]

        func = _TOOL_FUNCTIONS[name]
        try:
            # 工具函数多为同步 HTTP 调用，在线程池中执行避免阻塞事件循环
            result = await asyncio.to_thread(func, **arguments)
        except Exception as exc:  # noqa: BLE001
            result = {"code": 500, "message": f"工具执行异常: {exc}"}

        return [
            TextContent(
                type="text",
                text=json.dumps(result, ensure_ascii=False, default=str),
            )
        ]

    return server


async def run_stdio_server() -> None:
    """启动 MCP stdio 服务。"""
    server = create_mcp_server()
    async with stdio_server() as (read_stream, write_stream):
        await server.run(
            read_stream,
            write_stream,
            server.create_initialization_options(),
        )


if __name__ == "__main__":
    asyncio.run(run_stdio_server())
