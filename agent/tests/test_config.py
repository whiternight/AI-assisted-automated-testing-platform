"""
配置加载测试。
"""

import os

import pytest


class TestConfig:
    """测试配置中心。"""

    def test_config_can_be_imported(self):
        """配置模块可以正常导入。"""
        from src.config import config

        assert config is not None
        assert config.llm.model
        assert config.chroma.collection_name

    def test_empty_env_parsed_as_none_for_lists(self, monkeypatch):
        """空环境变量应被解析为 None，而不是空字符串，避免 list 字段校验失败。"""
        monkeypatch.setenv("AGENT_ALLOWED_TOOLS", "")
        monkeypatch.setenv("AGENT_BLOCKED_TOOLS", "")

        from src.config import Config

        cfg = Config()
        assert cfg.agent.allowed_tools is None
        assert cfg.agent.blocked_tools is None

    def test_embedding_provider_default_is_local(self):
        """默认 Embedding 提供方为 local。"""
        from src.config import Config

        cfg = Config()
        assert cfg.embedding.provider == "local"

    def test_embedding_provider_can_be_openai(self, monkeypatch):
        """可以通过环境变量切换为 openai provider。"""
        monkeypatch.setenv("EMBEDDING_PROVIDER", "openai")
        monkeypatch.setenv("EMBEDDING_API_KEY", "sk-test")
        monkeypatch.setenv("EMBEDDING_MODEL", "text-embedding-v3")

        from src.config import Config

        cfg = Config()
        assert cfg.embedding.provider == "openai"
        assert cfg.embedding.api_key == "sk-test"
        assert cfg.embedding.model == "text-embedding-v3"
