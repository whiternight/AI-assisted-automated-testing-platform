"""
main.py 入口行为测试。
"""

from typing import Any, Iterator, List, Optional
from unittest.mock import MagicMock, patch

import pytest
from langchain.agents import create_agent as create_langchain_agent
from langchain_core.callbacks import CallbackManagerForLLMRun
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_core.tools import tool
from langgraph.checkpoint.memory import MemorySaver

from main import StepPrinter


class FakeToolCallingModel(BaseChatModel):
    """伪 LLM：第一次返回 tool_call，第二次返回最终回答。"""

    calls: int = 0

    def _generate(
        self,
        messages: List[BaseMessage],
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> ChatResult:
        self.calls += 1
        if self.calls == 1:
            message = AIMessage(
                content="",
                tool_calls=[
                    {
                        "id": "call_1",
                        "name": "hello_tool",
                        "args": {"name": "world"},
                    }
                ],
            )
        else:
            message = AIMessage(content="已完成调用")
        return ChatResult(generations=[ChatGeneration(message=message)])

    def _stream(
        self,
        messages: List[BaseMessage],
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> Iterator[ChatGeneration]:
        yield ChatGeneration(message=AIMessage(content="stream"))

    @property
    def _llm_type(self) -> str:
        return "fake_tool_calling_model"

    def bind_tools(self, tools, **kwargs):
        return self


@tool
def hello_tool(name: str) -> str:
    """打招呼工具。"""
    return f"你好，{name}！"


class TestStepPrinter:
    """测试 debug 回调 StepPrinter 的执行过程可见性。"""

    def test_stepprinter_prints_tool_calls(self, capsys):
        """StepPrinter 应在 Agent 决定调用工具时打印工具名和参数。"""
        model = FakeToolCallingModel()
        agent = create_langchain_agent(
            model=model,
            tools=[hello_tool],
            system_prompt="你是一个测试助手。",
            checkpointer=MemorySaver(),
        )
        callbacks = [StepPrinter()]

        response = agent.invoke(
            {"messages": [("human", "打个招呼")]},
            config={
                "configurable": {"thread_id": "stepprinter-thread"},
                "callbacks": callbacks,
            },
        )

        assert response["messages"][-1].content == "已完成调用"
        captured = capsys.readouterr().out
        assert "[Agent] 决定调用工具: hello_tool" in captured
        assert "[Tool] 开始执行: hello_tool" in captured
        assert "[Tool] 返回结果（摘要）" in captured
        assert "你好，world！" in captured

    def test_stepprinter_verbose_prints_thoughts(self, capsys):
        """verbose=True 时应打印不含工具调用的普通文本思考内容。"""
        printer = StepPrinter(verbose=True)
        response = ChatResult(
            generations=[ChatGeneration(message=AIMessage(content="直接回答"))]
        )
        printer.on_llm_end(response, run_id="run-1")

        captured = capsys.readouterr().out
        assert "[Agent] 思考/回答: 直接回答" in captured

    def test_stepprinter_non_verbose_hides_thoughts(self, capsys):
        """verbose=False 时不应打印普通文本思考内容，避免与最终答案重复。"""
        printer = StepPrinter(verbose=False)
        response = ChatResult(
            generations=[ChatGeneration(message=AIMessage(content="直接回答"))]
        )
        printer.on_llm_end(response, run_id="run-1")

        captured = capsys.readouterr().out
        assert "[Agent] 思考/回答" not in captured

    def test_stepprinter_does_not_raise_on_malformed_response(self, capsys):
        """StepPrinter 在解析异常输出时不应抛异常，避免中断 Agent。"""
        printer = StepPrinter()

        class BadResponse:
            @property
            def generations(self):
                raise RuntimeError("模拟异常输出格式")

        # 传入一个访问 generations 会抛异常的对象，验证不中断且打印调试信息
        printer.on_llm_end(BadResponse(), run_id="run-1")
        captured = capsys.readouterr().out
        assert "[debug] 解析 LLM 输出时出错" in captured
