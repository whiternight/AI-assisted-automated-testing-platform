package com.example.testplatform.workflow.engine.delegate;

import com.example.testplatform.apicase.domain.dto.CaseDebugRequestDTO;
import com.example.testplatform.apicase.domain.vo.CaseDebugResponseVO;
import com.example.testplatform.common.exception.CaseExecuteException;
import com.example.testplatform.execution.domain.po.TaskNodeInstance;
import com.example.testplatform.execution.engine.CaseExecuteEngine;
import com.example.testplatform.execution.service.IExecutionLogService;
import com.example.testplatform.execution.service.ITaskNodeInstanceService;
import com.example.testplatform.enums.LogTypeEnum;
import com.example.testplatform.originalservice.domain.po.OriginalService;
import com.example.testplatform.originalservice.service.IOriginalServiceService;
import com.example.testplatform.workflow.domain.po.FlowNode;
import com.example.testplatform.workflow.mapper.FlowNodeMapper;
import com.example.testplatform.workflow.engine.util.AssertionChecker;
import com.example.testplatform.workflow.engine.util.InputParamResolver;
import com.example.testplatform.workflow.engine.util.OutputParamExtractor;
import com.example.testplatform.workflow.service.BpmnModelParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.delegate.BpmnError;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 原服务执行委托
 * <p>
 * Flowable serviceTask 的 JavaDelegate 实现，
 * 根据节点配置调用对应的原服务（HTTP/Dubbo）。
 * </p>
 * <pre>
 * BPMN XML 配置示例：
 * &lt;serviceTask id="node_1" name="查询订单"
 *     flowable:delegateExpression="${originalServiceDelegate}"&gt;
 * &lt;/serviceTask&gt;
 * </pre>
 *
 * @author TestPlatform
 */
@Slf4j
@Component("originalServiceDelegate")
public class OriginalServiceDelegate implements JavaDelegate {

    @Autowired
    private InputParamResolver inputParamResolver;

    @Resource
    private FlowNodeMapper flowNodeMapper;

    @Resource
    private IOriginalServiceService originalServiceService;

    @Resource
    private CaseExecuteEngine caseExecuteEngine;

    @Resource
    private AssertionChecker assertionChecker;

    @Resource
    private OutputParamExtractor outputParamExtractor;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private BpmnModelParser bpmnModelParser;

    @Resource
    private IExecutionLogService executionLogService;

    @Resource
    private ITaskNodeInstanceService taskNodeInstanceService;

    /**
     * 最大重试次数上限（安全阀，防止 FlowNode 配置异常值）
     */
    private static final int MAX_RETRY_LIMIT = 5;

