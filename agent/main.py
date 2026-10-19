"""
agent 统一入口。

通过子命令聚合所有能力：
  chat    与 LangGraph ReAct Agent 对话
  ingest  导入文档到 Chroma 向量库
  tool    直接调用单个工具（调试/脚本化）
  mcp     启动 MCP stdio 服务，对外暴露工具
  config  查看当前生效配置

示例：
  uv run python main.py chat -q "执行测试集 TS_001"
  uv run python main.py ingest -f data/manual.pdf
  uv run python main.py tool list_api_cases -p '{"pageSize": 5}'
  uv run python main.py mcp
  uv run python main.py config
"""

import argparse
import asyncio
import json
import os
import uuid
from typing import Any

from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.messages import AIMessage

from src.agent import create_agent
from src.config import config


class StepPrinter(BaseCallbackHandler):
    """执行过程回调：打印 LLM 决策与工具调用过程，替代有 bug 的 ConsoleCallbackHandler。

    实现原则：
    - 只打印、不抛异常，避免调试日志中断 Agent 主流程。
    - 兼容普通回答、工具调用、流式/非流式 LLM 返回。
    - 摘要输出，防止大结果刷屏。
    - verbose=False 时只打印工具调用链（默认），verbose=True 额外打印 LLM 思考内容。
    """

    def __init__(self, verbose: bool = False):
        super().__init__()
        self.verbose = verbose

    def on_llm_end(self, response, *, run_id, parent_run_id=None, **kwargs):
        try:
            message = None
            if hasattr(response, "generations") and response.generations:
                first_gen = response.generations[0]
                # 兼容两种格式：generations=[ChatGeneration(...)] 或 [[ChatGeneration(...)]]
                generation = first_gen[0] if isinstance(first_gen, list) else first_gen
                message = getattr(generation, "message", None)
            elif isinstance(response, AIMessage):
                message = response

            if not isinstance(message, AIMessage):
                return

            if message.tool_calls:
                for tc in message.tool_calls:
                    name = tc.get("name") if isinstance(tc, dict) else getattr(tc, "name", "unknown")
                    args = tc.get("args") if isinstance(tc, dict) else getattr(tc, "args", {})
                    print(f"\n[Agent] 决定调用工具: {name}")
                    print(f"[Agent] 参数: {args}")
            elif message.content and self.verbose:
                content = message.content
                if isinstance(content, list):
                    content = "\n".join(str(c) for c in content)
                print(f"\n[Agent] 思考/回答: {content[:200]}")
        except Exception as exc:  # noqa: BLE001
            print(f"\n[debug] 解析 LLM 输出时出错: {exc}")

    def on_tool_start(self, serialized, input_str, *, run_id, parent_run_id=None, **kwargs):
        try:
            name = "unknown"
            if isinstance(serialized, dict):
                name = serialized.get("name", "unknown")
            elif serialized is not None:
                name = getattr(serialized, "name", "unknown")
            print(f"\n[Tool] 开始执行: {name}")
            print(f"[Tool] 输入: {input_str}")
        except Exception as exc:  # noqa: BLE001
            print(f"\n[debug] 打印工具开始时出错: {exc}")

    def on_tool_end(self, output, *, run_id, parent_run_id=None, **kwargs):
        try:
            text = str(output)
            print(f"\n[Tool] 返回结果（摘要）: {text[:500]}{'...' if len(text) > 500 else ''}")
        except Exception as exc:  # noqa: BLE001
            print(f"\n[debug] 打印工具结果时出错: {exc}")


def _build_invoke_config(thread_id: str, callbacks: list[Any] | None = None) -> dict[str, Any]:
    """构造 Agent.invoke 的配置，包含会话 ID、循环上限与可选回调。"""
    # ReAct 每轮至少包含 agent + tools 两个节点，
    # +1 用于最终 agent 节点返回结果。
    recursion_limit = config.agent.max_iterations * 2 + 1
    config_dict: dict[str, Any] = {
        "configurable": {"thread_id": thread_id},
        "recursion_limit": recursion_limit,
    }
    if callbacks:
        config_dict["callbacks"] = callbacks
    return config_dict


def _run_chat_query(agent, query: str, thread_id: str, callbacks: list[Any] | None = None) -> None:
    """执行单次查询并打印最终回答。"""
    response = agent.invoke(
        {"messages": [("human", query)]},
        config=_build_invoke_config(thread_id, callbacks=callbacks),
    )
    print(response["messages"][-1].content)


def _run_chat_interactive(agent, thread_id: str, callbacks: list[Any] | None = None) -> None:
    """进入交互式对话循环。"""
    print(f"Agent 已启动。当前会话 ID：{thread_id}")
    print("输入问题开始对话，输入 exit / quit / 退出 结束。\n")

    while True:
        try:
            query = input("User: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n再见。")
            break

        if not query:
            continue
        if query.lower() in {"exit", "quit", "退出"}:
            print("再见。")
            break

        _run_chat_query(agent, query, thread_id, callbacks=callbacks)
        print()


def cmd_chat(args: argparse.Namespace) -> None:
    """chat 子命令：单次查询或交互模式。"""
    # 默认开启简版执行过程可见性；--debug 开启完整 LLM 思考内容
    callbacks: list[Any] = [StepPrinter(verbose=args.debug)]
    if args.debug:
        print("[debug] 已开启详细执行过程打印，将显示 Agent 思考、工具决策与工具调用链。\n")

    agent = create_agent()
    thread_id = args.thread_id or str(uuid.uuid4())

    if args.query:
        _run_chat_query(agent, args.query, thread_id, callbacks=callbacks)
    else:
        _run_chat_interactive(agent, thread_id, callbacks=callbacks)


