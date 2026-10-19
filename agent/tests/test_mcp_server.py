"""
MCP 服务器测试。
"""

import json
from unittest.mock import MagicMock

import pytest
from mcp import types

from src import mcp_server


class TestMCPServer:
    """测试 MCP Server 工具列表与调用。"""

    @pytest.mark.asyncio
    async def test_list_tools_returns_all_schemas(self):
        """list_tools 应返回所有已定义的 MCP 工具 Schema。"""
        server = mcp_server.create_mcp_server()
        response = await server.request_handlers[types.ListToolsRequest](
            types.ListToolsRequest()
        )
        tools = response.root.tools

        assert len(tools) == len(mcp_server.AUTOMATION_TOOLS)
        tool_names = {t.name for t in tools}
        assert "run_automation_test" in tool_names
        assert "list_api_cases" in tool_names

    @pytest.mark.asyncio
    async def test_call_tool_runs_automation_function(self, monkeypatch):
        """call_tool 应正确分发到对应函数并返回 JSON 结果。"""
        server = mcp_server.create_mcp_server()

        mock_func = MagicMock(return_value={"code": 200, "data": "ok"})
        monkeypatch.setitem(mcp_server._TOOL_FUNCTIONS, "test_tool", mock_func)

        req = types.CallToolRequest(
            params=types.CallToolRequestParams(name="test_tool", arguments={"x": 1})
        )
        response = await server.request_handlers[types.CallToolRequest](req)

        result = response.root.content
        assert len(result) == 1
        assert result[0].type == "text"
        payload = json.loads(result[0].text)
        assert payload["code"] == 200
        mock_func.assert_called_once_with(x=1)

    @pytest.mark.asyncio
    async def test_call_tool_unknown_tool_returns_404(self):
        """调用未知工具应返回 404。"""
        server = mcp_server.create_mcp_server()
        req = types.CallToolRequest(
            params=types.CallToolRequestParams(name="unknown_tool", arguments={})
        )
        response = await server.request_handlers[types.CallToolRequest](req)

        result = response.root.content
        assert len(result) == 1
        payload = json.loads(result[0].text)
        assert payload["code"] == 404
