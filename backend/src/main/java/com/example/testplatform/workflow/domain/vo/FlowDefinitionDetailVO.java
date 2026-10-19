package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程定义详情 VO
 * <p>包含基础信息、节点列表、连线列表、最近执行记录</p>
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "流程定义详情")
public class FlowDefinitionDetailVO {

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

    @Schema(description = "Flowable 流程定义 ID")
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

    @Schema(description = "BPMN XML 内容")
    private String definitionXml;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "节点列表")
    private List<BpmnNodeInfoVO> nodes;

    @Schema(description = "连线列表")
    private List<BpmnSequenceFlowInfoVO> sequences;

    @Schema(description = "最近执行记录（最多5条）")
    private List<FlowExecutionBriefVO> recentExecutions;

    @Schema(description = "最近一次失败的执行详情（无失败时为 null）")
    private FlowExecuteResultVO lastFailedExecution;
}
