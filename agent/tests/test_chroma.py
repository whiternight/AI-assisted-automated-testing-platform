"""
Chroma 向量库写入与检索测试。
"""

import tempfile
from pathlib import Path

import pytest

from langchain_core.documents import Document
from langchain_chroma import Chroma

from src.rag import RAGPipeline


class TestChromaOperations:
    """测试 Chroma 写入、检索、删除等操作。"""

    def _create_pipeline(self, temp_dir, monkeypatch):
        """创建一个使用 FakeEmbedding 的 RAGPipeline。"""
        monkeypatch.setenv("EMBEDDING_PROVIDER", "local")
        monkeypatch.setenv("EMBEDDING_MODEL_PATH", "/fake/model")
        monkeypatch.setenv("CHROMA_PERSIST_DIR", temp_dir)

        # 使用 mock 的 Embedding，避免加载真实模型
        from src import rag as rag_module

        fake_embeddings = self._fake_embeddings()
        monkeypatch.setattr(rag_module, "_init_embeddings", lambda: fake_embeddings)
        # 直接修改已加载的全局 config，禁用重排器
        monkeypatch.setattr(rag_module.config.reranker, "model_path", "")
        monkeypatch.setattr(rag_module.config.chroma, "persist_dir", temp_dir)

        return RAGPipeline()

    @staticmethod
    def _fake_embeddings():
        """返回固定向量的假 Embedding。"""
        from unittest.mock import MagicMock

        embeddings = MagicMock()
        # 每个文档返回不同向量，方便检索时区分
        embeddings.embed_documents.side_effect = lambda texts: [
            [float(i + 1), 0.0, 0.0] for i in range(len(texts))
        ]
        embeddings.embed_query.return_value = [1.0, 0.0, 0.0]
        return embeddings

    def test_ingest_single_file(self, temp_chroma_dir, monkeypatch):
        """导入单个 txt 文件后，向量库应能检索到内容。"""
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".txt", delete=False, encoding="utf-8"
        ) as f:
            f.write("这是一个测试文档。\n包含自动化测试相关信息。")
            file_path = f.name

        try:
            pipeline = self._create_pipeline(temp_chroma_dir, monkeypatch)
            count = pipeline.ingest(file_path)
            assert count == 1

            docs = pipeline.retrieve("自动化测试")
            assert len(docs) > 0
            assert "自动化测试" in docs[0].page_content
        finally:
            Path(file_path).unlink(missing_ok=True)

    def test_retrieve_empty_collection(self, temp_chroma_dir, monkeypatch):
        """空向量库检索应返回空列表。"""
        pipeline = self._create_pipeline(temp_chroma_dir, monkeypatch)
        docs = pipeline.retrieve("不存在的内容")
        assert docs == []

    def test_format_context(self):
        """format_context 应正确拼接文档来源与内容。"""
        docs = [
            Document(page_content="内容A", metadata={"source": "a.txt", "page": 1}),
            Document(page_content="内容B", metadata={"source": "b.txt"}),
        ]
        context = RAGPipeline.format_context(docs)
        assert "内容A" in context
        assert "内容B" in context
        assert "a.txt" in context

    def test_chroma_persistence(self, temp_chroma_dir, monkeypatch):
        """Chroma 持久化后重新加载，数据应保留。"""
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".txt", delete=False, encoding="utf-8"
        ) as f:
            f.write("持久化测试文档。")
            file_path = f.name

        try:
            pipeline = self._create_pipeline(temp_chroma_dir, monkeypatch)
            pipeline.ingest(file_path)

            # 重新初始化 pipeline，从同一目录加载
            pipeline2 = self._create_pipeline(temp_chroma_dir, monkeypatch)
            docs = pipeline2.retrieve("持久化")
            assert len(docs) > 0
        finally:
            Path(file_path).unlink(missing_ok=True)
