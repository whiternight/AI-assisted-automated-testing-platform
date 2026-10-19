package com.example.testplatform.apicase.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用例活跃度表
 * </p>
 *
 * @author author
 * @since 2026-06-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("case_activity")
public class CaseActivity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，UUID
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * FK → api_case(id)
     */
    @TableField("case_id")
    private String caseId;

    /**
     * 最近单用例调试时间
     */
    @TableField("last_debug_time")
    private LocalDateTime lastDebugTime;

    /**
     * 最近流程引用执行时间
     */
    @TableField("last_flow_execute_time")
    private LocalDateTime lastFlowExecuteTime;

    /**
     * 最近测试集执行时间
     */
    @TableField("last_suite_execute_time")
    private LocalDateTime lastSuiteExecuteTime;

    /**
     * 最近手动查看时间
     */
    @TableField("last_view_time")
    private LocalDateTime lastViewTime;

    /**
     * 最近一次计算时间（调度任务留痕）
     */
    @TableField("calculated_at")
    private LocalDateTime calculatedAt;

    /**
     * 创建时间，毫秒精度
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间，毫秒精度
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=正常，1=已删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;


}
