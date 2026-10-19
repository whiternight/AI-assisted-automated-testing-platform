"""
流程定义部署与执行相关工具的实现。

工具列表：
- list_flow_definitions      分页查询流程定义列表
- get_flow_definition_detail 查询流程定义详情
- deploy_flow_definition     部署流程定义（新建或升级）
- execute_flow               启动流程执行
- get_flow_execution_result  查询流程执行结果/详情
- get_flow_execution_history 分页查询流程执行历史
"""

import json
from typing import Any, Optional, Union

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


def _normalize_deploy_node(node: dict[str, Any]) -> dict[str, Any]:
    """将 LLM/详情返回的节点格式转换为部署接口要求的格式。

    支持字段别名：nodeKey/id/nodeId、nodeName/name、nodeType/type、originalServiceId/serviceId。
    若节点关联原服务且未提供映射/断言，会自动从原服务详情补齐，确保编辑回填完整。
    """
    normalized: dict[str, Any] = dict(node)
    # 节点标识字段兼容
    if "nodeKey" not in normalized:
        if "id" in normalized:
            normalized["nodeKey"] = normalized["id"]
        elif "nodeId" in normalized:
            normalized["nodeKey"] = normalized["nodeId"]
    if "nodeName" not in normalized and "name" in normalized:
        normalized["nodeName"] = normalized["name"]
    # 原服务 ID 兼容
    if "originalServiceId" not in normalized and "serviceId" in normalized:
        normalized["originalServiceId"] = normalized["serviceId"]
    # nodeType 兼容：显式 type / 字符串 "3" / 缺失但存在 originalServiceId 时默认 serviceTask
    if "nodeType" not in normalized and "type" in normalized:
        node_type = normalized["type"]
        normalized["nodeType"] = 3 if node_type in ("serviceTask", "bpmn:ServiceTask") else node_type
    node_type = normalized.get("nodeType")
    if node_type in ("serviceTask", "bpmn:ServiceTask"):
        normalized["nodeType"] = 3
    elif isinstance(node_type, str) and node_type.isdigit():
        normalized["nodeType"] = int(node_type)
    elif node_type is None and normalized.get("originalServiceId"):
        normalized["nodeType"] = 3

    # 若节点未提供名称、映射或断言，尝试从原服务详情补齐
    service_id = normalized.get("originalServiceId")
    if service_id:
        try:
            detail = client.request("GET", f"/original-service/{service_id}")
            svc = detail.get("data", {})
            if not normalized.get("nodeName") and svc.get("name"):
                normalized["nodeName"] = svc.get("name")
            if not normalized.get("inputParams") and svc.get("inputParams"):
                normalized["inputParams"] = svc.get("inputParams")
            if not normalized.get("outputParams") and svc.get("outputParams"):
                normalized["outputParams"] = svc.get("outputParams")
            if not normalized.get("assertions") and svc.get("assertions"):
                normalized["assertions"] = svc.get("assertions")
        except Exception:
            pass

    # 默认值
    normalized.setdefault("timeout", 30000)
    normalized.setdefault("retryCount", 0)
    normalized.setdefault("retryInterval", 0)
    normalized.setdefault("retryType", 1)
    return normalized


def _xml_escape(value: str) -> str:
    """对 XML 属性值做基本转义。"""
    return (
        str(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "&apos;")
    )


def _is_bpmn_xml_complete(xml: str) -> bool:
    """检查 BPMN XML 是否包含完整的流程定义与图形信息。"""
    if not xml or not xml.strip():
        return False
    xml_lower = xml.lower()
    return (
        "<process" in xml_lower
        and "</process>" in xml_lower
        and "<bpmndi:bpmndiagram" in xml_lower
    )