    /**
     * Flowable serviceTask 执行入口
     * <p>处理流程：
     * <ol>
     *   <li>根据当前节点 ID 查询 FlowNode 配置（优先 flowDefinitionId 精确查询）</li>
     *   <li>查询关联的原服务及用例 ID</li>
     *   <li>解析重试配置（最大 5 次，支持固定间隔/指数退避）</li>
     *   <li>带重试的执行循环：仅 ENV_ERROR 触发重试，其他错误立即失败</li>
     * </ol>
     * 错误分类：PLATFORM_ERROR（平台异常）、ENV_ERROR（环境错误，可重试）、
     * ASSERTION_FAIL（断言失败）、CASE_ERROR（用例错误）。</p>
     *
     * @param execution Flowable 委托执行上下文
     * @throws BpmnError 节点配置缺失、原服务不存在、用例执行失败或断言失败时抛出
     */
    @Override
    public void execute(DelegateExecution execution) {
        String nodeId = execution.getCurrentActivityId();
        String nodeName = execution.getCurrentActivityName();
        String procInstId = execution.getProcessInstanceId();

        log.info("原服务委托开始 - procInstId={}, nodeId={}, nodeName={}", procInstId, nodeId, nodeName);

        // 前置节点已失败时，仅跳过当前 BPMN 可达路径上的后继节点
        String failedNode = getStringVariable(execution, "_flow_failed_node", null);
        if (failedNode != null) {
            if (isSuccessor(execution.getProcessDefinitionId(), failedNode, nodeId)) {
                log.info("跳过节点 [{}] 因为前置节点 [{}] 已失败 - procInstId={}", nodeId, failedNode, procInstId);
                execution.setVariable("node_" + nodeId + "_skipped", true);
                return;
            }
            log.info("节点 [{}] 不是失败节点 [{}] 的后继，继续执行 - procInstId={}", nodeId, failedNode, procInstId);
        }

        try {
            // 1. 查询 FlowNode 配置
            FlowNode flowNode = findFlowNode(nodeId, execution);
            if (flowNode == null) {
                String msg = "未找到节点配置: nodeId=" + nodeId;
                log.error(msg);
                setNodeError(execution, nodeId, "PLATFORM_ERROR", msg);
                throw new RuntimeException(msg);
            }

            // 2. 查询原服务配置
            String originalServiceId = flowNode.getOriginalServiceId();
            if (originalServiceId == null || originalServiceId.isBlank()) {
                String msg = "节点 [" + nodeId + "] 未关联原服务";
                log.error(msg);
                setNodeError(execution, nodeId, "PLATFORM_ERROR", msg);
                throw new RuntimeException(msg);
            }

            OriginalService originalService = originalServiceService.getById(originalServiceId);
            if (originalService == null) {
                String msg = "原服务不存在: " + originalServiceId;
                log.error(msg);
                setNodeError(execution, nodeId, "PLATFORM_ERROR", msg);
                throw new RuntimeException(msg);
            }

            String caseId = originalService.getCaseId();
            if (caseId == null || caseId.isBlank()) {
                String msg = "原服务 [" + originalServiceId + "] 未关联用例";
                log.error(msg);
                setNodeError(execution, nodeId, "PLATFORM_ERROR", msg);
                throw new RuntimeException(msg);
            }

            // 存储 originalServiceId 到流程变量（供 ExecutionTraceCollector 查询）
            execution.setVariable("node_" + nodeId + "_originalServiceId", originalServiceId);

            // 3. 解析重试配置
            int maxRetries = resolveMaxRetries(flowNode);
            int retryInterval = flowNode.getRetryInterval() != null ? flowNode.getRetryInterval() : 1000;
            int retryType = flowNode.getRetryType() != null ? flowNode.getRetryType() : 1;

            // 4. 带重试的执行循环
            int attempt = 0;
            String lastErrorCode = null;
            String lastErrorMsg = null;

            while (true) {
                try {
                    executeOnce(execution, flowNode, originalService, caseId, nodeId);

                    // 执行成功（含断言通过），记录成功结果并退出
                    log.info("原服务委托执行成功 - procInstId={}, nodeId={}, attempt={}",
                            procInstId, nodeId, attempt);
                    return;

                } catch (RuntimeException e) {
                    String errorCode = extractErrorCode(e);
                    lastErrorCode = errorCode;
                    lastErrorMsg = e.getMessage();

                    // ENV_ERROR 且还有重试次数 → 执行重试
                    if ("ENV_ERROR".equals(errorCode) && attempt < maxRetries) {
                        attempt++;
                        long delay = calculateRetryDelay(attempt, retryInterval, retryType);

                        // 从流程变量读取上一次执行的 taskId/nodeInstanceId
                        String lastTaskId = getStringVariable(execution, "_retry_taskId_" + nodeId, null);
                        String lastNodeInstanceId = getStringVariable(execution, "_retry_nodeInstanceId_" + nodeId, null);

                        log.warn("ENV_ERROR 触发重试 - nodeId={}, attempt={}/{}, delay={}ms, error={}",
                                nodeId, attempt, maxRetries, delay, e.getMessage());

                        // 记录重试日志到 execution_log
                        recordRetryLog(nodeId, attempt, maxRetries, delay,
                                e.getMessage(), lastTaskId, lastNodeInstanceId);

                        // 尝试更新 task_node_instance 的 retryCount
                        updateNodeInstanceRetryCount(lastNodeInstanceId, attempt);

                        // 等待重试间隔
                        sleep(delay);
                        continue;
                    }

                    // 非 ENV_ERROR 或重试次数用尽 → 标记失败并中断流程
                    if ("ENV_ERROR".equals(errorCode) && attempt > 0) {
                        log.error("ENV_ERROR 重试次数用尽 - nodeId={}, attempts={}, 标记 FAILED",
                                nodeId, attempt);
                    }
                    // 关键修改：抛 RuntimeException 而非 BpmnError，让 Flowable 标记流程失败但不影响 processInstanceId
                    setNodeError(execution, nodeId, errorCode, e.getMessage());
                    throw e;
                }
            }

        } catch (RuntimeException e) {
            // 不抛异常！设置流程失败标志，让后续节点跳过执行。
            // 这样 startProcessInstanceById 能正常返回 processInstanceId，
            // 前端可轮询获取节点轨迹（失败节点标红、后续节点标为未执行）。
            log.error("原服务委托执行失败 - procInstId={}, nodeId={}, error={}", procInstId, nodeId, e.getMessage());
            execution.setVariable("_flow_failed_node", nodeId);
            execution.setVariable("_flow_failed_nodeName", nodeName);
        }
    }

