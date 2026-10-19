package com.example.testplatform.workflow.engine.trace;

import com.example.testplatform.workflow.domain.vo.FlowErrorDetailVO;
import com.example.testplatform.workflow.domain.vo.NodeExecutionTraceVO;
import com.example.testplatform.workflow.engine.gateway.GatewayEvaluationRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程失败定位器
 * <p>
 * 基于 ExecutionTraceCollector 收集的节点轨迹，
 * 找到第一个未完成的节点并从 execution 变量中获取错误详情。
 * </p>
 *
 * @author TestPlatform
 */
@Slf4j
@Component
public class FlowErrorLocator {

    @Autowired
    private ExecutionTraceCollector traceCollector;

    @Autowired
    private GatewayEvaluationRecorder gatewayEvaluationRecorder;

    /**
     * 定位流程失败原因
     *
     * @param processInstanceId 流程实例 ID
     * @return 失败定位详情，如果无法定位返回 null
     */
    public FlowErrorDetailVO locate(String processInstanceId) {
        log.info("开始定位流程失败 - processInstanceId={}", processInstanceId);

        List<NodeExecutionTraceVO> traces = traceCollector.collectNodeTraces(processInstanceId);

        // 找到第一个 FAILED 状态的节点
        NodeExecutionTraceVO failedNode = traces.stream()
                .filter(t -> "FAILED".equals(t.getStatus()))
                .findFirst()
                .orElse(null);

        if (failedNode == null) {
            // 尝试找 ACTIVE（未完成）的节点
            failedNode = traces.stream()
                    .filter(t -> "ACTIVE".equals(t.getStatus()))
                    .findFirst()
                    .orElse(null);
        }

        // 检测排他/包容网关无匹配分支导致的失败
        String failedSequenceFlowId = null;
        if (failedNode == null) {
            // 所有节点均 COMPLETED 但流程仍失败，可能是网关无匹配
            String noMatchGatewayId = gatewayEvaluationRecorder.detectNoMatchGatewayFailure(processInstanceId);
            if (noMatchGatewayId != null) {
                failedSequenceFlowId = noMatchGatewayId;
                log.info("流程失败原因：排他/包容网关无匹配分支 - gatewayId={}", noMatchGatewayId);
            }
        }

        if (failedNode == null) {
            // 无节点级失败，但可能是网关无匹配
            if (failedSequenceFlowId != null) {
                return FlowErrorDetailVO.builder()
                        .failedActivityId(failedSequenceFlowId)
                        .failedActivityName("网关无匹配分支")
                        .failedActivityType("exclusiveGateway")
                        .errorType("PLATFORM_ERROR")
                        .errorMessage("排他/包容网关的所有条件均不匹配，且未配置默认分支")
                        .failedSequenceFlowId(failedSequenceFlowId)
                        .failedAt(LocalDateTime.now())
                        .build();
            }
            log.warn("未找到失败或未完成节点 - processInstanceId={}", processInstanceId);
            return null;
        }

        FlowErrorDetailVO errorDetail = FlowErrorDetailVO.builder()
                .failedActivityId(failedNode.getActivityId())
                .failedActivityName(failedNode.getActivityName())
                .failedActivityType(failedNode.getActivityType())
                .failedNodeType(failedNode.getNodeType())
                .errorType(failedNode.getErrorType())
                .errorMessage(failedNode.getErrorMsg())
                .failedAt(failedNode.getEndTime() != null ? failedNode.getEndTime() : LocalDateTime.now())
                .build();

        log.info("流程失败定位完成 - processInstanceId={}, failedActivity={}",
                processInstanceId, failedNode.getActivityId());
        return errorDetail;
    }
}
