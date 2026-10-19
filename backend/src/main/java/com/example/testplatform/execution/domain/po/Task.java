package com.example.testplatform.execution.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.example.testplatform.enums.ExecuteModeEnum;
import com.example.testplatform.enums.TaskStatusEnum;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 任务表
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("task")
@Schema(name = "Task对象", description = "任务表")
public class Task implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，UUID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Schema(description = "任务流水号")
    private String taskNo;

    @Schema(description = "FK → test_suite(id)，单用例执行时为空")
    private String testSuiteId;

    @Schema(description = "用例集名称")
    private String testSuiteName;

    @Schema(description = "FK → application(id)")
    private String applicationId;

    @TableField(exist = false)
    @Schema(description = "应用名称")
    private String applicationName;

    @Schema(description = "执行人/触发人")
    private Long executorId;

    @Schema(description = "1=同步，2=异步")
    private Integer executeMode;

    /**
     * 执行模式枚举（业务层使用，不映射到数据库）
     */
    @TableField(exist = false)
    @Schema(description = "执行模式枚举")
    private ExecuteModeEnum executeModeEnum;

    @Schema(description = "0=待执行，1=执行中，2=成功，3=部分成功，4=失败，5=阻塞")
    private Integer status;

    /**
     * 任务状态枚举（业务层使用，不映射到数据库）
     */
    @TableField(exist = false)
    @Schema(description = "任务状态枚举")
    private TaskStatusEnum statusEnum;

    @Schema(description = "实际执行到的用例总数（分母）")
    private Integer totalCases;

    @Schema(description = "成功用例调用次数（分子）")
    private Integer successCount;

    @Schema(description = "失败次数")
    private Integer failCount;

    @Schema(description = "阻塞次数")
    private Integer blockedCount;

    @Schema(description = "跳过次数")
    private Integer skipCount;

    @Schema(description = "通过率（百分比，如 85.50 表示 85.5%）")
    private Double passRate;

    @Schema(description = "当前重试轮次（0=首轮执行）")
    private Integer retryRound;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "执行环境")
    private String env;

    @Schema(description = "创建时间，毫秒精度")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间，毫秒精度")
    private LocalDateTime updatedAt;

    @Schema(description = "逻辑删除：0=正常，1=已删除")
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;


}
