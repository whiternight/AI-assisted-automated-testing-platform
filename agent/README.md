# Agent — AI 智能体服务

平台的 AI 辅助层：以自然语言驱动测试任务（登录、选用例、跑任务、查结果），并提供 RAG 知识库与 MCP 工具服务。

## 技术栈

Python 3.12 · FastAPI · LangChain / LangGraph · Chroma（向量库）· sentence-transformers · MCP · uv（依赖管理）

## 目录结构

| 路径 | 职责 |
| --- | --- |
| `main.py` | 统一入口：`server` / `chat` / `ingest` / `tool` / `mcp` / `config` 子命令 |
| `src/config.py` | 配置中心（pydantic-settings）：Embedding / Reranker / LLM / Chroma / Retriever / Backend / Tool / Agent / Memory 分层 |
| `src/agent.py` | LangGraph Agent：思考-调用工具循环、最大轮数保护 |
| `src/server.py` | FastAPI 服务：`/api/agent/chat`（SSE 流式）、健康检查 |
| `src/rag.py` | RAG：文档切分、向量化、召回、重排 |
| `src/mcp_server.py` | MCP 工具服务 |
| `src/tools/` | 工具集：后端登录/接口调用客户端（`client.py`）、自动化测试（`automation.py`）、流程编排（`flow.py`）、原服务（`original_service.py`）、工具 schema（`schemas.py`） |
| `tests/` | pytest 测试 |
| `deploy/` | 生产部署样例：systemd unit、Nginx 反代、部署脚本 |

## 运行

```bash
uv sync
cp .env.example .env   # 填写 LLM_API_KEY / DASHSCOPE_API_KEY 等
uv run python main.py server        # HTTP 服务，8000 端口
uv run python main.py chat          # 命令行对话
```

## 配置

全部配置走环境变量（见 `.env.example`），加载优先级：环境变量 > `.env` > 代码默认值。关键项：

| 环境变量 | 说明 |
| --- | --- |
| `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` | 大模型（OpenAI 兼容接口） |
| `EMBEDDING_PROVIDER` | `local`（本地 HuggingFace）/ `openai`（在线） |
| `BACKEND_BASE_URL` | 后端服务地址，默认 `http://localhost:8080` |
| `BACKEND_TOKEN` | 调用后端用的 Bearer Token（也可由 Agent 自动登录获取） |
| `AGENT_MAX_ITERATIONS` | Agent 最大思考轮数，防止死循环 |

## 工程要点

- **Agent 行为准则内置**：默认凭证自动登录、401/403 自动续期重试、任务创建后轮询直至完成、结果结构化汇报。
- **SSE 不阻塞**：流式响应通过异步生成器透传，不阻塞 LangGraph 调度。
- **部署即样例**：`deploy/` 提供 systemd + Nginx（SSE 关缓冲）+ 一键部署脚本的完整参考。
