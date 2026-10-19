"""
原服务管理相关工具的实现。

原服务是流程编排中 serviceTask 节点的调用目标。
设计流程时，应优先查询原服务列表，把合适的原服务绑定到 serviceTask 节点，
避免生成无意义的人工任务节点导致流程无法自动推进。
"""

from typing import Any, Optional

try:
    from langchain_core.tools import tool
except ImportError:  # pragma: no cover
    try:
        from langchain.tools import tool
    except ImportError:
        def tool(func):  # type: ignore[misc]
            return func

from src.tools.client import client


def _prepare_query(params: dict[str, Any]) -> dict[str, Any]:
    """过滤空值，避免把 None 传给后端查询参数。"""
    return {k: v for k, v in params.items() if v is not None}


@tool
def list_original_services(
    applicationId: Optional[str] = None,
    keyword: Optional[str] = None,
    isAvailable: Optional[int] = None,
    ownerId: Optional[int] = None,
    pageNum: int = 1,
    pageSize: int = 20,
) -> dict[str, Any]:
    """分页查询原服务列表。

    设计流程编排时，应优先调用此工具获取可用的原服务，
    再把原服务ID（originalServiceId）绑定到 serviceTask 节点。
    """
    params = _prepare_query(
        {
            "applicationId": applicationId,
            "keyword": keyword,
            "isAvailable": isAvailable,
            "ownerId": ownerId,
            "pageNum": pageNum,
            "pageSize": pageSize,
        }
    )
    return client.request("GET", "/original-service/list", params=params)


@tool
def get_original_service_detail(id: str) -> dict[str, Any]:
    """查询单个原服务详情，包含协议类型、地址、请求头、参数、断言等。"""
    return client.request("GET", f"/original-service/{id}")


@tool
def create_original_service(
    name: str,
    applicationId: str,
    protocolType: int,
    address: str,
    requestType: Optional[str] = None,
    requestHeaders: Optional[str] = None,
    requestParams: Optional[str] = None,
    requestBody: Optional[str] = None,
    outputParams: Optional[str] = None,
    assertions: Optional[list[dict[str, Any]]] = None,
    isAvailable: int = 1,
) -> dict[str, Any]:
    """手动创建一个新的原服务。

    protocolType：1=HTTP，2=Dubbo。
    HTTP 场景下 requestType 必填（GET/POST/PUT/DELETE）。
    requestHeaders、requestParams、requestBody、outputParams 为 JSON 字符串。
    assertions 为断言规则对象列表，示例：
    [{"type": "STATUS_CODE", "operator": "EQUALS", "expression": "", "expectedValue": "200"}]
    """
    payload: dict[str, Any] = {
        "name": name,
        "applicationId": applicationId,
        "protocolType": protocolType,
        "address": address,
        "isAvailable": isAvailable,
    }
    if requestType is not None:
        payload["requestType"] = requestType
    if requestHeaders is not None:
        payload["requestHeaders"] = requestHeaders
    if requestParams is not None:
        payload["requestParams"] = requestParams
    if requestBody is not None:
        payload["requestBody"] = requestBody
    if outputParams is not None:
        payload["outputParams"] = outputParams
    if assertions is not None:
        payload["assertions"] = assertions
    return client.request("POST", "/original-service", json=payload)


@tool
def delete_original_service(id: str) -> dict[str, Any]:
    """根据ID删除单个原服务（逻辑删除）。"""
    return client.request("DELETE", f"/original-service/{id}")


@tool
def list_available_cases(
    applicationId: str,
    keyword: Optional[str] = None,
    protocolType: Optional[int] = None,
    pageNum: int = 1,
    pageSize: int = 20,
) -> dict[str, Any]:
    """查询尚未加入原服务的可选用例列表，用于批量添加用例到原服务。"""
    params = _prepare_query(
        {
            "applicationId": applicationId,
            "keyword": keyword,
            "protocolType": protocolType,
            "pageNum": pageNum,
            "pageSize": pageSize,
        }
    )
    return client.request("GET", "/original-service/available-cases", params=params)


@tool
def add_cases_to_original_service(
    caseIds: list[str], applicationId: str
) -> dict[str, Any]:
    """批量把现有用例添加为原服务。"""
    return client.request(
        "POST",
        "/original-service/add-cases",
        json={"caseIds": caseIds, "applicationId": applicationId},
    )
