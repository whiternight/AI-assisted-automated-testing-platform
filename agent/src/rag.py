"""
RAG 检索增强生成模块。

职责：
1. 按配置初始化 Embedding 模型（本地 HuggingFace 或 OpenAI 兼容在线服务）。
2. 管理 Chroma 向量库：加载文档、切分、索引、检索。
3. 可选接入本地重排模型，对召回结果精排。
4. 对外暴露 `ingest`、`retrieve`、`query`，供 Agent 或脚本直接调用。

配置方式（.env）：
- 本地 Embedding：EMBEDDING_PROVIDER=local，EMBEDDING_MODEL_PATH=...
- 在线 Embedding：EMBEDDING_PROVIDER=openai，EMBEDDING_API_KEY=...，EMBEDDING_MODEL=...
- 重排模型：RERANKER_MODEL_PATH=...
"""

from pathlib import Path
from typing import Any, List, Optional

from langchain_core.documents import Document
from langchain_chroma import Chroma
from langchain_community.document_loaders import (
    PyPDFLoader,
    TextLoader,
    UnstructuredFileLoader,
)
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_openai import ChatOpenAI

from src.config import config


def _init_embeddings():
    """按配置初始化 Embedding 模型：本地 HuggingFace 或 OpenAI 兼容在线服务。"""
    provider = config.embedding.provider.lower()

    if provider == "openai":
        from langchain_openai import OpenAIEmbeddings

        api_key = config.embedding.api_key
        if not api_key:
            raise ValueError(
                "已选择在线 Embedding（EMBEDDING_PROVIDER=openai），"
                "请在 .env 中设置 EMBEDDING_API_KEY。"
            )
        return OpenAIEmbeddings(
            model=config.embedding.model,
            openai_api_key=api_key,
            openai_api_base=config.embedding.base_url,
        )

    # 默认本地 HuggingFace
    model_path = config.embedding.model_path
    if not model_path:
        raise ValueError(
            "未配置 Embedding 模型。请在 .env 中设置 EMBEDDING_MODEL_PATH，"
            "或将 EMBEDDING_PROVIDER 设为 openai 并配置 EMBEDDING_API_KEY。"
        )

    from langchain_huggingface import HuggingFaceEmbeddings

    return HuggingFaceEmbeddings(
        model_name=model_path,
        model_kwargs={"device": "cpu"},
        encode_kwargs={"normalize_embeddings": True},
    )


def _init_reranker() -> Optional[Any]:
    """按配置初始化重排模型；未配置则返回 None。"""
    model_path = config.reranker.model_path
    if not model_path:
        return None

    from sentence_transformers import CrossEncoder

    return CrossEncoder(model_path, device="cpu")


