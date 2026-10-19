package com.example.testplatform.workflow.engine.listener;

import com.example.testplatform.workflow.domain.po.FlowExecution;
import com.example.testplatform.workflow.mapper.FlowExecutionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程完成事件监听器
 * <p>
 * 监听 Flowable 流程实例完成/取消事件，主动同步更新 flow_execution 表状态，
 * 解决"执行已完成但 flow_execution 仍为 status=0（执行中）"的脏数据问题。
 * </p>
 * <p>
 * 原实现采用"懒更新"模式：仅在查询执行结果（getExecutionResult）时才更新状态，
 * 若流程结束后无人查询，记录将永久停留在"执行中"，造成历史记录中的僵尸数据。
 * 本监听器在流程结束时立即触发状态更新，保证数据一致性。
 * </p>
 *
 * @author TestPlatform
 */
@Slf4j
@Component
public class FlowCompletionEventListener implements FlowableEventListener {

    @Resource
    private FlowExecutionMapper flowExecutionMapper;

    /**
     * 使用 @Lazy 注入 HistoryService 以打破循环依赖：
     * flowableConfig → flowCompletionEventListener → historyServiceBean → processEngine → flowableConfig
     */
    @Resource
    @Lazy
    private HistoryService historyService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 处理流程事件
     */
    @Override
    public void onEvent(FlowableEvent event) {
        String type = event.getType() != null ? event.getType().name() : null;
        if ("PROCESS_COMPLETED".equals(type)) {
            handleProcessCompleted(event);
        } else if ("PROCESS_CANCELLED".equals(type)) {
            handleProcessCancelled(event);
        }
        // 其他事件忽略
    }

    /**
     * 处理流程正常完成事件
     */
    private void handleProcessCompleted(FlowableEvent event) {
        String processInstanceId = extractProcessInstanceId(event);
        if (processInstanceId == null) return;

        log.info("收到流程完成事件: processInstanceId={}", processInstanceId);
        updateExecutionStatus(processInstanceId, false);
    }

    /**
     * 处理流程取消事件（异常终止、节点失败主动终止）
     */
    private void handleProcessCancelled(FlowableEvent event) {
        String processInstanceId = extractProcessInstanceId(event);
        if (processInstanceId == null) return;

        log.info("收到流程取消事件: processInstanceId={}", processInstanceId);
        updateExecutionStatus(processInstanceId, true);
    }

    /**
     * 更新 flow_execution 表状态
     *
     * @param processInstanceId 流程实例 ID
     * @param isCancelled       是否为取消（失败）流程
     */
    private void updateExecutionStatus(String processInstanceId, boolean isCancelled) {
        try {
            // 查询对应的 flow_execution 记录
            FlowExecution execution = flowExecutionMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowExecution>()
                            .eq(FlowExecution::getProcInstId, processInstanceId)
                            .orderByDesc(FlowExecution::getStartTime)
                            .last("LIMIT 1"));

            if (execution == null) {
                log.warn("流程完成事件未找到对应执行记录: processInstanceId={}", processInstanceId);
                return;
            }

            // 仅更新仍为"执行中"的记录，避免重复更新已完成记录
            if (execution.getStatus() != null && execution.getStatus() != 0) {
                log.debug("执行记录已是终态，跳过更新: executionId={}, status={}",
                        execution.getId(), execution.getStatus());
                return;
            }

            // 查询历史流程实例获取结束信息
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();

            if (historicInstance == null) {
                log.warn("未找到历史流程实例: processInstanceId={}", processInstanceId);
                return;
            }

            // 判定最终状态：取消或有 deleteReason → 失败(2)，否则 → 成功(1)
            boolean hasError = isCancelled || historicInstance.getDeleteReason() != null;
            int status = hasError ? 2 : 1;

            // 计算结束时间与耗时
            LocalDateTime endTime = null;
            Long duration = null;
            if (historicInstance.getEndTime() != null) {
                endTime = historicInstance.getEndTime().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
                duration = historicInstance.getDurationInMillis();
            }

            // 获取流程变量作为输出
            String outputParamsJson = null;
            try {
                List<HistoricVariableInstance> varInstances = historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .list();
                Map<String, Object> outputVars = varInstances.stream()
                        .collect(Collectors.toMap(
                                HistoricVariableInstance::getVariableName,
                                v -> v.getValue() != null ? v.getValue() : "",
                                (a, b) -> b
                        ));
                outputParamsJson = objectMapper.writeValueAsString(outputVars);
            } catch (Exception e) {
                log.warn("序列化流程输出变量失败: processInstanceId={}", processInstanceId, e);
            }

            // 更新执行记录
            execution.setStatus(status);
            execution.setEndTime(endTime);
            execution.setDuration(duration);
            execution.setOutputParams(outputParamsJson);
            execution.setErrorMsg(hasError ? historicInstance.getDeleteReason() : null);
            execution.setUpdatedAt(LocalDateTime.now());
            flowExecutionMapper.updateById(execution);

            log.info("流程执行状态已同步更新: executionId={}, processInstanceId={}, status={}({})",
                    execution.getId(), processInstanceId, status, hasError ? "失败" : "成功");
        } catch (Exception e) {
            log.error("更新流程执行状态失败: processInstanceId={}", processInstanceId, e);
        }
    }

    /**
     * 从事件中提取流程实例 ID
     * <p>PROCESS_COMPLETED/CANCELLED 事件的 entity 为 ProcessInstance 实例，
     * 其 id 即为流程实例 ID。</p>
     */
    private String extractProcessInstanceId(FlowableEvent event) {
        try {
            if (event instanceof FlowableEntityEvent) {
                Object entity = ((FlowableEntityEvent) event).getEntity();
                if (entity instanceof org.flowable.engine.runtime.ProcessInstance) {
                    return ((org.flowable.engine.runtime.ProcessInstance) entity).getId();
                }
                if (entity instanceof org.flowable.engine.impl.persistence.entity.ExecutionEntity) {
                    return ((org.flowable.engine.impl.persistence.entity.ExecutionEntity) entity).getProcessInstanceId();
                }
            }
        } catch (Exception e) {
            log.debug("提取 processInstanceId 失败: event={}", event.getType(), e);
        }
        return null;
    }

    @Override
    public boolean isFailOnException() {
        // 监听器内部异常不阻断流程引擎主流程
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
