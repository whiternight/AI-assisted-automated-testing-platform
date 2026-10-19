package com.example.testplatform.workflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 节点配置 DTO（随部署请求一起提交）
 *
 * @author TestPlatform
 */
@Data
@Schema(description = "流程节点配置")
public class FlowNodeDeployDTO {

    @NotBlank(message = "节点标识不能为空")
    @Schema(description = "画布节点唯一标识（如 node_1），与 BPMN serviceTask id 对应")
    private String nodeKey;

    @Schema(description = "节点显示名称")
    private String nodeName;

    @Schema(description = "节点类型：3=原服务节点（HTTP/Dubbo 等 serviceTask）。仅 serviceTask 会存入 flow_node 表")
    private Integer nodeType;

    @Schema(description = "FK → original_service(id)")
    private String originalServiceId;

    @Schema(description = "入参配置 JSON（静态值+动态引用）")
    private String inputParams;

    @Schema(description = "出参配置 JSON（JSONPath 提取字段列表）")
    private String outputParams;

    @Schema(description = "节点级超时（毫秒），覆盖用例默认值")
    private Integer timeout;

    @Schema(description = "重试次数（0~5）")
    private Integer retryCount;

    @Schema(description = "重试间隔（毫秒）")
    private Integer retryInterval;

    @Schema(description = "1=固定间隔，2=指数退避")
    private Integer retryType;

    @Schema(description = "断言配置 JSON（覆盖原服务级断言）")
    private String assertions;
}
