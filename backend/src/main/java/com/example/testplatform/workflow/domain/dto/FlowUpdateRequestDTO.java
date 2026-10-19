package com.example.testplatform.workflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 流程更新请求 DTO
 * <p>
 * 支持对已部署的流程定义进行编辑保存。
 * <ul>
 *   <li>当 edited=true 时，表示用户进行了实质性编辑，版本号自动 +1</li>
 *   <li>当 edited=false 或 null 时，表示仅保存（无实质变更），版本号保持不变</li>
 * </ul>
 * </p>
 *
 * @author TestPlatform
 */
@Data
@Schema(description = "流程更新请求")
public class FlowUpdateRequestDTO {

    @NotBlank(message = "流程定义 ID 不能为空")
    @Schema(description = "流程定义 ID（UUID）")
    private String id;

    @Schema(description = "流程名称（可选，不传则不修改）")
    private String name;

    @Schema(description = "所属应用 ID（可选，不传则不修改）")
    private String applicationId;

    @Schema(description = "业务域 ID（可选，不传则不修改）")
    private String businessDomainId;

    @Schema(description = "负责人 ID（可选，不传则不修改）")
    private Long ownerId;

    @Schema(description = "BPMN XML（可选，传入后会重新部署到 Flowable 引擎）")
    private String bpmnXml;

    @Valid
    @Schema(description = "节点配置列表（可选，传入后会先删后插重建节点）")
    private List<FlowNodeDeployDTO> nodes;

    @Schema(description = "是否进行了实质性编辑。true=版本号+1，false/null=仅保存，版本号不变")
    private Boolean edited;
}
