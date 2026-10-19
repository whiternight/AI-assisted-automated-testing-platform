"""
Embedding 初始化测试。
"""

from unittest.mock import MagicMock, patch

import pytest

from src import rag as rag_module


def _set_embedding_config(provider: str, model_path: str = "", api_key: str = "", model: str = ""):
    """直接修改 rag 模块中已加载的全局 config 实例（环境变量已在 import 时固化）。"""
    rag_module.config.embedding.provider = provider
    rag_module.config.embedding.model_path = model_path
    rag_module.config.embedding.api_key = api_key
    rag_module.config.embedding.model = model


class TestEmbeddingInit:
    """测试 Embedding 模型初始化逻辑。"""

    def test_init_local_embeddings_requires_model_path(self):
        """local provider 未配置 model_path 时应抛出 ValueError。"""
        _set_embedding_config(provider="local", model_path="")

        with pytest.raises(ValueError, match="未配置 Embedding 模型"):
            rag_module._init_embeddings()

    @patch("langchain_huggingface.HuggingFaceEmbeddings")
    def test_init_local_embeddings_success(self, mock_cls):
        """local provider 配置正确时应创建 HuggingFaceEmbeddings。"""
        _set_embedding_config(provider="local", model_path="/fake/local/model")

        mock_instance = MagicMock()
        mock_cls.return_value = mock_instance

        embeddings = rag_module._init_embeddings()

        assert embeddings is mock_instance
        mock_cls.assert_called_once()
        call_kwargs = mock_cls.call_args.kwargs
        assert call_kwargs["model_name"] == "/fake/local/model"

    def test_init_openai_embeddings_requires_api_key(self):
        """openai provider 未配置 api_key 时应抛出 ValueError。"""
        _set_embedding_config(provider="openai", api_key="")

        with pytest.raises(ValueError, match="EMBEDDING_API_KEY"):
            rag_module._init_embeddings()

    @patch("langchain_openai.OpenAIEmbeddings")
    def test_init_openai_embeddings_success(self, mock_cls):
        """openai provider 配置正确时应创建 OpenAIEmbeddings。"""
        _set_embedding_config(
            provider="openai",
            api_key="sk-test",
            model="text-embedding-v3",
        )
        rag_module.config.embedding.base_url = "https://example.com/v1"

        mock_instance = MagicMock()
        mock_cls.return_value = mock_instance

        embeddings = rag_module._init_embeddings()

        assert embeddings is mock_instance
        mock_cls.assert_called_once()
        call_kwargs = mock_cls.call_args.kwargs
        assert call_kwargs["model"] == "text-embedding-v3"
        assert call_kwargs["openai_api_key"] == "sk-test"
        assert call_kwargs["openai_api_base"] == "https://example.com/v1"

    @pytest.mark.integration
    @pytest.mark.slow
    def test_openai_embedding_returns_vector(self, monkeypatch):
        """集成测试：真实调用在线 Embedding 服务返回向量（需配置有效 key）。"""
        monkeypatch.setenv("EMBEDDING_PROVIDER", "openai")

        embeddings = rag_module._init_embeddings()
        vector = embeddings.embed_query("hello")

        assert isinstance(vector, list)
        assert len(vector) > 0
        assert all(isinstance(v, float) for v in vector)