class RAGPipeline:
    """RAG 流水线：文档加载 -> 切分 -> 索引 -> 检索 ->（重排）-> LLM 生成。"""

    def __init__(self):
        self.embeddings = _init_embeddings()
        self.reranker = _init_reranker()
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=config.retriever.chunk_size,
            chunk_overlap=config.retriever.chunk_overlap,
            length_function=len,
            separators=["\n\n", "\n", "。", "！", "？", "；", " ", ""],
        )
        self.vectorstore = Chroma(
            persist_directory=config.chroma.persist_dir,
            collection_name=config.chroma.collection_name,
            embedding_function=self.embeddings,
            collection_metadata={"hnsw:space": config.chroma.distance_metric},
        )

    @staticmethod
    def _load_documents(file_path: str) -> List[Document]:
        """根据扩展名选择加载器读取本地文件。"""
        path = Path(file_path)
        if not path.exists():
            raise FileNotFoundError(f"文件不存在: {file_path}")

        suffix = path.suffix.lower()
        if suffix == ".pdf":
            loader = PyPDFLoader(str(path))
        elif suffix == ".txt":
            loader = TextLoader(str(path), encoding="utf-8")
        else:
            # 支持 docx / md / html 等，依赖 unstructured / docx2txt
            loader = UnstructuredFileLoader(str(path))

        return loader.load()

    def ingest(self, file_path: str) -> int:
        """
        将本地文件加入向量库。

        Returns:
            写入的文档块数量。
        """
        documents = self._load_documents(file_path)
        chunks = self.text_splitter.split_documents(documents)
        self.vectorstore.add_documents(chunks)
        return len(chunks)

    def retrieve(self, query: str) -> List[Document]:
        """
        检索与 query 相关的文档块。

        流程：
        1. 向量相似度召回 search_k 条。
        2. 按 score_threshold 过滤。
        3. 若配置了重排模型，则重排后取 top_n。
        """
        docs_with_score = self.vectorstore.similarity_search_with_score(
            query, k=config.retriever.search_k
        )

        threshold = config.retriever.score_threshold
        # Chroma 默认返回的是距离（distance），配置使用 cosine 时 distance = 1 - similarity
        docs = [
            doc
            for doc, score in docs_with_score
            if (1 - score) >= threshold
        ]

        if self.reranker and docs:
            pairs = [(query, doc.page_content) for doc in docs]
            scores = self.reranker.predict(pairs)
            ranked = sorted(
                zip(docs, scores),
                key=lambda x: x[1],
                reverse=True,
            )
            docs = [doc for doc, _ in ranked[: config.reranker.top_n]]

        return docs

    @staticmethod
    def format_context(documents: List[Document]) -> str:
        """将文档块格式化为带来源标注的上下文文本。"""
        return "\n\n".join(
            f"[来源: {doc.metadata.get('source', 'unknown')} page:{doc.metadata.get('page', '-')} ]\n{doc.page_content}"
            for doc in documents
        )

    def query(self, query: str) -> str:
        """端到端 RAG 查询：检索 + LLM 生成回答。"""
        documents = self.retrieve(query)
        if not documents:
            return "未检索到与问题相关的资料。"

        context = self.format_context(documents)

        llm = ChatOpenAI(
            model=config.llm.model,
            openai_api_key=config.llm.api_key,
            openai_api_base=config.llm.base_url,
            temperature=config.llm.temperature,
            max_tokens=config.llm.max_tokens,
        )

        prompt = (
            "你是一个基于参考资料的问答助手。请严格根据以下参考资料回答问题，"
            "如果资料不足，请明确说明，不要编造信息。\n\n"
            f"参考资料：\n{context}\n\n"
            f"问题：{query}\n\n"
            "回答："
        )

        response = llm.invoke(prompt)
        return response.content


class _LazyRAGPipeline:
    """
    延迟初始化代理，避免在导入 src.rag 时就加载 Embedding 模型。

    第一次访问属性（如 rag.ingest / rag.retrieve）时才真正创建 RAGPipeline。
    """

    def __init__(self):
        self._instance: Optional[RAGPipeline] = None

    def _ensure_initialized(self) -> RAGPipeline:
        if self._instance is None:
            self._instance = RAGPipeline()
        return self._instance

    def ingest(self, file_path: str) -> int:
        return self._ensure_initialized().ingest(file_path)

    def retrieve(self, query: str) -> List[Document]:
        return self._ensure_initialized().retrieve(query)

    def format_context(self, documents: List[Document]) -> str:
        return self._ensure_initialized().format_context(documents)

    def query(self, query: str) -> str:
        return self._ensure_initialized().query(query)

    def __getattr__(self, name: str):
        return getattr(self._ensure_initialized(), name)


# 模块级默认实例，方便直接导入使用，但延迟初始化
rag = _LazyRAGPipeline()


if __name__ == "__main__":
    # 简单自测入口
    print("RAG 模块已加载。")
    print("示例用法：")
    print("  rag.ingest('data/manual.pdf')")
    print("  print(rag.query('介绍一下退换货流程'))")
