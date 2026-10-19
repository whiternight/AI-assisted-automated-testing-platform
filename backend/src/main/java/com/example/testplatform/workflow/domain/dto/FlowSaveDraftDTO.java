package com.example.testplatform.workflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 流程草稿保存请求 DTO
 * <p>仅保存基础信息，不包含 BPMN XML 和节点配置</p>
 *
 * @author TestPlatform
 */
@Data
@Schema(description = "流程草稿保存请求")
public class FlowSaveDraftDTO {

    @Schema(description = "流程定义 ID（可空，传入则更新已有草稿，不传则新建）")
    private String id;

    @NotBlank(message = "流程名称不能为空")
    @Schema(description = "流程名称")
    private String name;

    @NotNull(message = "所属应用不能为空")
    @Schema(description = "所属应用 ID（UUID）")
    private String applicationId;

    @Schema(description = "业务域 ID（UUID）")
    private String businessDomainId;
}
