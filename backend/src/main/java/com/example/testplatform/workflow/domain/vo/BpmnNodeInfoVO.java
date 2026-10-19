package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * BPMN 节点简要信息 VO
 * <p>从 Flowable FlowNode 映射，用于详情展示</p>
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "BPMN 节点信息")
public class BpmnNodeInfoVO {

    @Schema(description = "节点 ID（BPMN 元素 ID）")
    private String id;

    @Schema(description = "节点名称")
    private String name;

    @Schema(description = "节点类型（Flowable 原生值，如 serviceTask / exclusiveGateway）")
    private String type;

    @Schema(description = "关联原服务 ID（serviceTask 节点）")
    private String originalServiceId;

    @Schema(description = "入参配置 JSON")
    private String inputParams;

    @Schema(description = "出参配置 JSON")
    private String outputParams;

    @Schema(description = "断言配置 JSON")
    private String assertions;

    @Schema(description = "节点级超时（毫秒）")
    private Integer timeout;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "重试间隔（毫秒）")
    private Integer retryInterval;

    @Schema(description = "重试类型：1=固定间隔，2=指数退避")
    private Integer retryType;
}
