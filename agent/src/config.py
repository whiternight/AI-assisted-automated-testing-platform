"""
agent 配置中心（LangChain 架构指引版）

== 为什么要这样设计？ ==
1. 单一职责：config.py 只放"配置参数"，不创建 LLM / Embedding / VectorStore 客户端。
   客户端在各自模块里读取 config 后初始化，避免循环依赖。
2. 自动加载环境变量：pydantic-settings 内置 .env 读取，不需要手动 load_dotenv()。
3. 分层对应 LangChain 组件：每个 Settings 类对应 LangChain 里的一类对象，
   方便你以后按组件查文档、替换实现。

== 配置加载优先级（从高到低）==
    环境变量 > .env 文件 > 代码默认值

== LangChain 组件映射 ==
- EmbeddingSettings  -> LangChain Embeddings（文本转向量）
- RerankerSettings   -> Retriever 后处理 / contextual compression（重排）
- LLMSettings        -> LangChain Chat Models（大模型对话）
- ChromaSettings     -> LangChain VectorStore（向量数据库）
- RetrieverSettings  -> TextSplitter + VectorStoreRetriever（文档切分与召回）
- BackendSettings    -> 会被包装成 LangChain Tool，供 Agent 调用
- ToolSettings       -> LangChain Tools 定义来源
- AgentSettings      -> LangChain / LangGraph Agent 行为控制
- MemorySettings     -> LangChain Memory / Store（对话记忆）
"""

from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field
from typing import Optional


class EmbeddingSettings(BaseSettings):

    model_config = SettingsConfigDict(
        env_prefix="EMBEDDING_", env_file=".env", extra="ignore"
    )

    provider: str = Field(
        default="local",
        description="Embedding 提供方：local（本地 HuggingFace）/ openai（OpenAI 兼容接口）",
    )

    # 本地 HuggingFace 模型路径，例如 D:\\models\\bge-small-zh
    model_path: str = Field(default="", description="本地 Embedding 模型路径")

    # OpenAI 兼容在线 Embedding 服务参数
    model: str = Field(default="text-embedding-v3", description="在线 Embedding 模型名")
    base_url: str = Field(
        default="https://dashscope.aliyuncs.com/compatible-mode/v1",
        description="Embedding 服务的 OpenAI 兼容接口地址",
    )
    api_key: str = Field(default="", description="Embedding 服务 API Key")

    # 向量维度，创建 Chroma collection 时可能用到。
    dimensions: int = Field(default=1024, ge=1, description="Embedding 向量维度")


class RerankerSettings(BaseSettings):

    model_config = SettingsConfigDict(env_prefix="RERANKER_", env_file=".env", extra="ignore")

    # 本地模型路径，例如 D:\\models\\bge-reranker-base
    model_path: str = Field(default="", description="本地重排模型路径")

    # 重排后保留的文档数量
    top_n: int = Field(default=3, ge=1, description="重排后返回的 top_n")


class LLMSettings(BaseSettings):

    model_config = SettingsConfigDict(env_prefix="LLM_", env_file=".env", extra="ignore")

    model: str = Field(default="qwen3.7-plus", description="大模型名")
    base_url: str = Field(
        default="https://dashscope.aliyuncs.com/compatible-mode/v1",
        description="OpenAI 兼容接口地址"
    )
    api_key: str = Field(default="", description="LLM API Key")
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    max_tokens: int = Field(default=2048, ge=1)


class ChromaSettings(BaseSettings):

    model_config = SettingsConfigDict(env_prefix="CHROMA_", env_file=".env", extra="ignore")

    persist_dir: str = Field(default="chroma_db", description="持久化目录")
    collection_name: str = Field(default="test_agent", description="集合名")
    distance_metric: str = Field(default="cosine", description="距离函数")


class RetrieverSettings(BaseSettings):

    model_config = SettingsConfigDict(env_prefix="RETRIEVER_", env_file=".env", extra="ignore")

    chunk_size: int = Field(default=2000, ge=1, description="文档切分块大小")
    chunk_overlap: int = Field(default=50, ge=0, description="块之间重叠字符数")
    search_k: int = Field(default=5, ge=1, description="向量召回数量")
    score_threshold: float = Field(default=0.5, ge=0.0, le=1.0, description="相似度过滤阈值")


