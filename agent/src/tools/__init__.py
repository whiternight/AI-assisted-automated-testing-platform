"""
后端服务工具集合。

按 MCP（Model Context Protocol）标准提供工具定义与可执行函数，
当前仅包含自动化测试相关工具。
"""

from src.tools.automation import (
    backend_login,
    run_automation_test,
    get_automation_task,
    retry_automation_task,
    delete_automation_task,
    list_automation_tasks,
    direct_test,
    list_api_cases,
    debug_api_case,
)
from src.tools.flow import (
    list_flow_definitions,
    get_flow_definition_detail,
    deploy_flow_definition,
    execute_flow,
    get_flow_execution_result,
    get_flow_execution_history,
)
from src.tools.original_service import (
    list_original_services,
    get_original_service_detail,
    create_original_service,
    delete_original_service,
    list_available_cases,
    add_cases_to_original_service,
)
from src.tools.schemas import AUTOMATION_TOOLS, FLOW_TOOLS, ORIGINAL_SERVICE_TOOLS

__all__ = [
    # MCP 标准工具 Schema 列表
    "AUTOMATION_TOOLS",
    "FLOW_TOOLS",
    "ORIGINAL_SERVICE_TOOLS",
    # LangChain 可调用工具
    "backend_login",
    "run_automation_test",
    "get_automation_task",
    "retry_automation_task",
    "delete_automation_task",
    "list_automation_tasks",
    "direct_test",
    "list_api_cases",
    "debug_api_case",
    # 流程定义部署执行工具
    "list_flow_definitions",
    "get_flow_definition_detail",
    "deploy_flow_definition",
    "execute_flow",
    "get_flow_execution_result",
    "get_flow_execution_history",
    # 原服务管理工具
    "list_original_services",
    "get_original_service_detail",
    "create_original_service",
    "delete_original_service",
    "list_available_cases",
    "add_cases_to_original_service",
]
