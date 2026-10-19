"""
按 MCP（Model Context Protocol）标准定义的自动化测试工具 Schema 列表。

MCP 工具格式：
    {
        "name": "tool_name",
        "description": "工具用途与使用时机",
        "inputSchema": {
            "type": "object",
            "properties": {...},
            "required": [...]
        }
    }

这些 Schema 可直接用于 MCP Server 注册，也可供 LLM 理解工具参数。
"""

from typing import Any


AUTOMATION_TOOLS: list[dict[str, Any]] = [
    {
        "name": "backend_login",
        "description": "使用用户名和密码登录 后端服务，获取 JWT Token。"
        "当调用其它 test-backend 接口返回 401/403 时，应先调用此工具重新登录。"
        "如果用户未提供凭证，默认使用 admin / 123456。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "username": {
                    "type": "string",
                    "default": "admin",
                    "description": "登录用户名，默认 admin",
                },
                "password": {
                    "type": "string",
                    "default": "123456",
                    "description": "登录密码，默认 123456",
                },
            },
        },
    },
    {
        "name": "run_automation_test",
        "description": "启动一个自动化测试任务，按用例ID列表批量执行API用例。"
        "执行是异步的，返回任务ID后必须通过 get_automation_task 轮询结果。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "caseIds": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "参与自动化测试的用例ID列表",
                },
                "executorId": {
                    "type": "integer",
                    "default": 1,
                    "description": "执行人ID，默认1",
                },
                "testSuiteName": {
                    "type": "string",
                    "default": "agent-auto-test",
                    "description": "用例集名称，用于标识本次自动化测试任务，默认 agent-auto-test",
                },
                "env": {
                    "type": "string",
                    "default": "default",
                    "description": "执行环境，例如 dev、test、prod，默认为 default",
                },
                "timeout": {
                    "type": "integer",
                    "description": "单用例超时时间（毫秒），可选",
                },
            },
            "required": ["caseIds"],
        },
    },
    {
        "name": "get_automation_task",
        "description": "根据任务ID查询自动化测试任务详情，包含状态、成功/失败统计、各用例结果。"
        "通常在调用 run_automation_test 后用于轮询任务状态。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "taskId": {
                    "type": "string",
                    "description": "自动化测试任务ID",
                },
            },
            "required": ["taskId"],
        },
    },
    {
        "name": "retry_automation_task",
        "description": "对失败的用例重试执行指定的自动化测试任务，返回更新后的任务信息。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "taskId": {
                    "type": "string",
                    "description": "需要重试的自动化测试任务ID",
                },
            },
            "required": ["taskId"],
        },
    },
    {
        "name": "delete_automation_task",
        "description": "根据任务ID删除指定的自动化测试任务。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "taskId": {
                    "type": "string",
                    "description": "需要删除的自动化测试任务ID",
                },
            },
            "required": ["taskId"],
        },
    },
    {
        "name": "list_automation_tasks",
        "description": "分页查询自动化测试任务列表。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "page": {
                    "type": "integer",
                    "default": 1,
                    "description": "页码，默认1",
                },
                "size": {
                    "type": "integer",
                    "default": 10,
                    "description": "每页大小，默认10",
                },
            },
        },
    },
    {
        "name": "direct_test",
        "description": "直接测试一个HTTP或Dubbo接口，无需提前创建用例，也不保存任何数据。"
        "适合临时调试或验证接口可用性。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "protocolType": {
                    "type": "string",
                    "enum": ["HTTP", "DUBBO"],
                    "description": "协议类型：HTTP 或 DUBBO",
                },
                "requestType": {
                    "type": "string",
                    "enum": ["GET", "POST", "PUT", "DELETE"],
                    "description": "HTTP请求方法，protocolType=HTTP时必填",
                },
                "address": {
                    "type": "string",
                    "description": "HTTP完整URL，protocolType=HTTP时必填",
                },
                "requestHeaders": {
                    "type": "object",
                    "description": "HTTP请求头",
                },
                "requestParams": {
                    "type": "object",
                    "description": "HTTP请求参数",
                },
                "requestBody": {
                    "type": "string",
                    "description": "HTTP请求体",
                },
                "serviceName": {
                    "type": "string",
                    "description": "Dubbo服务全名，protocolType=DUBBO时必填",
                },
                "methodName": {
                    "type": "string",
                    "description": "Dubbo方法名，protocolType=DUBBO时必填",
                },
                "version": {
                    "type": "string",
                    "default": "1.0.0",
                    "description": "Dubbo版本号",
                },
                "group": {
                    "type": "string",
                    "description": "Dubbo服务分组",
                },
                "dubboUrl": {
                    "type": "string",
                    "description": "Dubbo直连地址，不填则走注册中心",
                },
                "paramTypes": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Dubbo参数类型全名列表",
                },
                "paramValues": {
                    "type": "array",
                    "description": "Dubbo参数值列表",
                },
                "responseFormat": {
                    "type": "string",
                    "enum": ["JSON", "BODY", "HTML", "TEXT"],
                    "description": "响应格式",
                },
                "responseAssertions": {
                    "type": "array",
                    "description": "断言规则列表",
                },
                "timeout": {
                    "type": "integer",
                    "default": 30000,
                    "description": "超时时间（毫秒），默认30000",
                },
            },
            "required": ["protocolType", "responseFormat"],
        },
    },
    {
        "name": "list_api_cases",
        "description": "分页查询API用例列表，支持按应用、协议类型、自动化开关、环境等条件过滤。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "keyword": {
                    "type": "string",
                    "description": "用例名称模糊搜索",
                },
                "applicationId": {
                    "type": "string",
                    "description": "应用ID",
                },
                "businessDomainId": {
                    "type": "integer",
                    "description": "业务域ID",
                },
                "protocolType": {
                    "type": "string",
                    "enum": ["HTTP", "DUBBO"],
                    "description": "协议类型",
                },
                "isAutomationEnabled": {
                    "type": "boolean",
                    "description": "是否开启自动化",
                },
                "ownerId": {
                    "type": "integer",
                    "description": "负责人ID",
                },
                "env": {
                    "type": "string",
                    "description": "环境",
                },
                "pageNum": {
                    "type": "integer",
                    "default": 1,
                    "description": "页码，默认1",
                },
                "pageSize": {
                    "type": "integer",
                    "default": 20,
                    "description": "每页大小，默认20",
                },
            },
        },
    },
    {
        "name": "debug_api_case",
        "description": "调试执行单个API用例，支持覆盖环境、请求头、参数和请求体。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "caseId": {
                    "type": "string",
                    "description": "API用例ID",
                },
                "env": {
                    "type": "string",
                    "default": "default",
                    "description": "执行环境，默认default",
                },
                "headers": {
                    "type": "object",
                    "description": "覆盖默认请求头",
                },
                "params": {
                    "type": "object",
                    "description": "覆盖默认请求参数",
                },
                "body": {
                    "type": "any",
                    "description": "覆盖默认请求体",
                },
                "timeout": {
                    "type": "integer",
                    "description": "超时时间（毫秒），不传则用用例默认值",
                },
            },
            "required": ["caseId"],
        },
    },
]


