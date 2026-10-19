package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程执行历史记录列表项 VO
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "流程执行历史记录")
public class FlowExecutionHistoryVO {

    @Schema(description = "执行记录 ID")
    private String id;

    @Schema(description = "Flowable 流程实例 ID")
    private String procInstId;

    @Schema(description = "流程定义 ID")
    private String flowDefinitionId;

    @Schema(description = "流程名称")
    private String flowName;

    @Schema(description = "执行状态：0=执行中 1=成功 2=失败 3=部分成功")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "执行耗时（毫秒）")
    private Long duration;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "执行人名称")
    private String executor;
}
