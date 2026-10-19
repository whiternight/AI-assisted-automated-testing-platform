package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程执行简要记录 VO
 * <p>用于流程定义详情中展示最近执行记录</p>
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "流程执行简要记录")
public class FlowExecutionBriefVO {

    @Schema(description = "执行记录 ID")
    private String id;

    @Schema(description = "Flowable 流程实例 ID")
    private String procInstId;

    @Schema(description = "执行状态：0=执行中 1=成功 2=失败 3=部分成功")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "执行耗时（毫秒）")
    private Long duration;
}
