package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 流程节点类型枚举
 *
 * @author TestPlatform
 */
@Getter
public enum FlowNodeTypeEnum {
    /**
     * 开始节点
     */
    START(1, "开始节点", "startEvent"),

    /**
     * 结束节点
     */
    END(2, "结束节点", "endEvent"),

    /**
     * 原服务节点（HTTP/Dubbo 等 serviceTask）
     */
    ORIGINAL_SERVICE(3, "原服务节点", "serviceTask"),

    /**
     * 排他网关（条件分支）
     */
    CONDITION_GATEWAY(4, "排他网关", "exclusiveGateway"),

    /**
     * 并行网关
     */
    PARALLEL_GATEWAY(5, "并行网关", "parallelGateway"),

    /**
     * 包容网关
     */
    INCLUSIVE_GATEWAY(6, "包容网关", "inclusiveGateway"),

    /**
     * 事件网关
     */
    EVENT_GATEWAY(7, "事件网关", "eventBasedGateway"),

    /**
     * 子流程
     */
    SUB_PROCESS(8, "子流程", "subProcess"),

    /**
     * 调用活动
     */
    CALL_ACTIVITY(9, "调用活动", "callActivity"),

    /**
     * 未知类型
     */
    UNKNOWN(-1, "未知类型", "");

    /**
     * 节点类型编码
     */
    private final int code;

    /**
     * 节点类型描述
     */
    private final String desc;

    /**
     * 对应 Flowable BPMN 元素类型
     */
    private final String flowableType;

    /**
     * 构造函数
     *
     * @param code         节点类型编码
     * @param desc         节点类型描述
     * @param flowableType Flowable BPMN 元素类型
     */
    FlowNodeTypeEnum(int code, String desc, String flowableType) {
        this.code = code;
        this.desc = desc;
        this.flowableType = flowableType;
    }

    /**
     * 根据编码获取流程节点类型枚举
     *
     * @param code 节点类型编码
     * @return 对应的流程节点类型枚举，未找到返回null
     */
    public static FlowNodeTypeEnum getByCode(int code) {
        for (FlowNodeTypeEnum type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return null;
    }

}
