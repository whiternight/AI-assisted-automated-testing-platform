"""
agent HTTP 服务入口。

暴露 FastAPI 接口供 frontend 前端调用：
- GET  /api/agent/health   健康检查
- POST /api/agent/chat     SSE 流式对话

SSE 事件：
- progress   当前执行阶段提示（分析/调用工具/等待工具/生成回答）
- thinking   Agent 思考/推理过程与工具决策摘要，展示在「运行过程」区域
- tool_start 工具开始执行
- tool_end   工具执行结束（输出摘要，最多 500 字符）
- answer     LLM 最终回答内容（按 token/小段多次发送，提供流式视觉反馈）
- error      执行错误
- done       流结束，携带 thread_id
"""

import asyncio
import json
import time
import uuid
from typing import Any

from fastapi import FastAPI, Header, HTTPException, Request

from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, AIMessageChunk, ToolMessage
from pydantic import BaseModel

from src.agent import create_agent
from src.config import config
from src.tools.client import client as backend_client


app = FastAPI(title="agent HTTP API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://127.0.0.1",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class ChatRequest(BaseModel):
    thread_id: str | None = None
    message: str
    debug: bool = False


def _build_invoke_config(thread_id: str) -> dict[str, Any]:
    """构造 Agent.stream 的配置，包含会话 ID 与循环上限。"""
    recursion_limit = config.agent.max_iterations * 2 + 1
    return {
        "configurable": {"thread_id": thread_id},
        "recursion_limit": recursion_limit,
    }


def _extract_bearer_token(authorization: str | None) -> str | None:
    """从 Authorization 头中提取 Bearer token。"""
    if not authorization:
        return None
    parts = authorization.split()
    if len(parts) != 2 or parts[0].lower() != "bearer":
        return None
    return parts[1]


def _format_sse(event: str, data: dict[str, Any]) -> str:
    """将事件格式化为 text/event-stream 行。"""
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def _extract_text(content: Any) -> str:
    """统一提取消息中的文本内容。"""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict) and item.get("type") == "text":
                parts.append(str(item.get("text", "")))
            else:
                parts.append(str(item))
        return "".join(parts)
    return str(content) if content is not None else ""


def _extract_tool_calls(msg: AIMessage | AIMessageChunk) -> list[dict[str, Any]]:
    """从 AIMessage 或 AIMessageChunk 中提取工具调用列表。"""
    calls: list[dict[str, Any]] = []
    # 完整消息
    for tc in getattr(msg, "tool_calls", []) or []:
        if isinstance(tc, dict):
            calls.append({"name": tc.get("name", "unknown"), "args": tc.get("args", {})})
        else:
            calls.append({
                "name": getattr(tc, "name", "unknown"),
                "args": getattr(tc, "args", {}),
            })
    # 流式分块
    for tc in getattr(msg, "tool_call_chunks", []) or []:
        if isinstance(tc, dict):
            calls.append({"name": tc.get("name", "unknown") or "unknown", "args": {}})
        else:
            calls.append({
                "name": getattr(tc, "name", None) or "unknown",
                "args": {},
            })
    return calls