FLOW_TOOLS: list[dict[str, Any]] = [
    {
        "name": "list_flow_definitions",
        "description": "分页查询流程定义列表，支持按名称和应用ID过滤。"
        "返回列表中的流程包含 id、name、status 等字段，status=2 表示已发布可执行。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "pageNum": {"type": "integer", "default": 1, "description": "页码，默认1"},
                "pageSize": {"type": "integer", "default": 10, "description": "每页大小，默认10"},
                "name": {"type": "string", "description": "流程名称模糊搜索"},
                "applicationId": {"type": "string", "description": "应用ID"},
            },
        },
    },
    {
        "name": "get_flow_definition_detail",
        "description": "根据流程定义ID查询详情，包含 BPMN XML、节点配置、最近执行记录等。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "flowDefinitionId": {
                    "type": "string",
                    "description": "流程定义ID",
                },
            },
            "required": ["flowDefinitionId"],
        },
    },
    {
        "name": "deploy_flow_definition",
        "description": "部署流程定义到 Flowable 引擎，使其变为可执行状态（status=2）。"
        "支持新建部署（不提供 flowDefinitionId）或升级部署（提供已有 flowDefinitionId）。"
        "若流程包含 serviceTask 节点，应在 nodes 中传入节点绑定配置。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "name": {"type": "string", "description": "流程名称"},
                "applicationId": {"type": "string", "description": "应用ID"},
                "bpmnXml": {"type": "string", "description": "BPMN 2.0 XML 字符串，新建部署时与 nodes 二选一"},
                "flowDefinitionId": {"type": "string", "description": "已有流程定义ID，升级部署时传入"},
                "businessDomainId": {"type": "integer", "description": "业务域ID，可选"},
                "ownerId": {"type": "integer", "description": "负责人ID，可选"},
                "nodes": {
                    "anyOf": [
                        {"type": "array", "description": "serviceTask 节点绑定配置列表", "items": {"type": "object"}},
                        {"type": "string", "description": "节点配置列表的 JSON 字符串"},
                    ],
                    "description": "serviceTask 节点绑定配置，新建部署时与 bpmnXml 二选一，工具会根据 nodes 自动生成 BPMN XML",
                },
            },
            "required": ["name", "applicationId"],
        },
    },
    {
        "name": "execute_flow",
        "description": "启动指定流程定义的执行，返回执行编号、流程实例ID和状态。"
        "executeMode=1 为同步执行（默认），executeMode=2 为异步执行，需要后续调用 get_flow_execution_result 轮询。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "flowDefinitionId": {"type": "string", "description": "流程定义ID"},
                "inputParams": {
                    "anyOf": [
                        {"type": "object", "description": "全局流程变量对象"},
                        {"type": "string", "description": "全局流程变量的 JSON 字符串"},
                    ],
                    "description": "全局流程变量，可选",
                },
                "env": {"type": "string", "default": "default", "description": "执行环境，默认 default"},
                "executeMode": {"type": "integer", "default": 1, "description": "执行模式：1同步，2异步"},
                "timeout": {"type": "integer", "description": "超时时间（毫秒），可选"},
            },
            "required": ["flowDefinitionId"],
        },
    },
    {
        "name": "get_flow_execution_result",
        "description": "根据流程实例ID查询执行结果，包含节点轨迹、网关评估、输出参数、错误详情等。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "processInstanceId": {"type": "string", "description": "流程实例ID"},
            },
            "required": ["processInstanceId"],
        },
    },
    {
        "name": "get_flow_execution_history",
        "description": "分页查询流程执行历史记录。status 取值：0=执行中，1=成功，2=失败，3=部分成功。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "pageNum": {"type": "integer", "default": 1, "description": "页码，默认1"},
                "pageSize": {"type": "integer", "default": 20, "description": "每页大小，默认20"},
                "flowDefinitionId": {"type": "string", "description": "流程定义ID，可选"},
                "status": {"type": "integer", "description": "执行状态过滤，可选"},
            },
        },
    },
]


