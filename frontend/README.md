# Frontend — 平台前端

自动化测试平台前端，覆盖用例管理、BPMN 流程设计器、执行历史、AI 助手对话等界面。

## 技术栈

Vue 3 · Vite · Element Plus · Pinia · Vue Router · bpmn-js（流程设计器）· Axios · Sass

## 目录结构（`src/`）

| 目录 | 职责 |
| --- | --- |
| `api/` | 后端接口封装：错误码分类（FE/BE）、统一消息提示、SSE 流式请求 |
| `components/` | 布局组件、BPMN 画布组件（`bpmn/`）、图标 |
| `views/` | 页面：登录注册、首页、用例管理、流程设计器（`bpmn/`）、执行历史、API 调试（`api/`）、个人中心 |
| `router/` | 路由与登录守卫 |
| `stores/` | Pinia 状态（用户态等） |
| `composables/` | 组合式函数：AI 助手、主题等 |
| `utils/` | Axios 实例与拦截器（Token 注入、401/403 处理、错误归一化） |

## 运行

```bash
npm install
npm run dev      # 5173 端口
npm run build    # 产物输出 dist/
```

## 代理约定（`vite.config.js`）

| 前缀 | 目标 | 说明 |
| --- | --- | --- |
| `/api` | `http://localhost:8080` | 后端服务（rewrite 去掉前缀） |
| `/deploy-api` | `http://localhost:8080` | 部署相关接口 |
| `/agent` | `http://127.0.0.1:8000` | AI 智能体服务，SSE 流透传（禁缓冲） |

## 工程要点

- **SSE 流式对话**：`/api/agent/chat` 使用 `fetch` + ReadableStream 逐段渲染，代理层禁用缓存与压缩。
- **BPMN 设计器**：基于 bpmn-js 自定义属性面板，节点绑定被测服务与断言配置，画布叠加执行状态着色。
- **错误归一化**：请求层将 HTTP 状态码与后端业务码统一分类为前端错误（FE_*）/后端错误（BE_*），按类别提示。
