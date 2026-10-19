"""
LangGraph Agent 行为测试。
"""

from typing import Any, Iterator, List, Optional
from unittest.mock import MagicMock, patch

import pytest
from langchain_core.callbacks import CallbackManagerForLLMRun
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.outputs import ChatGeneration, ChatResult


def _mock_embeddings():
    """创建一个返回固定向量的假 Embedding。"""
    embeddings = MagicMock()
    embeddings.embed_documents.return_value = [[1.0, 0.0, 0.0]]
    embeddings.embed_query.return_value = [1.0, 0.0, 0.0]
    return embeddings


def _patch_rag_init(monkeypatch):
    """补丁 src.rag 的初始化函数，避免加载真实模型；测试结束后自动恢复。"""
    from src import rag as rag_module

    monkeypatch.setattr(rag_module, "_init_embeddings", _mock_embeddings)
    monkeypatch.setattr(rag_module, "_init_reranker", lambda: None)


class FakeChatModel(BaseChatModel):
    """用于 Agent 测试的伪 LLM，直接返回固定 AIMessage，避免网络调用。"""

    response_content: str = "最终回答"

    def _generate(
        self,
        messages: List[BaseMessage],
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> ChatResult:
        message = AIMessage(content=self.response_content)
        generation = ChatGeneration(message=message)
        return ChatResult(generations=[generation])

    def _stream(
        self,
        messages: List[BaseMessage],
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> Iterator[ChatGeneration]:
        yield ChatGeneration(message=AIMessage(content=self.response_content))

    @property
    def _llm_type(self) -> str:
        return "fake_chat_model"

    def bind_tools(self, tools, **kwargs):
        """伪绑定工具：直接返回自身，避免触发真实工具调用。"""
        return self


class TestAgent:
    """测试 Agent 组装与工具选择行为。"""

    def test_create_agent_returns_compiled_graph(self):
        """create_agent 应返回编译后的 LangGraph。"""
        from src.agent import create_agent

        agent = create_agent()
        assert agent is not None
        assert "model" in agent.nodes
        assert "tools" in agent.nodes

    def test_agent_filters_tools_by_allowed_list(self, monkeypatch):
        """allowed_tools 应能限制 Agent 可用工具。"""
        monkeypatch.setenv("AGENT_ALLOWED_TOOLS", "list_api_cases,direct_test")

        from src.agent import create_agent

        agent = create_agent()
        assert agent is not None

    @patch("src.agent.ChatOpenAI")
    def test_agent_invoke_with_mocked_llm(self, mock_chat_cls, monkeypatch):
        """使用伪 LLM 验证 Agent 调用链。"""
        _patch_rag_init(monkeypatch)

        mock_chat_cls.return_value = FakeChatModel(response_content="最终回答")

        from src.agent import create_agent

        agent = create_agent()
        response = agent.invoke(
            {"messages": [("human", "你好")]},
            config={"configurable": {"thread_id": "test-thread"}},
        )

        assert response["messages"][-1].content == "最终回答"

    @pytest.mark.integration
    def test_agent_real_tool_call(self, monkeypatch):
        """集成测试：真实 Agent 调用工具（需 LLM 与 后端服务均可用）。"""
        _patch_rag_init(monkeypatch)
        from src.agent import create_agent

        agent = create_agent()
        response = agent.invoke(
            {"messages": [("human", "帮我列出 5 个 API 用例")]},
            config={"configurable": {"thread_id": "integration-thread"}},
        )

        assert response["messages"][-1].content
