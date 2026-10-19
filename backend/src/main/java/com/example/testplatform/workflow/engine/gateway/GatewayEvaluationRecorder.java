package com.example.testplatform.workflow.engine.gateway;

import com.example.testplatform.workflow.domain.vo.GatewayEvaluationVO;
import com.example.testplatform.workflow.domain.vo.TakenFlowVO;
import com.example.testplatform.workflow.service.BpmnModelParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 网关评估记录器
 * <p>
 * 通过 HistoryService 查询已执行流程实例中的网关 activity 及 sequenceFlow 历史，
 * 结合 BpmnModel 解析每条连线的条件表达式与默认分支属性，
 * 构造 {@link GatewayEvaluationVO} 列表，供问题定位和实时仪表盘使用。
 * </p>
 * <p>
 * 支持两种采集模式：
 * <ul>
 *   <li><b>事后采集</b>：{@link #collectGatewayEvaluations} —— 流程结束后使用，数据来自 HistoricActivityInstance</li>
 *   <li><b>实时采集</b>：{@link #collectRealtimeEvaluations} —— 流程执行中使用，优先读取 SequenceFlowTakeListener 写入的流程变量</li>
 * </ul>
 * </p>
 *
 * @author TestPlatform
 */
@Slf4j
@Component
public class GatewayEvaluationRecorder {

    @Autowired
    private HistoryService historyService;

    @Autowired
    private RuntimeService runtimeService;

    @Resource
    private BpmnModelParser bpmnModelParser;

    @Resource
    private ObjectMapper objectMapper;

    // ==================== 事后采集（HistoryService） ====================

    /**
     * 收集指定流程实例中所有网关的评估记录（事后模式）
     *
     * @param processInstanceId Flowable 流程实例 ID
     * @return 网关评估记录列表（按开始时间升序）
     */
    public List<GatewayEvaluationVO> collectGatewayEvaluations(String processInstanceId) {
        log.info("开始采集网关评估记录 - processInstanceId={}", processInstanceId);

        String procDefId = resolveProcessDefinitionId(processInstanceId);
        if (procDefId == null) {
            log.warn("无法解析流程定义 ID: processInstanceId={}", processInstanceId);
            return Collections.emptyList();
        }

        BpmnModel bpmnModel = bpmnModelParser.getBpmnModel(procDefId);
        if (bpmnModel == null) {
            log.warn("无法获取 BpmnModel: procDefId={}", procDefId);
            return Collections.emptyList();
        }

        List<HistoricActivityInstance> allActivities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        Set<String> takenFlowIds = allActivities.stream()
                .filter(a -> "sequenceFlow".equals(a.getActivityType()))
                .map(HistoricActivityInstance::getActivityId)
                .collect(Collectors.toSet());

        List<GatewayEvaluationVO> result = new ArrayList<>();
        for (HistoricActivityInstance activity : allActivities) {
            String activityType = activity.getActivityType();
            if (!isGatewayType(activityType)) continue;

            GatewayEvaluationVO evaluation = buildGatewayEvaluation(activity, bpmnModel, takenFlowIds);
            if (evaluation != null) {
                result.add(evaluation);
            }
        }

        log.info("网关评估采集完成 - processInstanceId={}, gatewayCount={}", processInstanceId, result.size());
        return result;
    }

    /**
     * 检测排他网关是否因"无匹配分支"而失败
     *
     * @param processInstanceId 流程实例 ID
     * @return 若存在失败的排他网关，返回其 gatewayId；否则返回 null
     */
    public String detectNoMatchGatewayFailure(String processInstanceId) {
        String procDefId = resolveProcessDefinitionId(processInstanceId);
        if (procDefId == null) return null;

        BpmnModel bpmnModel = bpmnModelParser.getBpmnModel(procDefId);
        if (bpmnModel == null) return null;

        List<HistoricActivityInstance> allActivities = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        Set<String> takenFlowIds = allActivities.stream()
                .filter(a -> "sequenceFlow".equals(a.getActivityType()))
                .map(HistoricActivityInstance::getActivityId)
                .collect(Collectors.toSet());

        for (HistoricActivityInstance activity : allActivities) {
            if (!"exclusiveGateway".equals(activity.getActivityType())
                    && !"inclusiveGateway".equals(activity.getActivityType())) {
                continue;
            }
            if (activity.getEndTime() == null) continue;

            FlowElement element = bpmnModelParser.getNodeById(bpmnModel, activity.getActivityId());
            if (!(element instanceof Gateway gateway)) continue;

            List<SequenceFlow> outgoing = gateway.getOutgoingFlows();
            if (outgoing == null || outgoing.isEmpty()) continue;

            boolean anyFlowTaken = outgoing.stream()
                    .anyMatch(sf -> takenFlowIds.contains(sf.getId()));

            if (!anyFlowTaken) {
                log.warn("检测到网关无匹配分支 - gatewayId={}, type={}",
                        activity.getActivityId(), activity.getActivityType());
                return activity.getActivityId();
            }
        }
        return null;
    }

    // ==================== 实时采集（流程变量） ====================

    /**
     * 实时采集网关走向（流程执行中，未结束时调用）
     * <p>
     * 优先读取 {@link SequenceFlowTakeListener} 写入的流程变量 {@code _gateway_takenFlows}，
     * 若变量不存在则降级使用 HistoryService 查询。
     * </p>
     *
     * @param processInstanceId 运行中的流程实例 ID
     * @return 网关评估记录列表（已触发的部分）
     */
    public List<GatewayEvaluationVO> collectRealtimeEvaluations(String processInstanceId) {
        log.info("开始实时采集网关走向 - processInstanceId={}", processInstanceId);

        List<TakenFlowVO> realtimeFlows = readRealtimeTakenFlows(processInstanceId);

        if (!realtimeFlows.isEmpty()) {
            return buildFromRealtimeFlows(processInstanceId, realtimeFlows);
        }

        log.debug("实时变量为空，降级使用 HistoryService 采集 - processInstanceId={}", processInstanceId);
        return collectGatewayEvaluations(processInstanceId);
    }

    /**
     * 从运行中流程实例读取 _gateway_takenFlows 变量
     */
    private List<TakenFlowVO> readRealtimeTakenFlows(String processInstanceId) {
        try {
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (pi == null) {
                return Collections.emptyList();
            }

            Object raw = runtimeService.getVariable(processInstanceId, SequenceFlowTakeListener.VAR_TAKEN_FLOWS);
            if (raw instanceof String str && !str.isBlank()) {
                List<Map<String, Object>> rawList = objectMapper.readValue(str, new TypeReference<>() {});
                List<TakenFlowVO> result = new ArrayList<>();
                for (Map<String, Object> record : rawList) {
                    result.add(TakenFlowVO.builder()
                            .flowId((String) record.get("flowId"))
                            .flowName((String) record.get("flowName"))
                            .conditionExpression((String) record.get("conditionExpression"))
                            .isDefault(Boolean.TRUE.equals(record.get("isDefault")))
                            .build());
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("读取实时 takenFlows 变量失败 - processInstanceId={}", processInstanceId, e);
        }
        return Collections.emptyList();
    }

    /**
     * 基于实时变量数据构建网关评估记录
     */
    private List<GatewayEvaluationVO> buildFromRealtimeFlows(String processInstanceId,
                                                             List<TakenFlowVO> realtimeFlows) {
        String procDefId = resolveProcessDefinitionId(processInstanceId);
        if (procDefId == null) return Collections.emptyList();

        BpmnModel bpmnModel = bpmnModelParser.getBpmnModel(procDefId);
        if (bpmnModel == null) return Collections.emptyList();

        // 构建 flowId → SequenceFlow 查找表
        Map<String, SequenceFlow> flowMap = new LinkedHashMap<>();
        for (FlowElement el : bpmnModelParser.getAllFlowElements(bpmnModel)) {
            if (el instanceof SequenceFlow sf) {
                flowMap.put(sf.getId(), sf);
            }
        }

        // 构建 flowId → sourceGatewayId 映射
        Map<String, String> flowToGateway = new LinkedHashMap<>();
        for (FlowElement el : bpmnModelParser.getAllFlowElements(bpmnModel)) {
            if (el instanceof Gateway gateway) {
                for (SequenceFlow sf : gateway.getOutgoingFlows()) {
                    flowToGateway.put(sf.getId(), gateway.getId());
                }
            }
        }

        // 按网关分组
        Map<String, List<TakenFlowVO>> gatewayFlows = new LinkedHashMap<>();
        for (TakenFlowVO flow : realtimeFlows) {
            String gatewayId = flowToGateway.get(flow.getFlowId());
            if (gatewayId != null) {
                gatewayFlows.computeIfAbsent(gatewayId, k -> new ArrayList<>()).add(flow);
            }
        }

        // 构建结果
        List<GatewayEvaluationVO> result = new ArrayList<>();
        for (Map.Entry<String, List<TakenFlowVO>> entry : gatewayFlows.entrySet()) {
            String gatewayId = entry.getKey();
            List<TakenFlowVO> takenFlows = entry.getValue();

            FlowElement el = bpmnModelParser.getNodeById(bpmnModel, gatewayId);
            String gatewayType = (el instanceof ExclusiveGateway) ? "exclusiveGateway"
                    : (el instanceof InclusiveGateway) ? "inclusiveGateway"
                    : (el instanceof ParallelGateway) ? "parallelGateway" : "unknown";

            String matchedFlowId = null;
            if ("exclusiveGateway".equals(gatewayType) && !takenFlows.isEmpty()) {
                matchedFlowId = takenFlows.stream()
                        .filter(f -> !Boolean.TRUE.equals(f.getIsDefault()))
                        .map(TakenFlowVO::getFlowId)
                        .findFirst()
                        .orElse(takenFlows.get(0).getFlowId());
            }

            Integer gatewayTypeCode = switch (gatewayType) {
                case "exclusiveGateway" -> 4;
                case "parallelGateway" -> 5;
                case "inclusiveGateway" -> 6;
                default -> null;
            };

            result.add(GatewayEvaluationVO.builder()
                    .gatewayId(gatewayId)
                    .gatewayName(el != null ? el.getName() : null)
                    .gatewayType(gatewayType)
                    .gatewayNodeKey(gatewayId)
                    .gatewayNodeName(el != null ? el.getName() : null)
                    .gatewayTypeCode(gatewayTypeCode)
                    .takenFlows(takenFlows)
                    .matchedFlowId(matchedFlowId)
                    .evaluationResult(true)
                    .evaluatedAt(LocalDateTime.now())
                    .build());
        }

        log.info("实时网关走向采集完成 - processInstanceId={}, gatewayCount={}", processInstanceId, result.size());
        return result;
    }

    // ==================== 私有辅助方法 ====================

    private GatewayEvaluationVO buildGatewayEvaluation(
            HistoricActivityInstance activity, BpmnModel bpmnModel, Set<String> takenFlowIds) {

        String gatewayId = activity.getActivityId();
        String gatewayType = activity.getActivityType();

        FlowElement element = bpmnModelParser.getNodeById(bpmnModel, gatewayId);
        if (!(element instanceof Gateway gateway)) return null;

        List<SequenceFlow> outgoingFlows = gateway.getOutgoingFlows();
        String defaultFlowId = gateway.getDefaultFlow();

        List<TakenFlowVO> takenFlows = new ArrayList<>();
        if (outgoingFlows != null) {
            for (SequenceFlow sf : outgoingFlows) {
                if (takenFlowIds.contains(sf.getId())) {
                    takenFlows.add(TakenFlowVO.builder()
                            .flowId(sf.getId())
                            .flowName(sf.getName())
                            .conditionExpression(sf.getConditionExpression())
                            .isDefault(sf.getId() != null && sf.getId().equals(defaultFlowId))
                            .build());
                }
            }
        }

        String matchedFlowId = null;
        if ("exclusiveGateway".equals(gatewayType) && !takenFlows.isEmpty()) {
            matchedFlowId = takenFlows.stream()
                    .filter(f -> !Boolean.TRUE.equals(f.getIsDefault()))
                    .map(TakenFlowVO::getFlowId)
                    .findFirst()
                    .orElse(takenFlows.get(0).getFlowId());
        }

        Integer gatewayTypeCode = switch (gatewayType) {
            case "exclusiveGateway" -> 4;
            case "parallelGateway" -> 5;
            case "inclusiveGateway" -> 6;
            default -> null;
        };

        return GatewayEvaluationVO.builder()
                .gatewayId(gatewayId)
                .gatewayName(activity.getActivityName())
                .gatewayType(gatewayType)
                .gatewayNodeKey(gatewayId)
                .gatewayNodeName(activity.getActivityName())
                .gatewayTypeCode(gatewayTypeCode)
                .takenFlows(takenFlows)
                .matchedFlowId(matchedFlowId)
                .evaluationResult(!takenFlows.isEmpty())
                .evaluatedAt(toLocalDateTime(activity.getStartTime()))
                .build();
    }

    private String resolveProcessDefinitionId(String processInstanceId) {
        HistoricProcessInstance historicProcess = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return historicProcess != null ? historicProcess.getProcessDefinitionId() : null;
    }

    private boolean isGatewayType(String activityType) {
        return "exclusiveGateway".equals(activityType)
                || "parallelGateway".equals(activityType)
                || "inclusiveGateway".equals(activityType);
    }

    /**
     * 收集所有已触发的连线ID（用于前端动态连线动画）
     * 优先读取实时变量，降级到HistoryService查询
     */
    public List<String> collectAllTakenFlowIds(String processInstanceId) {
        // 先尝试实时变量
        try {
            Object raw = runtimeService.getVariable(processInstanceId, SequenceFlowTakeListener.VAR_TAKEN_FLOWS);
            if (raw instanceof String str && !str.isBlank()) {
                List<Map<String, Object>> rawList = objectMapper.readValue(str, new TypeReference<>() {});
                List<String> flowIds = new ArrayList<>();
                for (Map<String, Object> record : rawList) {
                    String flowId = (String) record.get("flowId");
                    if (flowId != null && !flowIds.contains(flowId)) {
                        flowIds.add(flowId);
                    }
                }
                return flowIds;
            }
        } catch (Exception e) {
            log.debug("读取实时takenFlows失败，降级到History查询", e);
        }

        // 降级到HistoryService查询
        try {
            return historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .activityType("sequenceFlow")
                    .orderByHistoricActivityInstanceStartTime().asc()
                    .list()
                    .stream()
                    .map(HistoricActivityInstance::getActivityId)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询History中的连线失败", e);
            return Collections.emptyList();
        }
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}

