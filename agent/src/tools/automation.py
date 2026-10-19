"""
自动化测试相关工具的实现。

每个工具同时满足：
1. MCP 标准：参数、描述与 schemas.py 中的 inputSchema 保持一致。
2. LangChain 标准：使用 @tool 装饰器，函数签名与 docstring 清晰。

工具列表：
- backend_login              登录获取 JWT Token
- run_automation_test        启动自动化测试任务
- get_automation_task        查询自动化测试任务详情
- retry_automation_task      重试失败用例
- delete_automation_task     删除自动化测试任务
- list_automation_tasks      分页查询自动化测试任务列表
- direct_test                直接测试接口（免登录、免保存用例）
- list_api_cases             查询API用例列表
- debug_api_case             调试执行单个API用例
"""

from typing import Any, Optional

try:
    from langchain_core.tools import tool
except ImportError:  # pragma: no cover - 兼容旧版 LangChain 或未同步依赖的环境
    try:
        from langchain.tools import tool
    except ImportError:
        # 最小兼容：@tool 退化为普通装饰器
        def tool(func):  # type: ignore[misc]
            return func

from src.tools.client import client


def _prepare_query(params: dict[str, Any]) -> dict[str, Any]:
    """过滤空值，避免把 None 传给后端查询参数。"""
    return {k: v for k, v in params.items() if v is not None}


@tool
def backend_login(username: str = "admin", password: str = "123456") -> dict[str, Any]:
    """使用用户名和密码登录 后端服务，获取 JWT Token。

    当调用其它 test-backend 接口返回 401/403 时，应先调用此工具重新登录。
    如果用户未提供凭证，默认使用 admin / 123456。
    """
    return client.login(username, password)


@tool
def run_automation_test(
    caseIds: list[str],
    executorId: int = 1,
    testSuiteName: str = "agent-auto-test",
    env: str = "default",
    timeout: Optional[int] = None,
) -> dict[str, Any]:
    """启动一个自动化测试任务，按用例ID列表批量执行API用例。

    执行是异步的，返回结果中会包含 taskId。调用本工具后，
    应继续调用 get_automation_task 查询任务状态，直到任务完成。
    """
    payload: dict[str, Any] = {
        "caseIds": caseIds,
        "executorId": executorId,
        "testSuiteName": testSuiteName,
        "env": env,
    }
    if timeout is not None:
        payload["timeout"] = timeout
    return client.request("POST", "/task/automation/run", json=payload)


@tool
def get_automation_task(taskId: str) -> dict[str, Any]:
    """根据任务ID查询自动化测试任务详情，包含状态、成功/失败统计、各用例结果。

    通常在调用 run_automation_test 后用于轮询任务状态。
    """
    return client.request("GET", f"/task/automation/{taskId}")


@tool
def retry_automation_task(taskId: str) -> dict[str, Any]:
    """对失败的用例重试执行指定的自动化测试任务。"""
    return client.request("POST", f"/task/automation/{taskId}/retry")


@tool
def delete_automation_task(taskId: str) -> dict[str, Any]:
    """根据任务ID删除指定的自动化测试任务。"""
    return client.request("DELETE", f"/task/automation/{taskId}")


@tool
def list_automation_tasks(page: int = 1, size: int = 10) -> dict[str, Any]:
    """分页查询自动化测试任务列表。"""
    return client.request(
        "GET", "/task/automation/list", params={"page": page, "size": size}
    )


@tool
def direct_test(
    protocolType: str,
    responseFormat: str,
    requestType: Optional[str] = None,
    address: Optional[str] = None,
    requestHeaders: Optional[dict[str, str]] = None,
    requestParams: Optional[dict[str, Any]] = None,
    requestBody: Optional[str] = None,
    serviceName: Optional[str] = None,
    methodName: Optional[str] = None,
    version: str = "1.0.0",
    group: Optional[str] = None,
    dubboUrl: Optional[str] = None,
    paramTypes: Optional[list[str]] = None,
    paramValues: Optional[list[Any]] = None,
    responseAssertions: Optional[list[dict[str, Any]]] = None,
    timeout: int = 30000,
) -> dict[str, Any]:
    """直接测试一个HTTP或Dubbo接口，无需提前创建用例。"""
    payload: dict[str, Any] = {
        "protocolType": protocolType,
        "responseFormat": responseFormat,
        "timeout": timeout,
    }

    if protocolType == "HTTP":
        payload.update(
            {
                "requestType": requestType,
                "address": address,
                "requestHeaders": requestHeaders,
                "requestParams": requestParams,
                "requestBody": requestBody,
            }
        )
    elif protocolType == "DUBBO":
        payload.update(
            {
                "serviceName": serviceName,
                "methodName": methodName,
                "version": version,
                "group": group,
                "dubboUrl": dubboUrl,
                "paramTypes": paramTypes,
                "paramValues": paramValues,
            }
        )

    if responseAssertions is not None:
        payload["responseAssertions"] = responseAssertions

    return client.request("POST", "/test/direct", json=payload)


@tool
def list_api_cases(
    keyword: Optional[str] = None,
    applicationId: Optional[str] = None,
    businessDomainId: Optional[int] = None,
    protocolType: Optional[str] = None,
    isAutomationEnabled: Optional[bool] = None,
    ownerId: Optional[int] = None,
    env: Optional[str] = None,
    pageNum: int = 1,
    pageSize: int = 20,
) -> dict[str, Any]:
    """分页查询API用例列表。常用于为 run_automation_test 挑选要执行的用例。"""
    params = _prepare_query(
        {
            "keyword": keyword,
            "applicationId": applicationId,
            "businessDomainId": businessDomainId,
            "protocolType": protocolType,
            "isAutomationEnabled": isAutomationEnabled,
            "ownerId": ownerId,
            "env": env,
            "pageNum": pageNum,
            "pageSize": pageSize,
        }
    )
    return client.request("GET", "/api-case/list", params=params)


@tool
def debug_api_case(
    caseId: str,
    env: str = "default",
    headers: Optional[dict[str, str]] = None,
    params: Optional[dict[str, Any]] = None,
    body: Optional[Any] = None,
    timeout: Optional[int] = None,
) -> dict[str, Any]:
    """调试执行单个API用例。"""
    payload: dict[str, Any] = {"env": env}
    if headers is not None:
        payload["headers"] = headers
    if params is not None:
        payload["params"] = params
    if body is not None:
        payload["body"] = body
    if timeout is not None:
        payload["timeout"] = timeout
    return client.request("POST", f"/api-case/{caseId}/debug", json=payload)