class BackendSettings(BaseSettings):

    model_config = SettingsConfigDict(env_prefix="BACKEND_", env_file=".env", extra="ignore")

    base_url: str = Field(default="", description="后端服务根地址，例如 http://localhost:8080")
    auth_type: str = Field(default="", description="认证方式：none / api_key / bearer / oauth2")
    api_key: str = Field(default="", description="test-backend 接口 API Key")
    token: str = Field(default="", description="Bearer / JWT Token")
    timeout: int = Field(default=30, ge=1)
    max_retries: int = Field(default=3, ge=0)
    verify_ssl: bool = Field(default=True)


class ToolSettings(BaseSettings):

    model_config = SettingsConfigDict(env_prefix="TOOL_", env_file=".env", extra="ignore")

    config_path: str = Field(default="", description="工具定义文件路径（JSON/YAML）")
    openapi_spec_path: str = Field(default="", description="OpenAPI 规范路径")
    api_schema_dir: str = Field(default="", description="接口 schema 目录")


class AgentSettings(BaseSettings):

    model_config = SettingsConfigDict(
        env_prefix="AGENT_", env_file=".env", extra="ignore", env_parse_none_str=""
    )

    system_prompt: str = Field(
        default=(
            "你是一个自动化测试平台的智能测试助手。你的目标是理解用户意图后，主动调用工具完成测试任务，\n"
            "而不是反复向用户询问 obvious 的默认值。\n\n"
            "行为准则：\n"
            "1. 当任务涉及后端接口且未提供登录凭证时，直接使用 backend_login 工具，"
            "默认使用 username='admin'、password='123456'。\n"
            "2. 如果调用工具返回 401/403，说明 token 失效，先调用 backend_login 重新登录，然后重试原操作。\n"
            "3. 执行自动化测试时，executorId 默认为 1，任务名称（对应接口字段 testSuiteName）默认为 'agent-auto-test'，env 默认为 'default'。\n"
            "4. 调用 run_automation_test 后，必须通过 get_automation_task 轮询任务状态，直到任务完成或达到最大轮询次数。\n"
            "5. 选择用例时优先选择 isAutomationEnabled=true 的用例；如果没有明确指定数量，默认取 3~5 条。\n"
            "6. 输出报告时，统一使用‘自动化测试任务’ terminology，不要用‘测试集’，避免用户混淆。run_automation_test 创建的是‘自动化测试任务’，不是‘测试集’。\n"
            "7. 最终结果必须包含：任务ID、执行状态、成功/失败统计、关键错误或结论。"
        ),
        description="Agent 系统提示词"
    )
    max_iterations: int = Field(default=10, ge=1, description="最大思考/调用轮数，防止死循环")
    tool_choice: str = Field(default="auto", description="工具选择策略：auto / required / 具体工具名")
    allowed_tools: Optional[list[str]] = Field(default=None, description="允许调用的工具白名单")
    blocked_tools: Optional[list[str]] = Field(default=None, description="禁止调用的工具黑名单")
    enable_human_in_the_loop: bool = Field(default=False, description="敏感操作前是否人工确认")


class MemorySettings(BaseSettings):

    model_config = SettingsConfigDict(env_prefix="MEMORY_", env_file=".env", extra="ignore")

    memory_type: str = Field(default="window", description="记忆类型：buffer / window / summary")
    max_history_turns: int = Field(default=10, ge=1)
    session_store_path: str = Field(default="./sessions", description="会话持久化目录")


class AppSettings(BaseSettings):
    """应用通用配置。"""
    model_config = SettingsConfigDict(env_prefix="APP_", env_file=".env", extra="ignore")

    log_level: str = Field(default="INFO")
    data_dir: str = Field(default="./data")
    upload_dir: str = Field(default="./uploads")
    request_timeout: int = Field(default=60, ge=1)


class Config(BaseSettings):
    """
    全局配置入口。

    使用方式：
        from src.config import config
        print(config.llm.model)
        print(config.backend.base_url)
    """
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    embedding: EmbeddingSettings = Field(default_factory=EmbeddingSettings)
    reranker: RerankerSettings = Field(default_factory=RerankerSettings)
    llm: LLMSettings = Field(default_factory=LLMSettings)
    chroma: ChromaSettings = Field(default_factory=ChromaSettings)
    retriever: RetrieverSettings = Field(default_factory=RetrieverSettings)
    backend: BackendSettings = Field(default_factory=BackendSettings)
    tool: ToolSettings = Field(default_factory=ToolSettings)
    agent: AgentSettings = Field(default_factory=AgentSettings)
    memory: MemorySettings = Field(default_factory=MemorySettings)
    app: AppSettings = Field(default_factory=AppSettings)


config = Config()