    /**
     * 从异常中提取错误码
     * <p>支持两种情况：
     * <ul>
     *   <li>BpmnError：从 errorCode 字段取</li>
     *   <li>RuntimeException：从 setNodeError 设置的流程变量取</li>
     * </ul>
     * </p>
     */
    private String extractErrorCode(Exception e) {
        if (e instanceof BpmnError) {
            return ((BpmnError) e).getErrorCode();
        }
        String msg = e.getMessage();
        if (msg == null) {
            return "PLATFORM_ERROR";
        }
        // 尝试从消息中提取错误码（executeOnce 中 throw new RuntimeException(errorType + ": " + msg)）
        if (msg.startsWith("ASSERTION_FAIL")) return "ASSERTION_FAIL";
        if (msg.startsWith("ENV_ERROR")) return "ENV_ERROR";
        if (msg.startsWith("CASE_ERROR")) return "CASE_ERROR";
        return "PLATFORM_ERROR";
    }

    /**
     * 单次执行原服务（不含重试逻辑）
     * <p>包含：参数解析 → 调用执行引擎 → 输出提取 → 断言校验</p>
     *
     * @return 执行响应（成功时返回）
     * @throws BpmnError 执行失败时抛出
     */
    private CaseDebugResponseVO executeOnce(DelegateExecution execution, FlowNode flowNode,
                                             OriginalService originalService, String caseId,
                                             String nodeId) {
        // 1. 解析输入参数
        Map<String, Object> requestParams = inputParamResolver.resolve(flowNode.getInputParams(), execution.getVariables());
        log.debug("参数解析完成: nodeId={}, params={}", nodeId, requestParams);

        // 将输入参数快照写入流程变量（供 ExecutionTraceCollector 查询）
        try {
            execution.setVariable("node_" + nodeId + "_input",
                    objectMapper.writeValueAsString(requestParams));
        } catch (Exception ignored) {}

        // 2. 构建执行请求
        CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();
        requestDTO.setParams(requestParams);
        requestDTO.setEnv(getStringVariable(execution, "env", "default"));
        if (flowNode.getTimeout() != null && flowNode.getTimeout() > 0) {
            requestDTO.setTimeout(flowNode.getTimeout());
        }

        // 3. 调用执行引擎
        Long executorId = getLongVariable(execution, "executorId", null);
        log.info("开始执行用例: caseId={}, nodeId={}", caseId, nodeId);

        CaseDebugResponseVO response;
        try {
            response = caseExecuteEngine.execute(caseId, requestDTO, executorId);
        } catch (CaseExecuteException e) {
            String errorCode = e.getErrorType() != null ? e.getErrorType().name() : "PLATFORM_ERROR";
            String msg = "用例执行异常: " + e.getMessage();
            log.error("用例执行异常: caseId={}, nodeId={}, errorCode={}", caseId, nodeId, errorCode, e);
            setNodeError(execution, nodeId, errorCode, msg);
            throw new RuntimeException(errorCode + ": " + msg);
        } catch (Exception e) {
            String msg = "用例执行失败: " + e.getMessage();
            log.error("用例执行失败: caseId={}, nodeId={}", caseId, nodeId, e);
            setNodeError(execution, nodeId, "PLATFORM_ERROR", msg);
            throw new RuntimeException("PLATFORM_ERROR: " + msg);
        }

        // 4. 检查执行结果
        if (response == null) {
            String msg = "执行引擎返回空结果: caseId=" + caseId;
            setNodeError(execution, nodeId, "PLATFORM_ERROR", msg);
            throw new RuntimeException("PLATFORM_ERROR: " + msg);
        }

        // 检查是否有错误类型（环境错误、断言失败等）
        if (response.getErrorType() != null && !response.getErrorType().isBlank()) {
            String errorType = response.getErrorType();
            String msg = "节点执行失败: type=" + errorType + ", msg=" + response.getErrorMsg();
            log.warn(msg);
            // 将 taskId/nodeInstanceId 存入流程变量，供重试逻辑读取
            if (response.getTaskId() != null) {
                execution.setVariable("_retry_taskId_" + nodeId, response.getTaskId());
            }
            if (response.getNodeInstanceId() != null) {
                execution.setVariable("_retry_nodeInstanceId_" + nodeId, response.getNodeInstanceId());
            }
            setNodeError(execution, nodeId, errorType, msg);
            // 断言失败时流程继续（token 仍可到达汇合点），其他错误仍终止流程
            if ("ASSERTION_FAIL".equals(errorType)) {
                execution.setVariable("_flow_failed_node", nodeId);
                log.info("断言失败后继续驱动 token 流向汇合点 - procInstId={}, nodeId={}",
                        execution.getProcessInstanceId(), nodeId);
                return response;
            }
            throw new RuntimeException(errorType + ": " + msg);
        }

        // 5. 解析输出参数（JSONPath 提取）
        Object responseBody = response.getResponse() != null ? response.getResponse().getBody() : null;
        Map<String, Object> userOutputParams = outputParamExtractor.extract(responseBody, flowNode.getOutputParams());
        // 始终放入基础信息
        Map<String, Object> extractedOutput = new HashMap<>(userOutputParams);
        extractedOutput.put("status", response.getStatus());
        extractedOutput.put("result", response.getResult());
        extractedOutput.put("duration", response.getDuration());
        execution.setVariable("node_" + nodeId + "_result", extractedOutput);
        // 将用户自定义输出参数字段同时展开到根流程变量，供下游节点通过 $.fieldName 直接引用
        userOutputParams.forEach(execution::setVariable);
        log.debug("输出参数已写入流程变量: node_{}_result={}", nodeId, extractedOutput);

        // 存储输出快照到流程变量
        try {
            execution.setVariable("node_" + nodeId + "_output",
                    objectMapper.writeValueAsString(extractedOutput));
        } catch (Exception ignored) {}

        // 6. 断言校验（节点级断言优先，无则回退到原服务级断言）
        String assertions = (flowNode.getAssertions() != null && !flowNode.getAssertions().isBlank())
                ? flowNode.getAssertions()
                : originalService.getAssertions();
        if (assertions != null && !assertions.isBlank()) {
            Object responseForAssertion = response.getResponse(); // ResponseInfoVO 对象
            Map<String, String> responseHeaders = response.getResponse() != null
                    ? response.getResponse().getHeaders() : null;
            Long duration = response.getDuration();

            boolean allPassed = assertionChecker.check(
                    assertions, responseForAssertion, responseHeaders, duration);
            if (!allPassed) {
                String msg = "断言校验失败: " + assertionChecker.getFailureSummary();
                log.warn(msg);
                setNodeError(execution, nodeId, "ASSERTION_FAIL", msg);
                execution.setVariable("_flow_failed_node", nodeId);
                log.info("断言失败后继续驱动 token 流向汇合点 - procInstId={}, nodeId={}",
                        execution.getProcessInstanceId(), nodeId);
                return response;
            }
        }

        return response;
    }

