package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程执行结果 VO
 *
 * @author TestPlatform
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "流程执行结果")
public class FlowExecuteResultVO {

    @Schema(description = "Flowable 流程实例 ID")
    private String processInstanceId;

    @Schema(description = "流程定义 ID")
    private String flowDefinitionId;

    @Schema(description = "流程执行状态：0=执行中 1=成功 2=失败 3=部分成功")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "总执行耗时（毫秒）")
    private Long totalDuration;

    @Schema(description = "节点执行轨迹列表")
    private List<NodeExecutionTraceVO> nodeTraces;

    @Schema(description = "网关评估记录列表（实时或事后采集）")
    private List<GatewayEvaluationVO> gatewayEvaluations;

    @Schema(description = "流程输出参数")
    private Map<String, Object> outputParams;

    @Schema(description = "错误详情（失败时）")
    private FlowErrorDetailVO errorDetail;

    @Schema(description = "已触发的连线ID列表，用于前端动态显示执行路径动画")
    private List<String> activeSequenceFlows;
}

