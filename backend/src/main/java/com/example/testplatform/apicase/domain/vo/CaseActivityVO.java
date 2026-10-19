package com.example.testplatform.apicase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用例活跃度展示 VO
 *
 * @author TestPlatform
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CaseActivityVO", description = "用例活跃度展示对象")
public class CaseActivityVO {

    @Schema(description = "活跃度记录ID")
    private String id;

    @Schema(description = "用例ID")
    private String caseId;

    @Schema(description = "用例名称")
    private String caseName;

    @Schema(description = "所属应用ID")
    private String applicationId;

    @Schema(description = "所属应用名称")
    private String applicationName;

    @Schema(description = "最近单用例调试时间")
    private LocalDateTime lastDebugTime;

    @Schema(description = "最近流程引用执行时间")
    private LocalDateTime lastFlowExecuteTime;

    @Schema(description = "最近测试集执行时间")
    private LocalDateTime lastSuiteExecuteTime;

    @Schema(description = "最近手动查看时间")
    private LocalDateTime lastViewTime;

    @Schema(description = "综合最近活跃时间（四个时间维度取最大值）")
    private LocalDateTime lastActiveTime;

    @Schema(description = "是否活跃：true=活跃（7天内有调用），false=不活跃")
    private Boolean active;

    @Schema(description = "距上次活跃天数")
    private Long inactiveDays;

    @Schema(description = "最近一次计算时间")
    private LocalDateTime calculatedAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    // ====== 统计字段 ======

    @Schema(description = "总用例数")
    private Integer totalCount;

    @Schema(description = "活跃用例数")
    private Integer activeCount;

    @Schema(description = "不活跃用例数")
    private Integer inactiveCount;

    @Schema(description = "已逻辑删除用例数")
    private Integer deletedCount;
}
