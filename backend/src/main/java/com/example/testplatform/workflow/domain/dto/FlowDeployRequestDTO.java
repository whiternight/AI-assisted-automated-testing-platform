package com.example.testplatform.workflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/**
 * 流程部署请求 DTO
 * <p>接收前端传入的 BPMN XML 和节点配置进行流程部署</p>
 *
 * @author TestPlatform
 */
@Data
@Schema(description = "流程部署请求")
public class FlowDeployRequestDTO {

    @Schema(description = "流程定义 ID（可空，传入则升级已有草稿/流程，不传则新建）")
    private String id;

    @Schema(description = "流程名称（新建时必填，升级时可选）")
    private String name;

    @Schema(description = "所属应用 ID（UUID，新建时必填，升级时可选）")
    private String applicationId;

    @Schema(description = "业务域 ID（UUID）")
    private String businessDomainId;

    @Schema(description = "创建人 ID（可选，不传则默认为当前登录用户）")
    private Long creatorId;

    @Schema(description = "负责人 ID（可选，不传则默认为当前登录用户）")
    private Long ownerId;

    @Schema(description = "BPMN XML 流程定义内容（升级时若为空则使用已保存的 XML）")
    private String bpmnXml;

    @Valid
    @Schema(description = "节点配置列表（serviceTask 节点的输入输出、重试等配置）")
    private List<FlowNodeDeployDTO> nodes;
}