def _build_service_task_xml(
    node_id: str,
    node_name: str,
    service_id: str,
    method: str = "POST",
    address: str = "",
    input_mapping: str = "",
    output_mapping: str = "",
    assertions: str = "",
) -> str:
    """生成单个 serviceTask 的 XML 片段。"""
    attrs = [f'id="{_xml_escape(node_id)}"', f'name="{_xml_escape(node_name)}"']
    if service_id:
        attrs.append(f'flowable:serviceId="{_xml_escape(service_id)}"')
        attrs.append(f'flowable:serviceName="{_xml_escape(node_name)}"')
    if method:
        attrs.append(f'flowable:httpRequestMethod="{_xml_escape(method)}"')
    if address:
        attrs.append(f'flowable:serviceAddress="{_xml_escape(address)}"')
        attrs.append(f'flowable:httpRequestUrl="{_xml_escape(address)}"')
    if input_mapping:
        attrs.append(f'flowable:inputMapping="{_xml_escape(input_mapping)}"')
    if output_mapping:
        attrs.append(f'flowable:outputMapping="{_xml_escape(output_mapping)}"')
    if assertions:
        attrs.append(f'flowable:assertions="{_xml_escape(assertions)}"')
    return f"    <serviceTask {' '.join(attrs)} />"


def _node_type_is_gateway(node: dict[str, Any]) -> bool:
    """判断节点是否为排他网关。"""
    nt = node.get("nodeType") or node.get("type") or ""
    return str(nt).lower() in ("exclusivegateway", "exclusive_gateway", "gateway")


def _build_bpmn_xml(
    process_name: str, nodes: list[dict[str, Any]], application_id: str = ""
) -> str:
    """根据节点配置生成最小可部署 BPMN XML（包含 BPMN DI 图形信息）。

    流程结构：startEvent -> serviceTask1 -> ... -> endEvent
    若节点中包含 exclusiveGateway，则自动切换为 _build_gateway_bpmn_xml。
    """
    if any(_node_type_is_gateway(n) for n in nodes):
        return _build_gateway_bpmn_xml(process_name, nodes, application_id=application_id)

    process_id = f"Process_{application_id or 'agent'}"
    start_id = "StartEvent_1"
    end_id = "EndEvent_1"

    service_tasks: list[str] = []
    sequence_flows: list[str] = []
    shapes: list[str] = []
    edges: list[str] = []

    # 布局常量
    start_x, start_y = 200, 200
    start_w, start_h = 36, 36
    task_w, task_h = 100, 80
    task_y = 180
    task_x_start = 350
    task_x_step = 180
    end_w, end_h = 36, 36
    end_y = 200

    # 开始事件图形
    shapes.append(
        f'    <bpmndi:BPMNShape id="{_xml_escape(start_id)}_di" bpmnElement="{_xml_escape(start_id)}">\n'
        f'      <dc:Bounds x="{start_x}" y="{start_y}" width="{start_w}" height="{start_h}" />\n'
        f'    </bpmndi:BPMNShape>'
    )

    prev_id = start_id
    prev_x = start_x + start_w // 2
    prev_y = start_y + start_h // 2
    for idx, node in enumerate(nodes, start=1):
        node_id = node.get("nodeKey") or node.get("nodeId") or node.get("id") or f"ServiceTask_{idx}"
        node_name = node.get("nodeName") or node.get("name") or f"服务节点{idx}"
        service_id = node.get("originalServiceId") or ""

        # 尝试补齐原服务信息
        method, address = "POST", ""
        input_mapping = node.get("inputParams") or ""
        output_mapping = node.get("outputParams") or ""
        assertions = node.get("assertions") or ""
        if service_id:
            try:
                detail = client.request("GET", f"/original-service/{service_id}")
                svc = detail.get("data", {})
                method = svc.get("requestType") or "POST"
                address = svc.get("address") or ""
                # 若节点未指定名称，使用原服务名称作为显示名
                if not node.get("nodeName") and not node.get("name") and svc.get("name"):
                    node_name = svc.get("name")
                # 若节点未指定映射/断言，继承原服务配置
                if not input_mapping and svc.get("inputParams"):
                    input_mapping = svc.get("inputParams")
                if not output_mapping and svc.get("outputParams"):
                    output_mapping = svc.get("outputParams")
                if not assertions and svc.get("assertions"):
                    assertions = svc.get("assertions")
            except Exception:
                pass

        service_tasks.append(
            _build_service_task_xml(
                node_id=node_id,
                node_name=node_name,
                service_id=service_id,
                method=method,
                address=address,
                input_mapping=input_mapping,
                output_mapping=output_mapping,
                assertions=assertions,
            )
        )
        sequence_flows.append(
            f'    <sequenceFlow id="Flow_{idx}" sourceRef="{_xml_escape(prev_id)}" targetRef="{_xml_escape(node_id)}" />'
        )

        # serviceTask 图形
        task_x = task_x_start + (idx - 1) * task_x_step
        shapes.append(
            f'    <bpmndi:BPMNShape id="{_xml_escape(node_id)}_di" bpmnElement="{_xml_escape(node_id)}">\n'
            f'      <dc:Bounds x="{task_x}" y="{task_y}" width="{task_w}" height="{task_h}" />\n'
            f'    </bpmndi:BPMNShape>'
        )

        # 连线图形
        curr_x = task_x
        curr_y = task_y + task_h // 2
        edges.append(
            f'    <bpmndi:BPMNEdge id="Flow_{idx}_di" bpmnElement="Flow_{idx}">\n'
            f'      <di:waypoint x="{prev_x}" y="{prev_y}" />\n'
            f'      <di:waypoint x="{curr_x}" y="{curr_y}" />\n'
            f'    </bpmndi:BPMNEdge>'
        )

        prev_id = node_id
        prev_x = task_x + task_w
        prev_y = curr_y

    # 结束事件位置
    end_x = task_x_start + len(nodes) * task_x_step
    sequence_flows.append(
        f'    <sequenceFlow id="Flow_end" sourceRef="{_xml_escape(prev_id)}" targetRef="{_xml_escape(end_id)}" />'
    )
    end_center_x = end_x + end_w // 2
    end_center_y = end_y + end_h // 2
    shapes.append(
        f'    <bpmndi:BPMNShape id="{_xml_escape(end_id)}_di" bpmnElement="{_xml_escape(end_id)}">\n'
        f'      <dc:Bounds x="{end_x}" y="{end_y}" width="{end_w}" height="{end_h}" />\n'
        f'    </bpmndi:BPMNShape>'
    )
    edges.append(
        f'    <bpmndi:BPMNEdge id="Flow_end_di" bpmnElement="Flow_end">\n'
        f'      <di:waypoint x="{prev_x}" y="{prev_y}" />\n'
        f'      <di:waypoint x="{end_center_x}" y="{end_center_y}" />\n'
        f'    </bpmndi:BPMNEdge>'
    )

    return f'''<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:flowable="http://flowable.org/bpmn"
  id="Definitions_1"
  targetNamespace="http://bpmn.io/schema/bpmn">
  <process id="{_xml_escape(process_id)}" name="{_xml_escape(process_name)}" isExecutable="true">
    <startEvent id="{_xml_escape(start_id)}" name="开始" />
{chr(10).join(service_tasks)}
    <endEvent id="{_xml_escape(end_id)}" name="结束" />
{chr(10).join(sequence_flows)}
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="{_xml_escape(process_id)}">
{chr(10).join(shapes)}
{chr(10).join(edges)}
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>'''


