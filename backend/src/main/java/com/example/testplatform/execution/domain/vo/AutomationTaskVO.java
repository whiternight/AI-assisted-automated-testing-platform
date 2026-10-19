package com.example.testplatform.execution.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动化测试任务详情 VO
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(name = "AutomationTaskVO", description = "自动化测试任务详情")
public class AutomationTaskVO {

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "任务流水号")
    private String taskNo;

    @Schema(description = "应用名称")
    private String applicationName;

    @Schema(description = "用例集名称")
    private String testSuiteName;

    @Schema(description = "执行人ID")
    private Long executorId;

    @Schema(description = "执行人姓名")
    private String executorName;

    @Schema(description = "任务状态（0=待执行，1=执行中，2=成功，3=部分成功，4=失败）")
    private Integer status;

    @Schema(description = "任务状态描述")
    private String statusDesc;

    @Schema(description = "总用例数")
    private Integer totalCases;

    @Schema(description = "成功数")
    private Integer successCount;

    @Schema(description = "失败数")
    private Integer failCount;

    @Schema(description = "通过率（百分比）")
    private Double passRate;

    @Schema(description = "当前重试轮次")
    private Integer retryRound;

    @Schema(description = "执行环境")
    private String env;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "每个用例的执行结果")
    private List<NodeResultVO> nodeResults;
}
