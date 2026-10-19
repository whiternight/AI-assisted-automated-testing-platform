package com.example.testplatform.execution.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.testplatform.enums.LogTypeEnum;

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
 * 执行日志表
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("execution_log")
@Schema(name = "ExecutionLog对象", description = "执行日志表")
public class ExecutionLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，UUID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Schema(description = "FK → task(id)")
    private String taskId;

    @Schema(description = "FK → task_node_instance(id)")
    private String nodeInstanceId;

    @Schema(description = "1=请求日志，2=响应日志，3=错误日志，4=重试日志，5=参数替换日志")
    private Integer logType;

    /**
     * 日志类型枚举（业务层使用，不映射到数据库）
     */
    @TableField(exist = false)
    @Schema(description = "日志类型枚举")
    private LogTypeEnum logTypeEnum;

    @Schema(description = "日志摘要")
    private String content;

    @Schema(description = "详细内容（AES-256 加密）")
    private String detail;

    @Schema(description = "创建时间，毫秒精度")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间，毫秒精度")
    private LocalDateTime updatedAt;

    @TableLogic
    @Schema(description = "逻辑删除：0=正常，1=已删除")
    private Integer deleted;


}
