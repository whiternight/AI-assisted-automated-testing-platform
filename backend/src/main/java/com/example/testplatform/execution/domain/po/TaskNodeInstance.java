package com.example.testplatform.execution.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.example.testplatform.enums.ErrorTypeEnum;
import com.example.testplatform.enums.NodeInstanceStatusEnum;

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
 * 任务节点实例表
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("task_node_instance")
@Schema(name = "TaskNodeInstance对象", description = "任务节点实例表")
public class TaskNodeInstance implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，UUID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Schema(description = "FK → task(id)")
    private String taskId;

    @Schema(description = "FK → flow_node(id)")
    private String flowNodeId;

    @Schema(description = "FK → api_case(id)")
    private String caseId;

    @Schema(description = "0=PENDING，1=RUNNING，2=COMPLETED，3=FAILED，4=TIMEOUT，5=SKIPPED，6=BLOCKED")
    private Integer status;

    /**
     * 节点实例状态枚举（业务层使用，不映射到数据库）
     */
    @TableField(exist = false)
    @Schema(description = "节点实例状态枚举")
    private NodeInstanceStatusEnum statusEnum;

    @Schema(description = "实际入参快照（AES-256 加密）")
    private String inputSnapshot;

    @Schema(description = "实际出参快照（AES-256 加密）")
    private String outputSnapshot;

    @Schema(description = "1=ASSERTION_FAIL，2=ENV_ERROR，3=PLATFORM_ERROR")
    private Integer errorType;

    /**
     * 错误类型枚举（业务层使用，不映射到数据库）
     */
    @TableField(exist = false)
    @Schema(description = "错误类型枚举")
    private ErrorTypeEnum errorTypeEnum;

    @Schema(description = "错误信息摘要")
    private String errorMsg;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @Schema(description = "是否通过（用于通过率计算）：0=未通过（非2xx），1=已通过（2xx）")
    private Integer passed;

    @Schema(description = "创建时间，毫秒精度")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间，毫秒精度")
    private LocalDateTime updatedAt;

    @Schema(description = "逻辑删除：0=正常，1=已删除")
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;


}
