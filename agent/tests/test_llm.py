"""
LLM 调用测试。
"""

from unittest.mock import MagicMock, patch

import pytest


class TestLLM:
    """测试大模型调用层。"""

    @patch("src.rag.ChatOpenAI")
    def test_rag_query_builds_prompt_and_calls_llm(self, mock_chat_cls, monkeypatch):
        """RAG query 应正确拼装提示词并调用 LLM。"""
        monkeypatch.setenv("EMBEDDING_PROVIDER", "local")
        monkeypatch.setenv("EMBEDDING_MODEL_PATH", "/fake/model")
        monkeypatch.setenv("RERANKER_MODEL_PATH", "")

        mock_llm = MagicMock()
        mock_response = MagicMock()
        mock_response.content = "这是 LLM 的回答"
        mock_llm.invoke.return_value = mock_response
        mock_chat_cls.return_value = mock_llm

        from src.rag import RAGPipeline
        from unittest.mock import MagicMock as Mock

        fake_embeddings = Mock()
        fake_embeddings.embed_documents.return_value = [[1.0, 0.0]]
        fake_embeddings.embed_query.return_value = [1.0, 0.0]

        from src import rag as rag_module

        rag_module._init_embeddings = lambda: fake_embeddings

        pipeline = RAGPipeline()
        # 直接注入一个包含固定内容的 vectorstore
        from langchain_core.documents import Document
        from langchain_community.vectorstores import Chroma

        pipeline.vectorstore = Chroma.from_documents(
            [Document(page_content="参考资料内容")],
            embedding=fake_embeddings,
        )

        answer = pipeline.query("问题")

        assert answer == "这是 LLM 的回答"
        mock_llm.invoke.assert_called_once()
        prompt = mock_llm.invoke.call_args[0][0]
        assert "参考资料" in prompt
        assert "问题" in prompt

    @pytest.mark.integration
    def test_llm_real_openai_compatible_response(self, monkeypatch):
        """集成测试：真实调用 OpenAI 兼容接口返回结果（需配置有效 key）。"""
        from langchain_openai import ChatOpenAI
        from src.config import config

        llm = ChatOpenAI(
            model=config.llm.model,
            openai_api_key=config.llm.api_key,
            openai_api_base=config.llm.base_url,
            temperature=0.7,
            max_tokens=50,
        )
        response = llm.invoke("你好，请用一句话介绍自己。")

        assert response.content
        assert isinstance(response.content, str)