def cmd_ingest(args: argparse.Namespace) -> None:
    """ingest 子命令：导入文件或目录到 Chroma。"""
    try:
        from src.rag import rag
    except ValueError as exc:
        print(f"向量库未配置，无法导入：{exc}")
        raise SystemExit(1) from exc

    if args.file:
        try:
            count = rag.ingest(args.file)
            print(f"已导入 {count} 个文档块：{args.file}")
        except Exception as exc:  # noqa: BLE001
            print(f"导入失败：{exc}")
            raise SystemExit(1) from exc

    elif args.dir:
        total = 0
        for root, _, files in os.walk(args.dir):
            for filename in files:
                path = os.path.join(root, filename)
                try:
                    count = rag.ingest(path)
                    total += count
                    print(f"已导入 {count:4d} 块：{path}")
                except Exception as exc:  # noqa: BLE001
                    print(f"跳过 {path}：{exc}")
        print(f"\n总计导入 {total} 个文档块。")


def cmd_tool(args: argparse.Namespace) -> None:
    """tool 子命令：直接调用某个工具并打印 JSON 结果。"""
    from src.tools import automation

    func = getattr(automation, args.name, None)
    if func is None:
        print(f"未知工具：{args.name}")
        raise SystemExit(1)

    try:
        params = json.loads(args.params) if args.params else {}
    except json.JSONDecodeError as exc:
        print(f"参数 JSON 解析失败：{exc}")
        raise SystemExit(1) from exc

    try:
        result = func(**params)
    except Exception as exc:  # noqa: BLE001
        result = {"code": 500, "message": f"工具执行异常：{exc}"}

    print(json.dumps(result, ensure_ascii=False, indent=2, default=str))


def cmd_mcp(_args: argparse.Namespace) -> None:
    """mcp 子命令：启动 MCP stdio 服务。"""
    from src import mcp_server

    asyncio.run(mcp_server.run_stdio_server())


def cmd_server(args: argparse.Namespace) -> None:
    """server 子命令：启动 FastAPI HTTP 服务。"""
    import uvicorn

    from src.server import app

    uvicorn.run(app, host=args.host, port=args.port)


def _mask_value(key: str, value: Any, show_secrets: bool) -> Any:
    """默认对敏感配置做脱敏处理。"""
    if show_secrets:
        return value
    sensitive_keys = {"api_key", "token", "password", "secret"}
    if any(s in key.lower() for s in sensitive_keys) and isinstance(value, str) and value:
        return value[:4] + "****" + value[-4:] if len(value) > 8 else "****"
    return value


def cmd_config(args: argparse.Namespace) -> None:
    """config 子命令：打印当前生效配置。"""
    data = config.model_dump()

    def mask(data: dict[str, Any]) -> dict[str, Any]:
        return {
            k: (
                mask(v)
                if isinstance(v, dict)
                else _mask_value(k, v, args.show_secrets)
            )
            for k, v in data.items()
        }

    print(json.dumps(mask(data), ensure_ascii=False, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(
        prog="agent",
        description="agent 统一入口，支持对话、导入、工具调用、MCP 服务。",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    # ---- chat ----
    chat_parser = subparsers.add_parser("chat", help="与 LangGraph Agent 对话")
    chat_parser.add_argument(
        "--query", "-q", type=str, help="单次查询模式：直接传入用户问题"
    )
    chat_parser.add_argument(
        "--thread-id", "-t", type=str, help="会话 ID，用于恢复历史对话"
    )
    chat_parser.add_argument(
        "--debug",
        "-d",
        action="store_true",
        help="显示完整 LLM 思考内容（默认已显示工具调用链）",
    )
    chat_parser.set_defaults(func=cmd_chat)

    # ---- ingest ----
    ingest_parser = subparsers.add_parser(
        "ingest", help="导入文档到 Chroma 向量库"
    )
    ingest_group = ingest_parser.add_mutually_exclusive_group(required=True)
    ingest_group.add_argument("--file", "-f", type=str, help="单个文件路径")
    ingest_group.add_argument("--dir", "-d", type=str, help="目录路径")
    ingest_parser.set_defaults(func=cmd_ingest)

    # ---- tool ----
    tool_parser = subparsers.add_parser("tool", help="直接调用某个工具")
    tool_parser.add_argument("name", type=str, help="工具名，例如 list_api_cases")
    tool_parser.add_argument(
        "--params",
        "-p",
        type=str,
        default="{}",
        help="工具参数 JSON 字符串，例如 '{\"pageSize\": 5}'",
    )
    tool_parser.set_defaults(func=cmd_tool)

    # ---- mcp ----
    mcp_parser = subparsers.add_parser("mcp", help="启动 MCP stdio 服务")
    mcp_parser.set_defaults(func=cmd_mcp)

    # ---- server ----
    server_parser = subparsers.add_parser("server", help="启动 FastAPI HTTP 服务")
    server_parser.add_argument(
        "--host", type=str, default="0.0.0.0", help="监听地址（默认 0.0.0.0）"
    )
    server_parser.add_argument(
        "--port", type=int, default=8000, help="监听端口（默认 8000）"
    )
    server_parser.set_defaults(func=cmd_server)

    # ---- config ----
    config_parser = subparsers.add_parser("config", help="查看当前生效配置")
    config_parser.add_argument(
        "--show-secrets",
        action="store_true",
        help="显示敏感信息（默认对 api_key/token/password 脱敏）",
    )
    config_parser.set_defaults(func=cmd_config)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