ORIGINAL_SERVICE_TOOLS: list[dict[str, Any]] = [
    {
        "name": "list_original_services",
        "description": "分页查询原服务列表。设计流程编排时，应优先查询可用的原服务并绑定到 serviceTask 节点。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "applicationId": {"type": "string", "description": "应用ID，可选"},
                "keyword": {"type": "string", "description": "原服务名称模糊搜索，可选"},
                "isAvailable": {"type": "integer", "description": "可用状态：1=可用，0=不可用，可选"},
                "ownerId": {"type": "integer", "description": "负责人ID，可选"},
                "pageNum": {"type": "integer", "default": 1, "description": "页码，默认1"},
                "pageSize": {"type": "integer", "default": 20, "description": "每页大小，默认20"},
            },
        },
    },
    {
        "name": "get_original_service_detail",
        "description": "查询单个原服务详情，包含协议类型、请求地址、请求头、参数、断言等。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "原服务ID"},
            },
            "required": ["id"],
        },
    },
    {
        "name": "create_original_service",
        "description": "手动创建一个新的原服务。protocolType=1 表示 HTTP，=2 表示 Dubbo。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "name": {"type": "string", "description": "原服务名称"},
                "applicationId": {"type": "string", "description": "所属应用ID"},
                "protocolType": {"type": "integer", "description": "协议类型：1=HTTP，2=Dubbo"},
                "address": {"type": "string", "description": "请求地址（HTTP URL 或 Dubbo 服务路径）"},
                "requestType": {"type": "string", "description": "HTTP 请求方式：GET/POST/PUT/DELETE"},
                "requestHeaders": {"type": "string", "description": "请求头 JSON 字符串"},
                "requestParams": {"type": "string", "description": "请求参数 JSON 字符串"},
                "requestBody": {"type": "string", "description": "请求体 JSON 字符串"},
                "outputParams": {"type": "string", "description": "出参映射 JSON 字符串"},
                "assertions": {
                    "type": "array",
                    "description": "断言规则列表",
                    "items": {"type": "object"},
                },
                "isAvailable": {"type": "integer", "default": 1, "description": "可用状态：1=可用，0=不可用"},
            },
            "required": ["name", "applicationId", "protocolType", "address"],
        },
    },
    {
        "name": "delete_original_service",
        "description": "根据ID删除单个原服务（逻辑删除）。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "原服务ID"},
            },
            "required": ["id"],
        },
    },
    {
        "name": "list_available_cases",
        "description": "查询尚未加入原服务的可选用例列表，用于批量添加用例到原服务。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "applicationId": {"type": "string", "description": "应用ID"},
                "keyword": {"type": "string", "description": "用例名称模糊搜索"},
                "protocolType": {"type": "integer", "description": "协议类型：1=HTTP，2=Dubbo"},
                "pageNum": {"type": "integer", "default": 1, "description": "页码，默认1"},
                "pageSize": {"type": "integer", "default": 20, "description": "每页大小，默认20"},
            },
            "required": ["applicationId"],
        },
    },
    {
        "name": "add_cases_to_original_service",
        "description": "批量把现有用例添加为原服务。",
        "inputSchema": {
            "type": "object",
            "properties": {
                "caseIds": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "用例ID列表",
                },
                "applicationId": {"type": "string", "description": "所属应用ID"},
            },
            "required": ["caseIds", "applicationId"],
        },
    },
]
