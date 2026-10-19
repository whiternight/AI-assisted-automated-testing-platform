package com.example.testplatform.workflow.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

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
 * 流程执行实例表
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("flow_execution")
@Schema(name = "FlowExecution", description = "流程执行实例表")
public class FlowExecution implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，UUID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Schema(description = "FK → flow_definition(id)")
    private String flowDefinitionId;

    @Schema(description = "Flowable 流程实例ID")
    private String procInstId;

    @Schema(description = "0=执行中 1=成功 2=失败 3=部分成功")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "执行耗时（毫秒）")
    private Long duration;

    @Schema(description = "执行结果JSON")
    private String resultJson;

    @Schema(description = "执行入参JSON")
    private String inputParams;

    @Schema(description = "执行出参JSON")
    private String outputParams;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @TableLogic
    @Schema(description = "逻辑删除：0=正常，1=已删除")
    private Integer deleted;


}
