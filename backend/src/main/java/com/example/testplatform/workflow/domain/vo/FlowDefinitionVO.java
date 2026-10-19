package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程定义列表 VO
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "流程定义列表项")
public class FlowDefinitionVO {

    @Schema(description = "流程定义 ID（flow_definition 表主键）")
    private String id;

    @Schema(description = "流程名称")
    private String name;

    @Schema(description = "所属应用 ID")
    private String applicationId;

    @Schema(description = "所属应用名称")
    private String applicationName;

    @Schema(description = "所属业务域 ID")
    private String businessDomainId;

    @Schema(description = "Flowable 流程定义 ID（ACT_RE_PROCDEF.ID_）")
    private String procDefId;

    @Schema(description = "Flowable 部署 ID")
    private String deploymentId;

    @Schema(description = "流程版本号（语义化版本，如 0.0.1）")
    private String version;

    @Schema(description = "流程状态：0=草稿 1=未部署 2=已发布 3=已停用")
    private Integer status;

    @Schema(description = "创建人 ID")
    private Long creatorId;

    @Schema(description = "创建人姓名")
    private String creatorName;

    @Schema(description = "负责人 ID")
    private Long ownerId;

    @Schema(description = "负责人姓名")
    private String ownerName;

    @Schema(description = "节点数量（从 BpmnModel 统计）")
    private Integer nodeCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "部署时间")
    private LocalDateTime deployedAt;
}