def _split_answer_stream(content: str) -> list[str]:
    """将最终回答拆分为小段，提供平滑的流式视觉反馈。

    拆分规则：优先按语义边界（标点/空格）切分；单段过长时再按固定长度截断。
    """
    if not content:
        return []

    total = len(content)
    # 短回答整体一次性发送，避免过度切片导致空白
    if total <= 12:
        return [content]

    # 目标段大小：按回答长度线性增长，最少 4、最多 20 个字符一段
    if total <= 50:
        chunk_size = 4
    elif total <= 300:
        chunk_size = 8
    else:
        chunk_size = max(12, total // 30)

    chunks: list[str] = []
    start = 0
    delimiters = set("，。、；：？！.,;:!? \n")
    while start < total:
        end = min(start + chunk_size, total)
        # 尝试在 chunk_size 附近寻找语义切分点
        if end < total:
            for idx in range(end, start, -1):
                if content[idx - 1] in delimiters:
                    end = idx
                    break
        chunks.append(content[start:end])
        start = end
    return chunks


def _run_agent_stream(
    agent: Any,
    message: str,
    thread_id: str,
    queue: asyncio.Queue[dict[str, Any] | None],
    loop: asyncio.AbstractEventLoop,
    debug: bool = False,
) -> None:
    """在后台线程中运行 Agent 流式输出，将消息事件写入队列。

    事件约定：
    - progress:  当前执行阶段提示（分析/调用工具/等待工具/生成回答/完成）。
    - thinking:  Agent 思考/推理过程，展示在「运行过程」区域。
    - tool_start/tool_end: 工具执行事件。
    - answer:    LLM 最终输出结果，按 token/小段多次发送以提供流式视觉反馈。
    - error:     执行异常。

    流式实现：
    - 使用 LangGraph 的 stream_mode=["messages", "values"]。
    - "messages" 提供 token 级/消息级实时chunk，直接用于推送 thinking/answer/tool 事件。
    - "values" 提供完整状态快照，用于判定阶段、发送工具决策摘要、非流式模型兜底。

    重要：此函数在独立线程中运行，绝不能调用 time.sleep 或任何同步阻塞操作，
    否则会卡住 LangGraph 内部调度，导致 Agent 永远走不到下一步，SSE 流也永远不会结束。
    """

    def _put(event: str, data: dict[str, Any]) -> None:
        # 使用 call_soon_threadsafe + put_nowait 直接唤醒 event_generator 中的
        # queue.get()，避免 run_coroutine_threadsafe 在高压下产生的事件排队/丢失问题。
        ts = int(time.time() * 1000)
        data_with_ts = {**data, "_server_ts": ts}
        print(f"[SSE emit] event={event} ts={ts}", flush=True)
        loop.call_soon_threadsafe(
            queue.put_nowait,
            {"event": event, "data": data_with_ts},
        )

    def _put_answer_chunks(chunks: list[str]) -> None:
        """逐段投放 answer 事件，不做任何 sleep 阻塞。"""
        for chunk in chunks:
            _put("answer", {"content": chunk})

    stream_state: dict[str, Any] = {
        "stage": "thinking",
        "tools_executed": False,
        "seen_tool_call": False,
        "streamed_answer_chars": 0,
        "pending_content": [],
        "pending_message_has_tool_calls": False,
        "first_ai_message_finished": False,
        "last_tool_name": "",
    }

    def _flush_pending_content() -> None:
        """将 pending_content 按当前上下文分流为 thinking 或 answer。"""
        content = "".join(stream_state["pending_content"])
        stream_state["pending_content"] = []
        if not content:
            return

        if stream_state["tools_executed"]:
            # 工具执行后的内容 = 最终回答
            stream_state["stage"] = "answer"
            stream_state["streamed_answer_chars"] += len(content)
            _put("answer", {"content": content})
        elif stream_state["pending_message_has_tool_calls"]:
            # 第一轮模型输出中包含工具调用，其内容属于思考过程
            stream_state["stage"] = "thinking"
            _put("thinking", {"content": content})
        else:
            # 未调用工具，直接回答；拆分后投放，避免单条过长
            stream_state["stage"] = "answer"
            for chunk in _split_answer_stream(content):
                stream_state["streamed_answer_chars"] += len(chunk)
                _put("answer", {"content": chunk})

    def _handle_message_chunk(chunk: Any) -> None:
        """处理 "messages" 模式下的消息分片，做实时事件推送。"""
        if isinstance(chunk, ToolMessage):
            _flush_pending_content()
            stream_state["tools_executed"] = True
            stream_state["stage"] = "answer"
            stream_state["pending_message_has_tool_calls"] = False
            stream_state["first_ai_message_finished"] = True

            text = _extract_text(chunk.content)
            tool_name = getattr(chunk, "name", None) or stream_state["last_tool_name"] or "unknown"

            # run_automation_test 创建任务后，立即通知前端刷新列表
            # 注意：必须在截断文本前解析完整 JSON，否则会被截断导致解析失败。
            if tool_name == "run_automation_test":
                try:
                    output_data = json.loads(text)
                    task_data = output_data.get("data", {}) if isinstance(output_data.get("data"), dict) else {}
                    task_id = task_data.get("taskId") or task_data.get("id")
                    if task_id:
                        _put("task_created", {"task_id": task_id})
                except Exception:
                    pass

            if len(text) > 500:
                text = text[:500] + "..."
            _put("tool_end", {"name": tool_name, "output": text})
            _put("progress", {"stage": "tool_running", "message": "工具执行完成，继续生成回答…"})
            return

        if isinstance(chunk, (AIMessage, AIMessageChunk)):
            tool_call_chunks = getattr(chunk, "tool_call_chunks", None) or []
            if tool_call_chunks:
                stream_state["seen_tool_call"] = True
                stream_state["pending_message_has_tool_calls"] = True
                # 工具调用相关的碎片内容属于思考过程，先收集；
                # 等 values 模式给出完整 tool_calls 后再统一发送 tool_start / thinking。
                return

            content = _extract_text(chunk.content)
            if not content:
                return

            if stream_state["tools_executed"]:
                # 已过工具执行阶段，后续模型输出一定为最终回答，直接流式推送
                stream_state["stage"] = "answer"
                stream_state["streamed_answer_chars"] += len(content)
                _put("answer", {"content": content})
            elif stream_state["first_ai_message_finished"]:
                # 第一轮消息已结束（说明它可能是空内容或已处理），后续内容按当前上下文处理
                if stream_state["pending_message_has_tool_calls"]:
                    stream_state["stage"] = "thinking"
                    _put("thinking", {"content": content})
                else:
                    stream_state["stage"] = "answer"
                    stream_state["streamed_answer_chars"] += len(content)
                    _put("answer", {"content": content})
            else:
                # 仍在第一轮消息中，无法确定是直接回答还是工具调用前的思考，先缓冲
                stream_state["pending_content"].append(content)

    def _handle_values_state(state: dict[str, Any]) -> None:
        """处理 "values" 模式下的完整状态，用于阶段判定与兜底。"""
        messages = state.get("messages", [])
        if not messages:
            return

        last_msg = messages[-1]

        if isinstance(last_msg, ToolMessage):
            stream_state["last_tool_name"] = getattr(last_msg, "name", None) or ""
            _put("progress", {"stage": "tool_running", "message": "正在执行工具，等待返回结果…"})
            return

        if isinstance(last_msg, (AIMessage, AIMessageChunk)):
            content = _extract_text(last_msg.content)
            tool_calls = _extract_tool_calls(last_msg)

            if tool_calls:
                stream_state["seen_tool_call"] = True
                stream_state["pending_message_has_tool_calls"] = True
                stream_state["first_ai_message_finished"] = True
                _flush_pending_content()

                tool_summaries = [f"{tc['name']}({tc['args']})" for tc in tool_calls]
                current_tool = tool_calls[0]["name"]
                stream_state["last_tool_name"] = current_tool
                _put("progress", {"stage": "tool_calling", "message": f"正在调用工具：{current_tool}…"})
                _put(
                    "tool_start",
                    {"name": current_tool, "input": json.dumps(tool_calls[0]["args"], ensure_ascii=False)},
                )
                _put("thinking", {"content": f"\n正在分析需求并准备调用工具：{', '.join(tool_summaries)}"})
            elif content:
                # 当前完整 AI 消息有内容且无工具调用：
                # - 若尚未执行过工具，说明是直接回答；
                # - 若已执行过工具，说明是最终回答。
                stream_state["pending_message_has_tool_calls"] = False
                stream_state["first_ai_message_finished"] = True
                _flush_pending_content()

                _put("progress", {"stage": "answering", "message": "正在生成最终回答…"})
                stream_state["stage"] = "answer"

                # 若模型未通过 messages 流式输出（非流式模型），则在这里兜底一次性拆分发送。
                if stream_state["streamed_answer_chars"] == 0:
                    _put_answer_chunks(_split_answer_stream(content))

    try:
        _put("progress", {"stage": "analyzing", "message": "正在分析问题…"})

        for item in agent.stream(
            {"messages": [("human", message)]},
            config=_build_invoke_config(thread_id),
            stream_mode=["messages", "values"],
        ):
            if not isinstance(item, tuple) or len(item) != 2:
                continue
            event, data = item
            if event == "messages":
                # data 是 (chunk, metadata)
                if isinstance(data, tuple) and len(data) == 2:
                    chunk, _metadata = data
                    _handle_message_chunk(chunk)
                else:
                    _handle_message_chunk(data)
            elif event == "values":
                _handle_values_state(data)

    except Exception as exc:  # noqa: BLE001
        _put("error", {"message": f"Agent 执行异常：{exc}"})


@app.get("/api/agent/health")
async def health() -> dict[str, str]:
    """健康检查。"""
    return {"status": "ok"}


@app.post("/api/agent/chat")
async def chat(request: ChatRequest, authorization: str = Header(default=None)) -> StreamingResponse:
    """SSE 流式对话接口。"""
    token = _extract_bearer_token(authorization)
    if not token:
        raise HTTPException(status_code=401, detail="缺少或无效的 Authorization: Bearer token")

    thread_id = request.thread_id or str(uuid.uuid4())

    # 临时设置 test-backend 客户端认证信息，处理完成后还原旧值
    old_token = backend_client.token
    old_auth_type = backend_client.auth_type
    backend_client.token = token
    backend_client.auth_type = "bearer"

    async def event_generator():
        queue: asyncio.Queue[dict[str, Any] | None] = asyncio.Queue()
        loop = asyncio.get_running_loop()

        def run() -> None:
            try:
                agent = create_agent()
                _run_agent_stream(agent, request.message, thread_id, queue, loop, debug=request.debug)
            except Exception as exc:  # noqa: BLE001
                try:
                    loop.call_soon_threadsafe(
                        queue.put_nowait,
                        {"event": "error", "data": {"message": f"Agent 线程异常：{exc}"}},
                    )
                except Exception:
                    pass
            finally:
                backend_client.token = old_token
                backend_client.auth_type = old_auth_type
                # 必须先把结束标记放入队列，再让线程结束，否则 event_generator 可能
                # 因线程完成而提前退出，导致队列中已存在的事件（answer/thinking）丢失。
                try:
                    loop.call_soon_threadsafe(queue.put_nowait, None)
                except Exception:
                    pass

        task = asyncio.create_task(asyncio.to_thread(run))

        try:
            while True:
                item = await queue.get()
                if item is None:
                    break
                ts = int(time.time() * 1000)
                print(f"[SSE yield] event={item['event']} ts={ts}", flush=True)
                yield _format_sse(item["event"], item["data"])

            yield _format_sse("done", {"thread_id": thread_id})
        finally:
            if not task.done():
                task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
