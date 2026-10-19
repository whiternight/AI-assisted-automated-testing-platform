# AI-assisted Automated Testing Platform

AI 辅助的自动化测试平台（Monorepo）。平台以「用例管理 → 流程编排 → 任务执行 → AI 辅助」为主线，覆盖接口测试用例的录入、调试、编排、执行与结果分析全流程。

## 仓库结构

| 目录 | 说明 | 技术栈 |
| --- | --- | --- |
| `backend/` | 平台后端服务：用户权限、用例管理、流程编排（BPMN）、串行执行引擎、断言引擎 | Java 17 · Spring Boot 3 · MyBatis-Plus · Flowable · Dubbo · MySQL |
| `frontend/` | 平台前端：用例管理界面、BPMN 流程设计器、执行历史看板、AI 助手面板 | Vue 3 · Vite · Element Plus · Pinia · bpmn-js |
| `agent/` | AI 智能体服务：自然语言驱动测试任务、RAG 知识库、MCP 工具服务 | Python 3.12 · FastAPI · LangChain/LangGraph · Chroma |

## 快速开始

### 1. 后端（backend）

```bash
cd backend
# 准备 MySQL，创建数据库 test_platform，修改 src/main/resources/application-dev.yml 连接信息
./mvnw spring-boot:run
# 服务端口 8080，API 文档 http://localhost:8080/swagger-ui.html
```

### 2. 前端（frontend）

```bash
cd frontend
npm install
npm run dev
# 开发服务器 5173 端口，/api 代理到 8080，/agent 代理到 8000
```

### 3. AI 智能体（agent）

```bash
cd agent
uv sync
cp .env.example .env   # 填写 LLM / Embedding 的 API Key
uv run python main.py server
# 服务端口 8000
```

## 工程实践要点

- **统一返回与异常码体系**：后端统一 `Result` 包装 + 分段业务异常码（1xxx 通用 / 2xxx 认证 / 3xxx 资源 / 4xxx 用例 / 5xxx 应用 / 9xxx 系统），前端拦截器按码分类提示。
- **BPMN 流程编排**：用例以 ServiceTask 形式编排为 BPMN 流程，Flowable 引擎驱动串行执行，节点支持输入/输出映射与断言。
- **执行快照与回溯**：用例执行记录请求/响应快照（AES 加密存储），支持失败回溯与历史对比。
- **AI Agent 工具化**：后端能力以 Tool 形式暴露给 LangGraph Agent，支持登录态自动续期、任务轮询、SSE 流式对话。
- **配置外置**：密钥、数据库连接、模型 Key 全部走环境变量 / `.env`，仓库内仅保留占位示例。

## 说明

本仓库为脱敏后的骨架版本：所有密钥、真实服务器地址、业务命名均已替换为通用占位（`change-me`、`your-api-key`、`com.example` 等），保留了完整的模块结构与工程习惯，可直接作为同类平台的起手模板。