def _build_gateway_bpmn_xml(
    process_name: str, nodes: list[dict[str, Any]], application_id: str = ""
) -> str:
    """生成包含排他网关的 BPMN XML（包含 DI 图形信息）。

    节点约定：
    - serviceTask：普通节点字典，至少包含 nodeKey、nodeName、originalServiceId。
    - exclusiveGateway：nodeType 为 "exclusiveGateway"，包含 branches 列表。
      每个 branch 为 {"condition": "UEL表达式（可选）", "target": "目标 nodeKey"}。
      未提供 condition 的分支会被视为默认分支（true）。
    - 分支目标后的任务会按 nodes 列表顺序继续执行，直到结束事件。

    典型结构示例：
    [
      {"nodeKey":"create","nodeName":"创建用户","originalServiceId":"..."},
      {"nodeKey":"query","nodeName":"查询用户","originalServiceId":"..."},
      {"nodeType":"exclusiveGateway","nodeKey":"gw","nodeName":"判断状态",
       "branches":[
         {"condition":"${active == true}","target":"send_notify"},
         {"condition":"${active != true}","target":"activate_user"}
       ]},
      {"nodeKey":"activate_user","nodeName":"激活用户","originalServiceId":"..."},
      {"nodeKey":"send_notify","nodeName":"发送激活通知","originalServiceId":"..."}
    ]
    """
    process_id = f"Process_{application_id or 'agent'}"
    start_id = "StartEvent_1"
    end_id = "EndEvent_1"

    # 索引与规范化
    node_by_id: dict[str, dict[str, Any]] = {}
    ordered_ids: list[str] = []
    for idx, node in enumerate(nodes, start=1):
        node_id = node.get("nodeKey") or node.get("nodeId") or node.get("id") or f"Node_{idx}"
        node = dict(node)
        node["_id"] = node_id
        node_by_id[node_id] = node
        ordered_ids.append(node_id)

    # 补齐原服务信息并生成 serviceTask XML
    service_tasks: list[str] = []
    gateway_elements: list[str] = []
    shapes: list[str] = []
    sequence_flows: list[str] = []
    edges: list[str] = []

    # 布局常量（与线性版本保持一致）
    start_x, start_y = 200, 200
    start_w, start_h = 36, 36
    task_w, task_h = 100, 80
    task_y = 180
    task_x_start = 350
    task_x_step = 180
    gateway_size = 50
    gateway_y = 195
    end_w, end_h = 36, 36
    end_y = 200

    shapes.append(
        f'    <bpmndi:BPMNShape id="{_xml_escape(start_id)}_di" bpmnElement="{_xml_escape(start_id)}">\n'
        f'      <dc:Bounds x="{start_x}" y="{start_y}" width="{start_w}" height="{start_h}" />\n'
        f'    </bpmndi:BPMNShape>'
    )

    flow_counter = 0

    def _next_node_id(current_id: str) -> Optional[str]:
        try:
            idx = ordered_ids.index(current_id)
            if idx + 1 < len(ordered_ids):
                return ordered_ids[idx + 1]
        except ValueError:
            pass
        return None

    def _add_edge(source_id: str, target_id: str, condition: str = "", flow_id: Optional[str] = None) -> str:
        nonlocal flow_counter
        flow_counter += 1
        fid = flow_id or f"Flow_{flow_counter}"
        s_ref = _xml_escape(source_id)
        t_ref = _xml_escape(target_id)
        sf = f'    <sequenceFlow id="{fid}" sourceRef="{s_ref}" targetRef="{t_ref}"'
        if condition:
            sf += f'>\n      <conditionExpression xsi:type="tFormalExpression">{_xml_escape(condition)}</conditionExpression>\n    </sequenceFlow>'
        else:
            sf += " />"
        sequence_flows.append(sf)
        return fid

    def _node_xml(node: dict[str, Any]) -> str:
        """生成 BPMN 元素 XML（serviceTask 或 exclusiveGateway）。"""
        node_id = node["_id"]
        node_name = node.get("nodeName") or node.get("name") or node_id
        if _node_type_is_gateway(node):
            return f'    <exclusiveGateway id="{_xml_escape(node_id)}" name="{_xml_escape(node_name)}" />'

        service_id = node.get("originalServiceId") or ""
        method, address = "POST", ""
        input_mapping = node.get("inputParams") or ""
        output_mapping = node.get("outputParams") or ""
        assertions = node.get("assertions") or ""
        if service_id:
            try:
                detail = client.request("GET", f"/original-service/{service_id}")
                svc = detail.get("data", {})
                method = svc.get("requestType") or "POST"
                address = svc.get("address") or ""
                if not node.get("nodeName") and not node.get("name") and svc.get("name"):
                    node_name = svc.get("name")
                if not input_mapping and svc.get("inputParams"):
                    input_mapping = svc.get("inputParams")
                if not output_mapping and svc.get("outputParams"):
                    output_mapping = svc.get("outputParams")
                if not assertions and svc.get("assertions"):
                    assertions = svc.get("assertions")
            except Exception:
                pass
        return _build_service_task_xml(
            node_id=node_id,
            node_name=node_name,
            service_id=service_id,
            method=method,
            address=address,
            input_mapping=input_mapping,
            output_mapping=output_mapping,
            assertions=assertions,
        )

    # 生成元素 XML 与图形（按列表顺序布局）
    layout_info: dict[str, dict[str, int]] = {}
    for idx, node_id in enumerate(ordered_ids, start=1):
        node = node_by_id[node_id]
        x = task_x_start + (idx - 1) * task_x_step
        if _node_type_is_gateway(node):
            gateway_elements.append(_node_xml(node))
            shapes.append(
                f'    <bpmndi:BPMNShape id="{_xml_escape(node_id)}_di" bpmnElement="{_xml_escape(node_id)}">\n'
                f'      <dc:Bounds x="{x}" y="{gateway_y}" width="{gateway_size}" height="{gateway_size}" />\n'
                f'    </bpmndi:BPMNShape>'
            )
            layout_info[node_id] = {"x": x, "y": gateway_y + gateway_size // 2, "w": gateway_size}
        else:
            service_tasks.append(_node_xml(node))
            shapes.append(
                f'    <bpmndi:BPMNShape id="{_xml_escape(node_id)}_di" bpmnElement="{_xml_escape(node_id)}">\n'
                f'      <dc:Bounds x="{x}" y="{task_y}" width="{task_w}" height="{task_h}" />\n'
                f'    </bpmndi:BPMNShape>'
            )
            layout_info[node_id] = {"x": x, "y": task_y + task_h // 2, "w": task_w}

    # 生成连线
    # start -> first node
    first_id = ordered_ids[0]
    _add_edge(start_id, first_id)
    edges.append(
        f'    <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">\n'
        f'      <di:waypoint x="{start_x + start_w}" y="{start_y + start_h // 2}" />\n'
        f'      <di:waypoint x="{layout_info[first_id]['x']}" y="{layout_info[first_id]['y']}" />\n'
        f'    </bpmndi:BPMNEdge>'
    )

    edge_flow_ids: dict[tuple[str, str], str] = {}

    def _record_edge_flow(source: str, target: str, fid: str) -> None:
        edge_flow_ids[(source, target)] = fid

    # 节点间连线
    for idx, node_id in enumerate(ordered_ids):
        node = node_by_id[node_id]
        next_id = _next_node_id(node_id)

        if _node_type_is_gateway(node):
            branches = node.get("branches") or []
            # 确保至少有一个默认分支，避免 Flowable 无匹配条件时异常
            has_default = any(not (b.get("condition") or "").strip() for b in branches)
            if not has_default and branches:
                # 把最后一个分支改为默认分支（空条件 = 默认流）
                branches = [dict(b) for b in branches]
                branches[-1]["condition"] = ""
            for b in branches:
                target = b.get("target")
                if not target or target not in node_by_id:
                    continue
                cond = b.get("condition") or ""
                fid = _add_edge(node_id, target, condition=cond)
                _record_edge_flow(node_id, target, fid)
        else:
            # serviceTask：连到下一个节点；若当前是最后一个节点则连到结束事件
            if next_id:
                fid = _add_edge(node_id, next_id)
                _record_edge_flow(node_id, next_id, fid)
            else:
                fid = _add_edge(node_id, end_id)
                _record_edge_flow(node_id, end_id, fid)

    # 结束事件布局
    end_x = task_x_start + len(ordered_ids) * task_x_step
    end_center_x = end_x + end_w // 2
    end_center_y = end_y + end_h // 2
    shapes.append(
        f'    <bpmndi:BPMNShape id="{_xml_escape(end_id)}_di" bpmnElement="{_xml_escape(end_id)}">\n'
        f'      <dc:Bounds x="{end_x}" y="{end_y}" width="{end_w}" height="{end_h}" />\n'
        f'    </bpmndi:BPMNShape>'
    )

    # 生成所有 BPMNEdge 图形
    for (source, target), fid in edge_flow_ids.items():
        sx = layout_info.get(source, {}).get("x", 0) + layout_info.get(source, {}).get("w", 0) // 2
        sy = layout_info.get(source, {}).get("y", 0)
        if source == start_id:
            sx = start_x + start_w
            sy = start_y + start_h // 2
        tx = layout_info.get(target, {}).get("x", 0)
        ty = layout_info.get(target, {}).get("y", 0)
        if target == end_id:
            tx = end_center_x
            ty = end_center_y
        edges.append(
            f'    <bpmndi:BPMNEdge id="{_xml_escape(fid)}_di" bpmnElement="{_xml_escape(fid)}">\n'
            f'      <di:waypoint x="{sx}" y="{sy}" />\n'
            f'      <di:waypoint x="{tx}" y="{ty}" />\n'
            f'    </bpmndi:BPMNEdge>'
        )

    # 注：第一个 start->first 的 edge 已经添加；这里避免重复，按 edge_flow_ids 重新生成全部边
    # 移除之前手动添加的 Flow_1_di，统一由 edge_flow_ids 生成
    edges = [e for e in edges if 'id="Flow_1_di"' not in e]

    return f'''<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:flowable="http://flowable.org/bpmn"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  id="Definitions_1"
  targetNamespace="http://bpmn.io/schema/bpmn">
  <process id="{_xml_escape(process_id)}" name="{_xml_escape(process_name)}" isExecutable="true">
    <startEvent id="{_xml_escape(start_id)}" name="开始" />
{chr(10).join(service_tasks)}
{chr(10).join(gateway_elements)}
    <endEvent id="{_xml_escape(end_id)}" name="结束" />
{chr(10).join(sequence_flows)}
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="{_xml_escape(process_id)}">
{chr(10).join(shapes)}
{chr(10).join(edges)}
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>'''


@tool
def list_flow_definitions(
    pageNum: int = 1,
    pageSize: int = 10,
    name: Optional[str] = None,
    applicationId: Optional[str] = None,
) -> dict[str, Any]:
    """分页查询流程定义列表，支持按名称和应用ID过滤。

    返回列表中的流程包含 id、name、status 等字段，status=2 表示已发布可执行。
    """
    params = _prepare_query(
        {
            "pageNum": pageNum,
            "pageSize": pageSize,
            "name": name,
            "applicationId": applicationId,
        }
    )
    return client.request("GET", "/api-flow-definition/list", params=params)


@tool
def get_flow_definition_detail(flowDefinitionId: str) -> dict[str, Any]:
    """根据流程定义ID查询详情，包含 BPMN XML、节点配置、最近执行记录等。"""
    return client.request("GET", f"/api-flow-definition/{flowDefinitionId}")


@tool
def deploy_flow_definition(
    name: str,
    applicationId: str,
    bpmnXml: Optional[str] = None,
    flowDefinitionId: Optional[str] = None,
    businessDomainId: Optional[Any] = None,
    ownerId: Optional[int] = None,
    nodes: Optional[Union[list[dict[str, Any]], str]] = None,
) -> dict[str, Any]:
    """部署流程定义到 Flowable 引擎，使其变为可执行状态（status=2）。

    支持两种场景：
    1. 新建部署：提供 name、applicationId、bpmnXml，不填 flowDefinitionId。
    2. 升级部署：提供已有 flowDefinitionId，bpmnXml 和 nodes 可省略，
       工具会自动查询流程详情补全。

    若流程包含 serviceTask 节点，应在 nodes 中传入节点绑定配置；
    否则引擎执行时可能找不到关联原服务。
    """
    # LLM 可能把 nodes 序列化成 JSON 字符串传入，需要反序列化
    if isinstance(nodes, str):
        try:
            nodes = json.loads(nodes)
        except Exception as exc:
            return {
                "code": 400,
                "message": f"nodes 参数不是合法的 JSON 数组：{exc}",
                "data": None,
            }

    # 统一规范化节点格式，避免 LLM 使用 nodeId/serviceId 等别名导致 XML 与 nodes 不匹配
    if isinstance(nodes, list):
        nodes = [_normalize_deploy_node(n) for n in nodes]

    if not bpmnXml and not flowDefinitionId:
        if not nodes:
            return {
                "code": 400,
                "message": "新建部署时必须提供 bpmnXml 或 nodes，工具可根据 nodes 自动生成 BPMN XML",
                "data": None,
            }
        bpmnXml = _build_bpmn_xml(name, nodes, application_id=applicationId)
    elif bpmnXml and nodes and not _is_bpmn_xml_complete(bpmnXml):
        # LLM 可能生成缺少 BPMN DI 等必要结构的 XML，优先使用工具生成的完整 XML
        bpmnXml = _build_bpmn_xml(name, nodes, application_id=applicationId)

    if flowDefinitionId is not None and not bpmnXml:
        detail = client.request("GET", f"/api-flow-definition/{flowDefinitionId}")
        bpmnXml = (
            detail.get("data", {}).get("bpmnXml")
            or detail.get("data", {}).get("definitionXml")
            or ""
        )
        if not nodes:
            nodes = detail.get("data", {}).get("nodes")
            if isinstance(nodes, list):
                nodes = [_normalize_deploy_node(n) for n in nodes]
        if not bpmnXml:
            return {
                "code": 400,
                "message": f"无法获取流程 {flowDefinitionId} 的 BPMN XML，请手动提供 bpmnXml",
                "data": None,
            }

    payload: dict[str, Any] = {
        "name": name,
        "applicationId": applicationId,
        "bpmnXml": bpmnXml,
    }
    if flowDefinitionId is not None:
        payload["id"] = flowDefinitionId
    if businessDomainId is not None:
        payload["businessDomainId"] = businessDomainId
    if ownerId is not None:
        payload["ownerId"] = ownerId
    if nodes is not None:
        all_nodes = nodes if isinstance(nodes, list) else [_normalize_deploy_node(n) for n in nodes]
        # 后端 flow_node 表只保存 serviceTask 节点；网关/事件等节点由 BPMN XML 承载，
        # 传入后端会导致 nodeType 不兼容或保存为错误类型，因此过滤掉。
        payload["nodes"] = [n for n in all_nodes if not _node_type_is_gateway(n)]
    return client.request("POST", "/api-flow-definition/deploy", json=payload)


@tool
def execute_flow(
    flowDefinitionId: str,
    inputParams: Optional[Union[dict[str, Any], str]] = None,
    env: str = "default",
    executeMode: int = 1,
    timeout: Optional[int] = None,
) -> dict[str, Any]:
    """启动指定流程定义的执行，返回执行编号、流程实例ID和状态。

    executeMode 说明：
    - 1（默认）：同步执行，等待全部节点完成后返回最终结果。
    - 2：异步执行，接口立即返回，需要后续调用 get_flow_execution_result 轮询。

    当需要传入全局流程变量时，使用 inputParams。
    """
    # LLM 可能把对象序列化成 JSON 字符串传入，需要反序列化
    if isinstance(inputParams, str):
        try:
            inputParams = json.loads(inputParams)
        except Exception as exc:
            return {
                "code": 400,
                "message": f"inputParams 不是合法的 JSON 对象：{exc}",
                "data": None,
            }
    if isinstance(timeout, str):
        try:
            timeout = int(timeout)
        except Exception:
            timeout = None

    payload: dict[str, Any] = {
        "flowDefinitionId": flowDefinitionId,
        "env": env,
        "executeMode": executeMode,
    }
    if inputParams is not None:
        payload["inputParams"] = inputParams
    if timeout is not None:
        payload["timeout"] = timeout
    return client.request(
        "POST", f"/api-flow-execution/{flowDefinitionId}/execute", json=payload
    )


@tool
def get_flow_execution_result(processInstanceId: str) -> dict[str, Any]:
    """根据流程实例ID查询执行结果，包含节点轨迹、网关评估、输出参数、错误详情等。"""
    return client.request("GET", f"/api-flow-execution/execution/{processInstanceId}")


@tool
def get_flow_execution_history(
    pageNum: int = 1,
    pageSize: int = 20,
    flowDefinitionId: Optional[str] = None,
    status: Optional[int] = None,
) -> dict[str, Any]:
    """分页查询流程执行历史记录。

    status 取值：0=执行中，1=成功，2=失败，3=部分成功。
    """
    params = _prepare_query(
        {
            "pageNum": pageNum,
            "pageSize": pageSize,
            "flowDefinitionId": flowDefinitionId,
            "status": status,
        }
    )
    return client.request("GET", "/api-flow-execution/history", params=params)
