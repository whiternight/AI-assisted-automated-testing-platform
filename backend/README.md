# Backend — 平台后端服务

自动化测试平台后端，提供用户权限、接口用例管理、原服务管理、测试集、BPMN 流程编排与串行执行引擎能力。

## 技术栈

Java 17 · Spring Boot 3 · Spring Security + JWT · MyBatis-Plus · Flowable 7 · Dubbo 3 · MySQL · Caffeine · springdoc-openapi

## 模块划分（`com.example.testplatform`）

| 包 | 职责 |
| --- | --- |
| `auth` | 注册/登录、JWT、用户-部门-角色权限、用户激活审批流（Flowable 委托） |
| `app` | 应用与业务域管理、直接调试（DirectTest） |
| `apicase` | 接口用例 CRUD、cURL 导入、重复检测、调试试跑、变更日志与历史快照 |
| `originalservice` | 原服务（被测服务）注册与用例关联 |
| `testsuite` | 测试集管理与用例聚合 |
| `workflow` | BPMN 流程定义、部署、节点解析、流程执行 |
| `execution` | 串行执行引擎：任务、节点实例、执行日志、HTTP/Dubbo 执行器、断言引擎 |
| `common` | 统一返回 `Result`、全局异常处理、AES/JWT 工具、线程池与 HTTP 客户端配置 |
| `enums` | 业务异常码（1xxx~9xxx 分段）、协议类型、流程节点/状态等枚举 |

## 运行

```bash
# 1. 创建数据库
CREATE DATABASE test_platform DEFAULT CHARSET utf8mb4;

# 2. 修改配置（密钥全部支持环境变量注入）
#    src/main/resources/application-dev.yml  -> 数据库连接
#    JWT_SECRET / AES_KEY / DB_PASSWORD 等通过环境变量覆盖

# 3. 启动
./mvnw spring-boot:run
```

- 服务端口：`8080`
- API 文档：`http://localhost:8080/swagger-ui.html`
- 默认管理员（种子数据）：`admin / 123456`（仅本地开发使用）

## 配置说明

| 配置 | 环境变量 | 默认（占位） |
| --- | --- | --- |
| JWT 密钥 | `JWT_SECRET` | `change-me-jwt-secret-key` |
| AES 加密密钥 | `AES_KEY` | `change-me-aes-key` |
| 数据库密码 | `DB_PASSWORD` | `change-me` |
| 生产库地址 | `DB_HOST` | `127.0.0.1` |

## 测试

```bash
./mvnw test            # 单元测试
./mvnw verify          # 含集成测试（application-test.yml，随机端口）
```
