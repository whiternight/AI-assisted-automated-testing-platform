package com.example.testplatform.workflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 流程设计保存请求 DTO
 * <p>保存 BPMN XML 和 serviceTask 节点配置，状态为 1=未部署，不部署到 Flowable</p>
 *
 * @author TestPlatform
 */
@Data
@Schema(description = "流程设计保存请求（未部署）")
public class FlowSaveRequestDTO {

    @Schema(description = "流程定义 ID（可空，传入则更新已有未部署/草稿记录，不传则新建）")
    private String id;

    @NotBlank(message = "流程名称不能为空")
    @Schema(description = "流程名称")
    private String name;

    @NotNull(message = "所属应用不能为空")
    @Schema(description = "所属应用 ID（UUID）")
    private String applicationId;

    @Schema(description = "业务域 ID（UUID）")
    private String businessDomainId;

    @NotBlank(message = "BPMN XML 不能为空")
    @Schema(description = "BPMN XML 流程定义内容（由 bpmn-js 生成）")
    private String bpmnXml;

    @Schema(description = "serviceTask 节点配置列表（仅 serviceTask 会存入 flow_node 表）")
    private List<@Valid FlowNodeDeployDTO> nodes;
}
