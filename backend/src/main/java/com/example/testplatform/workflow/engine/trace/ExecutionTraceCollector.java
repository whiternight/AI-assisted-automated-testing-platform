package com.example.testplatform.workflow.engine.trace;

import com.example.testplatform.common.util.AesEncryptUtil;
import com.example.testplatform.enums.FlowNodeTypeEnum;
import com.example.testplatform.workflow.domain.vo.GatewayEvaluationVO;
import com.example.testplatform.workflow.domain.vo.NodeExecutionTraceVO;
import com.example.testplatform.workflow.service.BpmnModelParser;
import com.example.testplatform.workflow.service.BpmnNodeTypeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 执行轨迹收集器
 * <p>
 * 通过 Flowable HistoryService 查询 HistoricActivityInstance，
 * 将节点/连线执行记录转换为 NodeExecutionTraceVO 列表。
 * 不维护内存状态，每次查询实时从 Flowable 历史表获取。
 * </p>
 *
 * @author TestPlatform
 */
@Slf4j
@Component
public class ExecutionTraceCollector {

    @Autowired
    private HistoryService historyService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private ManagementService managementService;

    @Resource
    private BpmnNodeTypeMapper bpmnNodeTypeMapper;

    @Resource
    private BpmnModelParser bpmnModelParser;

