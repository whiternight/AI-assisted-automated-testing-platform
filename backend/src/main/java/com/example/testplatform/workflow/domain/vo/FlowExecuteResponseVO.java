package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程执行启动响应 VO
 * <p>异步启动后立即返回的轻量响应</p>
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "流程执行启动响应")
public class FlowExecuteResponseVO {

    @Schema(description = "执行编号（TaskNoGenerator 生成）")
    private String taskNo;

    @Schema(description = "flow_execution 表 ID")
    private String executionId;

    @Schema(description = "Flowable 流程实例 ID")
    private String processInstanceId;

    @Schema(description = "执行状态：0=执行中 1=成功 2=失败 3=部分成功")
    private Integer status;

    @Schema(description = "启动时间")
    private LocalDateTime startTime;
}
