package com.example.testplatform.workflow.engine.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SequenceFlow 触发监听器
 * <p>
 * 在 BPMN XML 的 {@code <sequenceFlow>} 元素上注册 {@code take} 事件监听器，
 * 实时将连线触发信息写入流程变量，供 {@link GatewayEvaluationRecorder} 事后采集使用。
 * </p>
 * <p>
 * BPMN 配置示例：
 * <pre>{@code
 * <sequenceFlow id="flow_1" sourceRef="gateway_1" targetRef="node_2">
 *     <extensionElements>
 *         <flowable:executionListener
 *             event="take"
 *             delegateExpression="${sequenceFlowTakeListener}" />
 *     </extensionElements>
 *     <conditionExpression xsi:type="tFormalExpression">${amount > 100}</conditionExpression>
 * </sequenceFlow>
 * }</pre>
 * </p>
 * <p>
 * 流程变量格式（JSON 列表）：
 * <pre>{@code
 * _gateway_takenFlows = [
 *   { "flowId": "flow_1", "sourceRef": "gateway_1", "targetRef": "node_2", "timestamp": "..." },
 *   ...
 * ]
 * }</pre>
 * </p>
 *
 * @author TestPlatform
 */
@Slf4j
@Component("sequenceFlowTakeListener")
public class SequenceFlowTakeListener implements ExecutionListener {

    private static final long serialVersionUID = 1L;

    /**
     * 流程变量 key，存储已触发连线列表（JSON 格式）
     */
    public static final String VAR_TAKEN_FLOWS = "_gateway_takenFlows";

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void notify(DelegateExecution execution) {
        String flowId = execution.getCurrentActivityId();
        String processInstanceId = execution.getProcessInstanceId();

        log.debug("SequenceFlow 触发 - processInstanceId={}, flowId={}", processInstanceId, flowId);

        try {
            // 读取已有的 taken flows 列表
            List<Map<String, Object>> takenFlows = readTakenFlows(execution);

            // 构建本次触发记录
            Map<String, Object> flowRecord = new LinkedHashMap<>();
            flowRecord.put("flowId", flowId);
            flowRecord.put("processInstanceId", processInstanceId);
            flowRecord.put("timestamp", System.currentTimeMillis());

            // 尝试从 BpmnModel 获取 sourceRef / targetRef（Flowable 7.x 中可从 execution 获取）
            // 注意：DelegateExecution.getCurrentFlowElement() 返回 SequenceFlow 对象
            try {
                if (execution.getCurrentFlowElement() instanceof org.flowable.bpmn.model.SequenceFlow sf) {
                    flowRecord.put("sourceRef", sf.getSourceRef());
                    flowRecord.put("targetRef", sf.getTargetRef());
                    flowRecord.put("conditionExpression", sf.getConditionExpression());
                }
            } catch (Exception e) {
                log.debug("无法获取 SequenceFlow 元素详情: flowId={}", flowId, e);
            }

            takenFlows.add(flowRecord);

            // 写回流程变量（序列化为 JSON 字符串）
            String json = objectMapper.writeValueAsString(takenFlows);
            execution.setVariable(VAR_TAKEN_FLOWS, json);

            log.debug("连线触发记录已写入流程变量 - flowId={}, count={}", flowId, takenFlows.size());

        } catch (Exception e) {
            // 监听器不应中断流程执行，仅记录日志
            log.warn("SequenceFlow 触发记录失败（不影响流程）: flowId={}", flowId, e);
        }
    }

    /**
     * 从流程变量中读取已有的 taken flows 列表
     */
    private List<Map<String, Object>> readTakenFlows(DelegateExecution execution) {
        Object existing = execution.getVariable(VAR_TAKEN_FLOWS);
        if (existing instanceof String str && !str.isBlank()) {
            try {
                return objectMapper.readValue(str, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("解析 taken flows 变量失败，将重置为空列表: {}", e.getMessage());
            }
        }
        return new ArrayList<>();
    }
}