    @Resource
    private AesEncryptUtil aesEncryptUtil;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 收集指定流程实例的所有节点执行轨迹
     *
     * @param processInstanceId Flowable 流程实例 ID
     * @return 节点轨迹列表（按开始时间排序）
     */
    public List<NodeExecutionTraceVO> collectNodeTraces(String processInstanceId) {
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        // 查询运行时活动节点ID
        Set<String> activeActivityIds = new HashSet<>();
        try {
            activeActivityIds = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .list()
                    .stream()
                    .map(e -> e.getActivityId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("查询运行时活动节点失败: processInstanceId={}", processInstanceId, e);
        }

        // 查询异步作业，检查是否有失败的作业
        Set<String> failedJobNodeIds = new HashSet<>();
        try {
            managementService.createJobQuery()
                    .processInstanceId(processInstanceId)
                    .withException()
                    .list()
                    .forEach(job -> {
                        if (job.getExecutionId() != null) {
                            try {
                                String activityId = runtimeService.createExecutionQuery()
                                        .executionId(job.getExecutionId())
                                        .singleResult()
                                        .getActivityId();
                                if (activityId != null) {
                                    failedJobNodeIds.add(activityId);
                                    String errorMsg = job.getExceptionMessage();
                                    runtimeService.setVariable(processInstanceId,
                                            "node_" + activityId + "_errorType", "ENV_ERROR");
                                    runtimeService.setVariable(processInstanceId,
                                            "node_" + activityId + "_errorMsg",
                                            errorMsg != null ? errorMsg : "异步任务执行异常");
                                }
                            } catch (Exception ignored) {}
                        }
                    });
        } catch (Exception e) {
            log.warn("查询失败作业失败: processInstanceId={}", processInstanceId, e);
        }

        // 查询流程变量（用于获取 originalServiceId、input/output snapshot）
        Map<String, Object> variables = queryProcessVariables(processInstanceId);

        // 检查是否有节点失败标志（OriginalServiceDelegate 不抛异常而是设置此变量）
        String flowFailedNode = getStringVariable(variables, "_flow_failed_node");

        // 统一查询 HistoricProcessInstance，避免 RuntimeService/HistoryService 跨服务查询竞态
        // getEndTime() == null → 流程仍在运行；getDeleteReason() != null → 流程异常终止
        HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        boolean processRunning = historicProcess != null && historicProcess.getEndTime() == null;
        // processFailed = Flowable 异常终止 OR 节点委托设置了失败标志
        boolean processFailed = (historicProcess != null && historicProcess.getEndTime() != null
                && historicProcess.getDeleteReason() != null) || flowFailedNode != null;

        Set<String> tracedIds = new HashSet<>();
        List<NodeExecutionTraceVO> traces = new ArrayList<>();
        for (HistoricActivityInstance activity : activities) {
            String activityType = activity.getActivityType();

            // 跳过 sequenceFlow（连线不是节点）
            if ("sequenceFlow".equals(activityType)) {
                continue;
            }

            FlowNodeTypeEnum nodeType = bpmnNodeTypeMapper.map(activityType);

            // 计算时间
            LocalDateTime startTime = toDate(activity.getStartTime());
            LocalDateTime endTime = toDate(activity.getEndTime());
            Long duration = activity.getDurationInMillis();

            // 判断状态
            String status;
            String activityId = activity.getActivityId();
            tracedIds.add(activityId);

            if (activity.getEndTime() != null) {
                status = "COMPLETED";
            } else {
                status = "EXECUTING";
            }

            // 如果流程失败，已开始但未结束的节点应标记为 FAILED（而非 EXECUTING）
            if (processFailed && "EXECUTING".equals(status)) {
                status = "FAILED";
            }

            // 如果节点在失败作业列表中，标记为FAILED
            if (failedJobNodeIds.contains(activityId)) {
                status = "FAILED";
            }

            // 从流程变量获取节点相关数据
            String originalServiceId = getStringVariable(variables, "node_" + activityId + "_originalServiceId");
            String inputSnapshot = getEncryptedVariable(variables, "node_" + activityId + "_input");
            String outputSnapshot = getEncryptedVariable(variables, "node_" + activityId + "_output");
            String errorType = getStringVariable(variables, "node_" + activityId + "_errorType");
            String errorMsg = getStringVariable(variables, "node_" + activityId + "_errorMsg");

            // 如果节点有错误，状态改为 FAILED
            if (errorType != null || errorMsg != null) {
                status = "FAILED";
            }

            // 如果节点被跳过（前置节点失败），标记为 NOT_EXECUTED
            String skippedFlag = getStringVariable(variables, "node_" + activityId + "_skipped");
            if ("true".equals(skippedFlag)) {
                status = "NOT_EXECUTED";
            }

            // 如果流程失败，EndEvent 不应显示为 COMPLETED
            if (processFailed && "endEvent".equals(activityType) && "COMPLETED".equals(status)) {
                status = "FAILED";
            }

            traces.add(NodeExecutionTraceVO.builder()
                    .activityId(activityId)
                    .activityName(activity.getActivityName())
                    .activityType(activityType)
                    .nodeType(nodeType)
                    .status(status)
                    .originalServiceId(originalServiceId)
                    .duration(duration)
                    .inputSnapshot(inputSnapshot)
                    .outputSnapshot(outputSnapshot)
                    .errorType(errorType)
                    .errorMsg(errorMsg)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build());
        }

        // 添加运行时活动但历史表中还没有记录的节点（异步任务等待执行或刚被拾取的情况）
        for (String activityId : activeActivityIds) {
            if (!tracedIds.contains(activityId)) {
                // 查找节点信息
                try {
                    BpmnModel bpmnModel = bpmnModelParser.getBpmnModel(historicProcess.getProcessDefinitionId());
                    if (bpmnModel != null) {
                        for (Process process : bpmnModel.getProcesses()) {
                            FlowElement element = process.getFlowElement(activityId);
                            if (element instanceof FlowNode && !(element instanceof SequenceFlow)) {
                                String nodeName = element.getName();
                                String activityType = element.getClass().getSimpleName();
                                FlowNodeTypeEnum nodeType = bpmnNodeTypeMapper.map(activityType);

                                String errorType = getStringVariable(variables, "node_" + activityId + "_errorType");
                                String errorMsg = getStringVariable(variables, "node_" + activityId + "_errorMsg");
                                String originalServiceId = getStringVariable(variables, "node_" + activityId + "_originalServiceId");
                                String inputSnapshot = getEncryptedVariable(variables, "node_" + activityId + "_input");

                                String status = "EXECUTING";
                                if (failedJobNodeIds.contains(activityId) || errorType != null || errorMsg != null) {
                                    status = "FAILED";
                                }

                                traces.add(NodeExecutionTraceVO.builder()
                                        .activityId(activityId)
                                        .activityName(nodeName)
                                        .activityType(activityType)
                                        .nodeType(nodeType)
                                        .status(status)
                                        .originalServiceId(originalServiceId)
                                        .inputSnapshot(inputSnapshot)
                                        .errorType(errorType)
                                        .errorMsg(errorMsg)
                                        .startTime(LocalDateTime.now())
                                        .build());
                                tracedIds.add(activityId);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("添加运行时活动节点失败: activityId={}", activityId, e);
                }
            }
        }

        // 补充未执行的节点：运行中为 PENDING，成功时为 SKIPPED，失败时为 NOT_EXECUTED
        appendSkippedNodes(traces, tracedIds, processFailed, processRunning, historicProcess);

        return traces;
    }

    /**
     * 补充未执行的节点状态
     * <p>从 BpmnModel 获取所有节点，对比已有轨迹：</p>
     * <ul>
     *   <li>流程运行中时，未执行节点标记为 PENDING（等待执行）</li>
     *   <li>流程正常结束时，未执行节点标记为 SKIPPED（如未命中的分支）</li>
     *   <li>流程失败时，未执行节点标记为 NOT_EXECUTED，避免与真实执行过的节点混淆</li>
     * </ul>
     */
    private void appendSkippedNodes(List<NodeExecutionTraceVO> traces,
                                    Set<String> tracedIds,
                                    boolean processFailed, boolean processRunning,
                                    HistoricProcessInstance historicProcess) {
        try {
            if (historicProcess == null || historicProcess.getProcessDefinitionId() == null) {
                return;
            }

            BpmnModel bpmnModel = bpmnModelParser.getBpmnModel(historicProcess.getProcessDefinitionId());
            if (bpmnModel == null) {
                return;
            }

            // 根据流程状态决定未执行节点的状态
            // 运行中 → PENDING；失败 → NOT_EXECUTED；成功 → SKIPPED
            String skippedStatus;
            if (processRunning) {
                skippedStatus = "PENDING";
            } else if (processFailed) {
                skippedStatus = "NOT_EXECUTED";
            } else {
                skippedStatus = "SKIPPED";
            }

            // 遍历 BPMN 所有节点，找出未执行的
            for (Process process : bpmnModel.getProcesses()) {
                for (FlowElement element : process.getFlowElements()) {
                    if (element instanceof FlowNode && !(element instanceof SequenceFlow)) {
                        String activityId = element.getId();
                        if (!tracedIds.contains(activityId)) {
                            FlowNodeTypeEnum nodeType = bpmnNodeTypeMapper.map(element.getClass().getSimpleName());
                            traces.add(NodeExecutionTraceVO.builder()
                                    .activityId(activityId)
                                    .activityName(element.getName())
                                    .activityType(element.getClass().getSimpleName())
                                    .nodeType(nodeType)
                                    .status(skippedStatus)
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("补充未执行节点失败", e);
        }
    }

    /**
     * 查询流程变量
     * <p>运行时变量（流程未结束）使用 RuntimeService 实时查询；结束后再走历史表</p>
     */
    private Map<String, Object> queryProcessVariables(String processInstanceId) {
        Map<String, Object> result = new HashMap<>();
        // 1) 运行时变量：流程仍在执行时，必须从这里查（HistoricVariableInstance 只有结束后才有数据）
        try {
            Map<String, Object> runtimeVars = runtimeService.getVariables(processInstanceId);
            if (runtimeVars != null) {
                result.putAll(runtimeVars);
            }
        } catch (Exception e) {
            log.warn("查询运行时变量失败: processInstanceId={}", processInstanceId, e);
        }
        // 2) 历史变量：流程结束后从这里查，覆盖运行时变量（以最终值为准）
        try {
            List<HistoricVariableInstance> historicVars = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .list();
            for (HistoricVariableInstance v : historicVars) {
                result.put(v.getVariableName(), v.getValue() != null ? v.getValue() : "");
            }
        } catch (Exception e) {
            log.warn("查询历史变量失败: processInstanceId={}", processInstanceId, e);
        }
        return result;
    }

    /**
     * 从变量中获取字符串值
     */
    private String getStringVariable(Map<String, Object> variables, String key) {
        Object value = variables.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 从变量中获取加密的快照值
     * <p>如果值已经是加密字符串（Base64 格式），直接返回；否则加密后返回</p>
     */
    private String getEncryptedVariable(Map<String, Object> variables, String key) {
        Object value = variables.get(key);
        if (value == null) {
            return null;
        }
        String plainText = value.toString();
        try {
            return aesEncryptUtil.encrypt(plainText);
        } catch (Exception e) {
            log.warn("AES 加密失败: key={}", key, e);
            return null;
        }
    }

    // ==================== 实时记录方法（监听器调用） ====================

    /**
     * 记录网关评估结果
     * <p>将评估结果存入流程变量，供后续 collectNodeTraces 查询</p>
     */
    public void recordGatewayEvaluation(GatewayEvaluationVO evaluation) {
        log.debug("记录网关评估: gatewayNodeKey={}, gatewayType={}, result={}",
                evaluation.getGatewayNodeKey(), evaluation.getGatewayType(), evaluation.getEvaluationResult());
    }

    /**
     * 记录节点开始
     * <p>将节点开始信息存入流程变量</p>
     */
    public void recordNodeStart(DelegateExecution execution, String nodeId, String nodeName) {
        log.debug("记录节点开始: procInstId={}, nodeId={}, nodeName={}",
                execution.getProcessInstanceId(), nodeId, nodeName);
        try {
            runtimeService.setVariable(execution.getProcessInstanceId(),
                    "node_" + nodeId + "_startTime", LocalDateTime.now().toString());
        } catch (Exception e) {
            log.warn("记录节点开始变量失败: nodeId={}", nodeId, e);
        }
    }

    /**
     * 记录节点结束
     * <p>将节点执行结果存入流程变量</p>
     */
    public void recordNodeEnd(DelegateExecution execution, String nodeId, Object result) {
        log.debug("记录节点结束: procInstId={}, nodeId={}, result={}",
                execution.getProcessInstanceId(), nodeId, result);
        try {
            String processInstanceId = execution.getProcessInstanceId();
            if (result != null) {
                String resultJson = objectMapper.writeValueAsString(result);
                runtimeService.setVariable(processInstanceId,
                        "node_" + nodeId + "_output", resultJson);
            }
            runtimeService.setVariable(processInstanceId,
                    "node_" + nodeId + "_endTime", LocalDateTime.now().toString());
        } catch (Exception e) {
            log.warn("记录节点结束变量失败: nodeId={}", nodeId, e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * Date → LocalDateTime 转换
     */
    private LocalDateTime toDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
