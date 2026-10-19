package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程部署响应 VO
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "流程部署响应")
public class FlowDeployResponseVO {

    @Schema(description = "流程定义 ID")
    private String flowDefinitionId;

    @Schema(description = "Flowable 部署 ID")
    private String deploymentId;

    @Schema(description = "Flowable 流程定义 ID")
    private String procDefId;

    @Schema(description = "流程名称")
    private String name;

    @Schema(description = "版本号（语义化版本，如 0.0.1）")
    private String version;

    @Schema(description = "流程状态：0=草稿 1=未部署 2=已发布 3=已停用")
    private Integer status;

    @Schema(description = "部署时间")
    private LocalDateTime deployedAt;
}
