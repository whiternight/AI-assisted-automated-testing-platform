package com.example.testplatform.workflow.service;

import com.example.testplatform.enums.FlowNodeTypeEnum;
import org.springframework.stereotype.Component;

/**
 * BPMN 节点类型映射器
 * <p>将 Flowable BPMN 元素类型映射为业务节点类型枚举</p>
 *
 * @author TestPlatform
 */
@Component
public class BpmnNodeTypeMapper {

    /**
     * 根据 BPMN 元素类型获取业务节点类型
     *
     * @param bpmnElementType BPMN 元素类型名称
     * @return 对应的业务节点类型枚举
     */
    public FlowNodeTypeEnum map(String bpmnElementType) {
        if (bpmnElementType == null) {
            return FlowNodeTypeEnum.UNKNOWN;
        }
        return switch (bpmnElementType) {
            case "startEvent" -> FlowNodeTypeEnum.START;
            case "endEvent" -> FlowNodeTypeEnum.END;
            case "serviceTask" -> FlowNodeTypeEnum.ORIGINAL_SERVICE;
            case "exclusiveGateway" -> FlowNodeTypeEnum.CONDITION_GATEWAY;
            case "parallelGateway" -> FlowNodeTypeEnum.PARALLEL_GATEWAY;
            case "inclusiveGateway" -> FlowNodeTypeEnum.INCLUSIVE_GATEWAY;
            case "eventBasedGateway" -> FlowNodeTypeEnum.EVENT_GATEWAY;
            case "subProcess" -> FlowNodeTypeEnum.SUB_PROCESS;
            case "callActivity" -> FlowNodeTypeEnum.CALL_ACTIVITY;
            default -> FlowNodeTypeEnum.UNKNOWN;
        };
    }

    /**
     * 反向映射：业务节点类型 → BPMN 元素类型
     *
     * @param nodeType 业务节点类型枚举
     * @return BPMN 元素类型名称
     */
    public String reverse(FlowNodeTypeEnum nodeType) {
        return nodeType != null ? nodeType.getFlowableType() : "";
    }
}
