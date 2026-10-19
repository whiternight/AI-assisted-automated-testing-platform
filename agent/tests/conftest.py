"""
测试公共夹具与工具函数。

约定：
- 单元测试默认使用 mock，不调用真实外部服务。
- 需要真实网络/后端的测试使用 @pytest.mark.integration 标记，默认跳过。
"""

import os
from unittest.mock import MagicMock

import pytest


@pytest.fixture(autouse=True)
def _isolate_env(monkeypatch):
    """每个测试运行在隔离的环境中，避免读取开发者本地 .env。"""
    # 清空可能影响测试的环境变量
    env_keys = [
        "EMBEDDING_PROVIDER",
        "EMBEDDING_MODEL_PATH",
        "EMBEDDING_API_KEY",
        "EMBEDDING_BASE_URL",
        "EMBEDDING_MODEL",
        "RERANKER_MODEL_PATH",
        "LLM_API_KEY",
        "LLM_BASE_URL",
        "BACKEND_BASE_URL",
        "BACKEND_TOKEN",
    ]
    for key in env_keys:
        monkeypatch.delenv(key, raising=False)

    # 设置项目根目录，方便加载 src 包
    monkeypatch.setenv("PYTHONPATH", os.path.dirname(os.path.dirname(__file__)))


@pytest.fixture
def fake_embeddings():
    """返回一个假的 Embedding 对象，用于 Chroma 测试，避免加载真实模型。"""
    embeddings = MagicMock()
    embeddings.embed_documents.return_value = [[0.1, 0.2, 0.3]]
    embeddings.embed_query.return_value = [0.1, 0.2, 0.3]
    return embeddings


@pytest.fixture
def temp_chroma_dir(tmp_path):
    """返回一个临时 Chroma 持久化目录。"""
    return str(tmp_path / "chroma")


def pytest_configure(config):
    """注册自定义 pytest 标记。"""
    config.addinivalue_line("markers", "integration: 需要真实外部服务的集成测试")
    config.addinivalue_line("markers", "slow: 运行较慢的测试")


def pytest_runtest_setup(item):
    """默认跳过 integration 测试，除非显式传入 --run-integration。"""
    if "integration" in item.keywords and not item.config.getoption("--run-integration"):
        pytest.skip("需要 --run-integration 选项才运行集成测试")


def pytest_addoption(parser):
    parser.addoption(
        "--run-integration",
        action="store_true",
        default=False,
        help="运行需要真实外部服务的集成测试",
    )