    /**
     * 根据 nodeId 和 flowDefinitionId 查询 FlowNode 配置
     * <p>查询策略：
     * <ol>
     *   <li>优先从流程变量读取 flowDefinitionId，结合 nodeKey 精确查询（避免跨流程同名节点冲突）</li>
     *   <li>降级：仅通过 nodeKey 查询，取最近创建的一条（兼容未注入 flowDefinitionId 的旧流程）</li>
     * </ol>
     * </p>
     *
     * @param nodeId    BPMN 节点 ID（对应 FlowNode.nodeKey）
     * @param execution Flowable 委托执行上下文
     * @return FlowNode 配置，未找到返回 null
     */
    private FlowNode findFlowNode(String nodeId, DelegateExecution execution) {
        // 从流程变量获取 flowDefinitionId
        String flowDefId = getStringVariable(execution, "flowDefinitionId", null);

        // 优先通过 nodeKey + flowDefinitionId 精确查询
        // @TableLogic 启用后，MP 自动过滤 deleted=1
        if (flowDefId != null && !flowDefId.isBlank()) {
            return flowNodeMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowNode>()
                            .eq(FlowNode::getNodeKey, nodeId)
                            .eq(FlowNode::getFlowDefinitionId, flowDefId)
            );
        }

        // 降级：仅通过 nodeKey 查询
        return flowNodeMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowNode>()
                        .eq(FlowNode::getNodeKey, nodeId)
                        .orderByDesc(FlowNode::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    private String getStringVariable(DelegateExecution execution, String name, String defaultValue) {
        Object value = execution.getVariable(name);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }

    /**
     * 将节点错误信息写入流程变量（供 ExecutionTraceCollector 查询）
     */
    private void setNodeError(DelegateExecution execution, String nodeId, String errorType, String errorMsg) {
        execution.setVariable("node_" + nodeId + "_errorType", errorType);
        execution.setVariable("node_" + nodeId + "_errorMsg", errorMsg);
    }

    // ==================== 重试相关方法 ====================

    /**
     * 解析 FlowNode 的最大重试次数（0~5）
     */
    private int resolveMaxRetries(FlowNode flowNode) {
        if (flowNode.getRetryCount() == null || flowNode.getRetryCount() <= 0) {
            return 0;
        }
        return Math.min(flowNode.getRetryCount(), MAX_RETRY_LIMIT);
    }

    /**
     * 计算重试等待时间
     *
     * @param attempt       当前重试次数（从 1 开始）
     * @param baseInterval  基础间隔（毫秒）
     * @param retryType     1=固定间隔，2=指数退避
     * @return 等待时间（毫秒）
     */
    private long calculateRetryDelay(int attempt, int baseInterval, int retryType) {
        if (retryType == 2) {
            // 指数退避：baseInterval * 2^(attempt-1)
            // 例如：1000ms, 2000ms, 4000ms, 8000ms, 16000ms
            return (long) baseInterval * (1L << (attempt - 1));
        }
        // 固定间隔
        return baseInterval;
    }

    /**
     * 记录重试日志到 execution_log 表
     */
    private void recordRetryLog(String nodeId,
                                int attempt, int maxRetries, long delay,
                                String errorMsg, String taskId, String nodeInstanceId) {
        try {
            String content = String.format("重试 %d/%d - 等待 %dms - 错误: %s",
                    attempt, maxRetries, delay, errorMsg);
            String detail = String.format(
                    "{\"nodeId\":\"%s\",\"attempt\":%d,\"maxRetries\":%d,\"delay\":%d,\"error\":\"%s\"}",
                    nodeId, attempt, maxRetries, delay,
                    errorMsg != null ? errorMsg.replace("\"", "\\\"") : "");

            executionLogService.saveLogAsync(
                    taskId,
                    nodeInstanceId,
                    LogTypeEnum.RETRY,
                    content,
                    detail
            );
        } catch (Exception e) {
            log.warn("重试日志记录失败: nodeId={}, attempt={}", nodeId, attempt, e);
        }
    }

    /**
     * 更新 task_node_instance 的 retryCount
     */
    private void updateNodeInstanceRetryCount(String nodeInstanceId, int retryCount) {
        if (nodeInstanceId == null || nodeInstanceId.isBlank()) {
            return;
        }
        try {
            TaskNodeInstance update = new TaskNodeInstance();
            update.setId(nodeInstanceId);
            update.setRetryCount(retryCount);
            taskNodeInstanceService.updateById(update);
            log.debug("节点实例重试次数已更新: nodeInstanceId={}, retryCount={}", nodeInstanceId, retryCount);
        } catch (Exception e) {
            log.warn("更新节点实例重试次数失败: nodeInstanceId={}", nodeInstanceId, e);
        }
    }

    /**
     * 线程休眠（重试等待）
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("重试等待被中断");
        }
    }

    private Long getLongVariable(DelegateExecution execution, String name, Long defaultValue) {
        Object value = execution.getVariable(name);
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 判断 targetNodeId 是否可以通过 BPMN 出线从 sourceNodeId 到达
     * <p>用于并行网关场景：失败节点所在分支的兄弟节点不应被跳过，
     * 只有失败节点在 BPMN 上的真正后继节点才应跳过。</p>
     */
    private boolean isSuccessor(String processDefinitionId, String sourceNodeId, String targetNodeId) {
        if (sourceNodeId == null || targetNodeId == null || sourceNodeId.equals(targetNodeId)) {
            return false;
        }
        try {
            BpmnModel bpmnModel = bpmnModelParser.getBpmnModel(processDefinitionId);
            if (bpmnModel == null) {
                return true; // 解析失败时保守处理，按原逻辑跳过
            }

            Map<String, FlowElement> elementMap = new HashMap<>();
            for (org.flowable.bpmn.model.Process process : bpmnModel.getProcesses()) {
                for (FlowElement element : process.getFlowElements()) {
                    elementMap.put(element.getId(), element);
                }
            }

            Set<String> visited = new HashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.offer(sourceNodeId);
            visited.add(sourceNodeId);

            while (!queue.isEmpty()) {
                String currentId = queue.poll();
                FlowElement current = elementMap.get(currentId);
                if (!(current instanceof org.flowable.bpmn.model.FlowNode currentNode)) {
                    continue;
                }
                for (SequenceFlow flow : currentNode.getOutgoingFlows()) {
                    String nextId = flow.getTargetRef();
                    if (targetNodeId.equals(nextId)) {
                        return true;
                    }
                    if (!visited.contains(nextId)) {
                        visited.add(nextId);
                        queue.offer(nextId);
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("BPMN 可达性判断失败: processDefinitionId={}, source={}, target={}",
                    processDefinitionId, sourceNodeId, targetNodeId, e);
            return true; // 异常时保守处理，按原逻辑跳过
        }
    }
}
