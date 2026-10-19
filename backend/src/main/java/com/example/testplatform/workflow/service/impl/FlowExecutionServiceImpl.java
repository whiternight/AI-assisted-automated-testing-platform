package com.example.testplatform.workflow.service.impl;

import com.example.testplatform.common.exception.BusinessException;
import com.example.testplatform.common.util.AesEncryptUtil;
import com.example.testplatform.common.util.SecurityUtil;
import com.example.testplatform.common.util.TaskNoGenerator;
import com.example.testplatform.enums.BusinessExceptionEnum;
import com.example.testplatform.workflow.domain.dto.FlowExecuteRequestDTO;
import com.example.testplatform.workflow.domain.po.FlowDefinition;
import com.example.testplatform.workflow.domain.po.FlowExecution;
import com.example.testplatform.workflow.domain.vo.FlowErrorDetailVO;
import com.example.testplatform.workflow.domain.vo.FlowExecuteResponseVO;
import com.example.testplatform.workflow.domain.vo.FlowExecuteResultVO;
import com.example.testplatform.workflow.domain.vo.FlowExecutionHistoryVO;
import com.example.testplatform.workflow.domain.vo.NodeDetailVO;
import com.example.testplatform.workflow.domain.vo.NodeExecutionTraceVO;
import com.example.testplatform.workflow.engine.gateway.GatewayEvaluationRecorder;
import com.example.testplatform.workflow.engine.trace.ExecutionTraceCollector;
import com.example.testplatform.workflow.engine.trace.FlowErrorLocator;
import com.example.testplatform.workflow.mapper.FlowExecutionMapper;
import com.example.testplatform.workflow.service.IFlowDefinitionService;
import com.example.testplatform.workflow.service.IFlowExecutionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 流程执行实例表 服务实现类
 *
 * @author TestPlatform
 * @since 2026-06-17
 */
@Slf4j
@Service
public class FlowExecutionServiceImpl extends ServiceImpl<FlowExecutionMapper, FlowExecution> implements IFlowExecutionService {

    @Resource
    private IFlowDefinitionService flowDefinitionService;

    @Resource
    private RuntimeService runtimeService;

    @Resource
    private RepositoryService repositoryService;

    @Resource
    private HistoryService historyService;

    @Resource
    private TaskNoGenerator taskNoGenerator;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ExecutionTraceCollector traceCollector;

    @Resource
    private FlowErrorLocator flowErrorLocator;

    @Resource
    private GatewayEvaluationRecorder gatewayRecorder;

    @Resource
    private AesEncryptUtil aesEncryptUtil;

    /**
     * 启动流程执行
     * <p>执行流程：
     * <ol>
     *   <li>校验流程定义存在、已部署（procDefId 非空）且状态为已发布（status=2）</li>
     *   <li>注入流程变量：flowDefinitionId（供节点精确查询）、executorId（执行人）</li>
     *   <li>调用 Flowable RuntimeService 启动流程实例</li>
     *   <li>插入 flow_execution 记录，状态 0=执行中</li>
     * </ol>
     * </p>
     *
     * @param flowDefinitionId 流程定义 ID
     * @param dto              执行请求（含输入参数、环境、超时等）
     * @return 执行响应（含 taskNo、executionId、processInstanceId）
     * @throws BusinessException 流程不存在、未部署、未发布或启动失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowExecuteResponseVO executeFlow(String flowDefinitionId, FlowExecuteRequestDTO dto) {
        log.info("开始执行流程: flowDefinitionId={}", flowDefinitionId);

        // 1. 从 flow_definition 表获取流程定义
        FlowDefinition flowDef = flowDefinitionService.getById(flowDefinitionId);
        if (flowDef == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "流程定义不存在: " + flowDefinitionId);
        }
        if (flowDef.getProcDefId() == null || flowDef.getProcDefId().isBlank()) {
            throw new BusinessException(BusinessExceptionEnum.VALIDATE_ERROR, "流程定义尚未部署，procDefId 为空");
        }
        // 验证 Flowable 端流程定义确实存在
        ProcessDefinition procDefCheck = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(flowDef.getProcDefId())
                .singleResult();
        if (procDefCheck == null) {
            throw new BusinessException(BusinessExceptionEnum.VALIDATE_ERROR,
                    "流程定义在 Flowable 引擎中不存在（procDefId=" + flowDef.getProcDefId() + "），请重新保存设计以重新部署");
        }
        // 兼容历史数据：数据库 status 字段可能与实际部署状态不一致
        // 只要 procDefId 有效（Flowable 端存在），就允许执行，并把 status 同步为 2
        if (flowDef.getStatus() == null || flowDef.getStatus() != 2) {
            log.warn("流程定义数据库 status={} 但已部署到Flowable，自动同步为2: flowDefinitionId={}", flowDef.getStatus(), flowDefinitionId);
            flowDef.setStatus(2);
            flowDef.setUpdatedAt(LocalDateTime.now());
            flowDefinitionService.updateById(flowDef);
        }

        // 2. 启动 Flowable 流程实例
        String procDefId = flowDef.getProcDefId();
        Map<String, Object> variables = dto.getInputParams() != null
                ? new java.util.HashMap<>(dto.getInputParams())
                : new java.util.HashMap<>();
        // 注入流程定义 ID，供 OriginalServiceDelegate.findFlowNode 精确查询使用
        variables.put("flowDefinitionId", flowDefinitionId);
        // 注入当前登录用户 ID 作为执行人，供 CaseExecuteEngine.createTaskAndNode 创建任务使用
        variables.putIfAbsent("executorId", SecurityUtil.getCurrentUserId());

        ProcessInstance processInstance;
        try {
            processInstance = runtimeService.startProcessInstanceById(procDefId, variables);
            log.info("Flowable 流程启动成功: processInstanceId={}", processInstance.getId());
        } catch (Exception e) {
            log.error("Flowable 流程启动失败: procDefId={}", procDefId, e);
            // 提取根因，方便定位问题
            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR,
                    "流程启动失败: " + rootCause.getMessage());
        }

        // 3. 生成执行编号
        String taskNo = taskNoGenerator.generate();
        LocalDateTime startTime = LocalDateTime.now();

        // 4. 插入 flow_execution 记录
        FlowExecution execution = new FlowExecution();
        execution.setFlowDefinitionId(flowDefinitionId);
        execution.setProcInstId(processInstance.getId());
        execution.setStatus(0); // 0=执行中
        execution.setStartTime(startTime);
        execution.setInputParams(serializeJson(variables));
        execution.setCreatedAt(startTime);
        execution.setUpdatedAt(startTime);
        execution.setDeleted(0);
        this.save(execution);

        log.info("流程执行记录已创建: executionId={}, taskNo={}, procInstId={}", execution.getId(), taskNo, processInstance.getId());

        // 5. 返回响应
        return FlowExecuteResponseVO.builder()
                .taskNo(taskNo)
                .executionId(execution.getId())
                .processInstanceId(processInstance.getId())
                .status(0)
                .startTime(startTime)
                .build();
    }

    /**
     * 查询流程执行结果
     * <p>根据 Flowable 流程实例 ID 查询执行状态与轨迹：
     * <ul>
     *   <li>运行中：状态 0，实时采集节点轨迹和网关走向</li>
     *   <li>已结束：状态 1=成功/2=失败，计算耗时、收集轨迹、提取输出变量，失败时定位错误节点</li>
     * </ul>
     * 结束后会同步更新 flow_execution 表的状态、结束时间、耗时及输出参数。</p>
     *
     * @param processInstanceId Flowable 流程实例 ID
     * @return 执行结果 VO（含节点轨迹、网关评估、错误详情）
     * @throws BusinessException 执行记录不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowExecuteResultVO getExecutionResult(String processInstanceId) {
        log.info("查询流程执行结果: processInstanceId={}", processInstanceId);

        // 1. 从 flow_execution 表查询记录
        FlowExecution execution = this.lambdaQuery()
                .eq(FlowExecution::getProcInstId, processInstanceId)
                .orderByDesc(FlowExecution::getStartTime)
                .last("LIMIT 1")
                .one();

        if (execution == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "执行记录不存在: " + processInstanceId);
        }

        // 2. 查询 Flowable 流程实例状态
        boolean isRunning = false;
        ProcessInstance runningInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runningInstance != null) {
            isRunning = true;
        }

        // 3. 查询历史流程实例
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        // 4. 构建响应
        FlowExecuteResultVO.FlowExecuteResultVOBuilder builder = FlowExecuteResultVO.builder()
                .processInstanceId(processInstanceId)
                .flowDefinitionId(execution.getFlowDefinitionId())
                .startTime(execution.getStartTime());

        if (isRunning) {
            // 流程仍在执行中
            builder.status(0);
            // 收集当前节点轨迹
            List<NodeExecutionTraceVO> traces = traceCollector.collectNodeTraces(processInstanceId);
            builder.nodeTraces(traces);
            // 实时采集网关走向（仪表盘用）
            builder.gatewayEvaluations(gatewayRecorder.collectRealtimeEvaluations(processInstanceId));
            // 收集已触发的连线，用于前端动态连线动画
            builder.activeSequenceFlows(gatewayRecorder.collectAllTakenFlowIds(processInstanceId));
        } else if (historicInstance != null) {
            // 流程已结束
            // 检查是否有节点失败标志（OriginalServiceDelegate 不抛异常而是设置 _flow_failed_node）
            boolean flowFailed = false;
            String failedNodeName = null;
            try {
                HistoricVariableInstance failedNodeVar = historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .variableName("_flow_failed_node")
                        .singleResult();
                if (failedNodeVar != null && failedNodeVar.getValue() != null) {
                    flowFailed = true;
                    failedNodeName = failedNodeVar.getValue().toString();
                }
            } catch (Exception e) {
                log.warn("查询 _flow_failed_node 变量失败: processInstanceId={}", processInstanceId, e);
            }
            boolean hasError = historicInstance.getDeleteReason() != null || flowFailed;
            int status = hasError ? 2 : 1; // 2=失败, 1=成功

            // 计算耗时
            Long totalDuration = null;
            LocalDateTime endTime = null;
            if (historicInstance.getEndTime() != null) {
                endTime = historicInstance.getEndTime().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
                totalDuration = historicInstance.getDurationInMillis();
            }

            // 收集节点轨迹
            List<NodeExecutionTraceVO> traces = traceCollector.collectNodeTraces(processInstanceId);

            // 获取流程变量作为输出
            Map<String, Object> outputVars = null;
            try {
                outputVars = historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .list()
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(
                                HistoricVariableInstance::getVariableName,
                                HistoricVariableInstance::getValue,
                                (a, b) -> b
                        ));
            } catch (Exception e) {
                log.warn("获取流程变量失败: processInstanceId={}", processInstanceId, e);
            }

            builder.status(status)
                    .endTime(endTime)
                    .totalDuration(totalDuration)
                    .nodeTraces(traces)
                    .outputParams(outputVars);

            // 失败时定位错误
            if (hasError) {
                FlowErrorDetailVO errorDetail = flowErrorLocator.locate(processInstanceId);
                builder.errorDetail(errorDetail);
            }

            // 事后采集网关走向
            builder.gatewayEvaluations(gatewayRecorder.collectGatewayEvaluations(processInstanceId));
            // 收集已触发的连线，用于前端显示执行路径
            builder.activeSequenceFlows(gatewayRecorder.collectAllTakenFlowIds(processInstanceId));

            // 同步更新 flow_execution 表
            if (execution.getStatus() == 0) {
                execution.setStatus(status);
                execution.setEndTime(endTime);
                execution.setDuration(totalDuration);
                execution.setOutputParams(serializeJson(outputVars));
                execution.setErrorMsg(hasError
                        ? (historicInstance.getDeleteReason() != null
                            ? historicInstance.getDeleteReason()
                            : "节点执行失败: " + failedNodeName)
                        : null);
                execution.setUpdatedAt(LocalDateTime.now());
                this.updateById(execution);
            }
        } else {
            // 无历史记录（异常情况）
            builder.status(2);
        }

        return builder.build();
    }

    /**
     * 分页查询执行历史
     * <p>支持按流程定义 ID、执行状态、流程名称模糊、时间范围组合过滤，结果按开始时间倒序，并补充流程名称与执行人。</p>
     *
     * @param pageNum           页码（从 1 开始）
     * @param pageSize          每页大小
     * @param flowDefinitionId  流程定义 ID（可空）
     * @param status            执行状态（0=执行中 1=成功 2=失败，可空）
     * @param flowName          流程名称模糊关键词（可空）
     * @param startTime         开始时间下界（可空，格式 yyyy-MM-dd HH:mm:ss，含）
     * @param endTime           开始时间上界（可空，格式 yyyy-MM-dd HH:mm:ss，含）
     * @return 执行历史 VO 分页结果
     */
    @Override
    public IPage<FlowExecutionHistoryVO> listExecutionHistory(Integer pageNum, Integer pageSize,
                                                              String flowDefinitionId, Integer status,
                                                              String flowName, String startTime, String endTime) {
        log.info("分页查询执行历史: pageNum={}, pageSize={}, flowDefinitionId={}, status={}, flowName={}, startTime={}, endTime={}",
                pageNum, pageSize, flowDefinitionId, status, flowName, startTime, endTime);

        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        java.time.LocalDateTime startLt = parseTime(startTime, dtf);
        java.time.LocalDateTime endLt = parseTime(endTime, dtf);
        boolean hasNameFilter = flowName != null && !flowName.isBlank();
        boolean hasTimeFilter = startLt != null || endLt != null;

        // 1. 查询 flow_execution（@TableLogic 自动过滤 deleted=1）
        //    若带名称/时间过滤，则不使用数据库分页，改为内存过滤后再分页，以保证结果一致性
        List<FlowExecution> all;
        if (hasNameFilter || hasTimeFilter) {
            all = this.lambdaQuery()
                    .eq(flowDefinitionId != null && !flowDefinitionId.isBlank(),
                            FlowExecution::getFlowDefinitionId, flowDefinitionId)
                    .eq(status != null, FlowExecution::getStatus, status)
                    .orderByDesc(FlowExecution::getStartTime)
                    .list();
        } else {
            Page<FlowExecution> pageParam = new Page<>(pageNum, pageSize);
            Page<FlowExecution> entityPage = this.lambdaQuery()
                    .eq(flowDefinitionId != null && !flowDefinitionId.isBlank(),
                            FlowExecution::getFlowDefinitionId, flowDefinitionId)
                    .eq(status != null, FlowExecution::getStatus, status)
                    .orderByDesc(FlowExecution::getStartTime)
                    .page(pageParam);
            all = entityPage.getRecords();
        }

        // 2. 转换为 VO，补充流程名称，并应用名称/时间过滤
        List<FlowExecutionHistoryVO> voList = new java.util.ArrayList<>();
        for (FlowExecution exec : all) {
            String name = null;
            if (exec.getFlowDefinitionId() != null && !exec.getFlowDefinitionId().isBlank()) {
                FlowDefinition flowDef = flowDefinitionService.getById(exec.getFlowDefinitionId());
                if (flowDef != null) {
                    name = flowDef.getName();
                }
            }

            // 名称模糊过滤
            if (hasNameFilter && (name == null || !name.toLowerCase().contains(flowName.toLowerCase()))) {
                continue;
            }
            // 时间范围过滤（基于 startTime）
            if (startLt != null && (exec.getStartTime() == null || exec.getStartTime().isBefore(startLt))) {
                continue;
            }
            if (endLt != null && (exec.getStartTime() == null || exec.getStartTime().isAfter(endLt))) {
                continue;
            }

            voList.add(FlowExecutionHistoryVO.builder()
                    .id(exec.getId())
                    .procInstId(exec.getProcInstId())
                    .flowDefinitionId(exec.getFlowDefinitionId())
                    .flowName(name)
                    .status(exec.getStatus())
                    .startTime(exec.getStartTime())
                    .endTime(exec.getEndTime())
                    .duration(exec.getDuration())
                    .createdAt(exec.getCreatedAt())
                    .executor(getExecutorName(exec))
                    .build());
        }

        // 3. 名称/时间过滤场景下进行内存分页
        long total = voList.size();
        if (hasNameFilter || hasTimeFilter) {
            int from = Math.min((int) ((pageNum - 1) * pageSize), voList.size());
            int to = Math.min(from + pageSize, voList.size());
            voList = voList.subList(from, to);
        }

        Page<FlowExecutionHistoryVO> voPage = new Page<>(pageNum, pageSize, total);
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 解析时间字符串（容错：解析失败返回 null）
     */
    private java.time.LocalDateTime parseTime(String text, java.time.format.DateTimeFormatter dtf) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(text, dtf);
        } catch (Exception e) {
            log.warn("时间解析失败: text={}", text, e);
            return null;
        }
    }

    /**
     * 获取执行人名称（当前实现：返回 null 占位，可后续扩展为查询用户表）
     */
    private String getExecutorName(FlowExecution exec) {
        // flow_execution 表当前未存储执行人字段；如需展示，可从 inputParams 的 executorId 反查用户表
        return null;
    }

    /**
     * 查询节点执行详情
     * <p>根据流程实例 ID 和活动 ID 查询单个节点的执行详情，包含解密后的输入/输出快照。
     * 节点轨迹通过 ExecutionTraceCollector 从 Flowable 历史表采集。</p>
     *
     * @param processInstanceId Flowable 流程实例 ID
     * @param activityId        BPMN 活动 ID（节点 ID）
     * @return 节点详情 VO（含状态、耗时、输入/输出快照、错误信息）
     * @throws BusinessException 节点不存在
     */
    @Override
    public NodeDetailVO getNodeDetail(String processInstanceId, String activityId) {
        log.info("查询节点详情: processInstanceId={}, activityId={}", processInstanceId, activityId);

        // 1. 收集节点轨迹
        List<NodeExecutionTraceVO> traces = traceCollector.collectNodeTraces(processInstanceId);

        // 2. 找到目标节点
        NodeExecutionTraceVO trace = traces.stream()
                .filter(t -> activityId.equals(t.getActivityId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(BusinessExceptionEnum.NOT_FOUND,
                        "节点不存在: activityId=" + activityId));

        // 3. 从流程变量获取加密快照并解密
        String snapshotEnter = getDecryptedVariable(processInstanceId, "node_" + activityId + "_input");
        String snapshotExit = getDecryptedVariable(processInstanceId, "node_" + activityId + "_output");

        // 4. 构建响应
        return NodeDetailVO.builder()
                .activityId(trace.getActivityId())
                .activityName(trace.getActivityName())
                .activityType(trace.getActivityType())
                .nodeType(trace.getNodeType())
                .status(trace.getStatus())
                .originalServiceId(trace.getOriginalServiceId())
                .duration(trace.getDuration())
                .errorType(trace.getErrorType())
                .errorMsg(trace.getErrorMsg())
                .startTime(trace.getStartTime())
                .endTime(trace.getEndTime())
                .snapshotEnter(snapshotEnter)
                .snapshotExit(snapshotExit)
                .build();
    }

    /**
     * 从流程变量获取加密值并解密
     */
    private String getDecryptedVariable(String processInstanceId, String variableName) {
        try {
            org.flowable.variable.api.history.HistoricVariableInstance varInstance =
                    historyService.createHistoricVariableInstanceQuery()
                            .processInstanceId(processInstanceId)
                            .variableName(variableName)
                            .singleResult();
            if (varInstance == null || varInstance.getValue() == null) {
                return null;
            }
            String cipherText = varInstance.getValue().toString();
            return aesEncryptUtil.decrypt(cipherText);
        } catch (Exception e) {
            log.warn("解密流程变量失败: variableName={}", variableName, e);
            return null;
        }
    }

    /**
     * 序列化为 JSON 字符串
     */
    private String serializeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 终止流程执行
     * <p>当任意节点执行失败时，前端调用此接口终止后续节点执行。
     * 实现策略：通过 Flowable RuntimeService 删除运行中的流程实例（级联删除子执行与任务），
     * 并同步更新 flow_execution 表状态为 2=失败，记录终止原因。</p>
     *
     * @param processInstanceId Flowable 流程实例 ID
     * @throws BusinessException 执行记录不存在或终止失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateExecution(String processInstanceId) {
        log.info("终止流程执行: processInstanceId={}", processInstanceId);

        // 1. 查询执行记录
        FlowExecution execution = this.lambdaQuery()
                .eq(FlowExecution::getProcInstId, processInstanceId)
                .orderByDesc(FlowExecution::getStartTime)
                .last("LIMIT 1")
                .one();

        if (execution == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "执行记录不存在: " + processInstanceId);
        }

        // 2. 删除运行中的流程实例（cascade=true 级联删除子执行、任务、变量）
        try {
            runtimeService.deleteProcessInstance(processInstanceId, "节点执行失败，前端主动终止流程");
            log.info("Flowable 流程实例已终止: processInstanceId={}", processInstanceId);
        } catch (Exception e) {
            // 流程可能已结束（不存在运行中实例），忽略此异常，仅记录日志
            log.warn("终止 Flowable 流程实例时异常（可能已结束）: processInstanceId={}", processInstanceId, e);
        }

        // 3. 同步更新 flow_execution 表状态
        if (execution.getStatus() == 0) {
            execution.setStatus(2); // 2=失败
            execution.setEndTime(LocalDateTime.now());
            if (execution.getStartTime() != null) {
                long durationMs = java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis();
                execution.setDuration(durationMs);
            }
            execution.setErrorMsg("节点执行失败，流程被终止");
            execution.setUpdatedAt(LocalDateTime.now());
            this.updateById(execution);
            log.info("flow_execution 状态已更新为失败: executionId={}", execution.getId());
        }
    }

    /**
     * 重试流程执行
     * <p>根据原流程实例 ID 定位流程定义，重新启动一个新的流程实例。
     * 原执行记录保持不变，新增一条 flow_execution 记录关联新的流程实例。</p>
     *
     * @param processInstanceId 原流程实例 ID（用于定位流程定义）
     * @return 新的执行启动响应
     * @throws BusinessException 原执行记录不存在或重试失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowExecuteResponseVO retryExecution(String processInstanceId) {
        log.info("重试流程执行: 原processInstanceId={}", processInstanceId);

        // 1. 查询原执行记录，获取流程定义 ID
        FlowExecution originalExecution = this.lambdaQuery()
                .eq(FlowExecution::getProcInstId, processInstanceId)
                .orderByDesc(FlowExecution::getStartTime)
                .last("LIMIT 1")
                .one();

        if (originalExecution == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND,
                    "执行记录不存在，无法重试: " + processInstanceId);
        }

        String flowDefinitionId = originalExecution.getFlowDefinitionId();
        if (flowDefinitionId == null || flowDefinitionId.isBlank()) {
            throw new BusinessException(BusinessExceptionEnum.VALIDATE_ERROR,
                    "原执行记录缺少流程定义ID，无法重试");
        }

        // 2. 复用原输入参数重新执行流程
        FlowExecuteRequestDTO retryDto = new FlowExecuteRequestDTO();
        // 反序列化原输入参数（若有）
        if (originalExecution.getInputParams() != null && !originalExecution.getInputParams().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> inputParams = objectMapper.readValue(
                        originalExecution.getInputParams(), Map.class);
                retryDto.setInputParams(inputParams);
            } catch (JsonProcessingException e) {
                log.warn("反序列化原输入参数失败，使用空参数重试: {}", e.getMessage());
            }
        }

        log.info("重试流程执行: flowDefinitionId={}, 原executionId={}", flowDefinitionId, originalExecution.getId());
        return executeFlow(flowDefinitionId, retryDto);
    }

    /**
     * 清理历史"执行中"脏数据
     * <p>
     * 扫描 flow_execution 表中 status=0（执行中）的记录，逐一核对 Flowable 流程实例实际状态：
     * <ul>
     *   <li>Flowable 中已无运行中实例且历史实例存在 → 流程已结束，按 deleteReason 判定成功(1)/失败(2)</li>
     *   <li>Flowable 中既无运行中实例也无历史实例 → 视为失败(2)，errorMsg 标注"流程实例不存在"</li>
     *   <li>Flowable 中仍有运行中实例 → 真正执行中，跳过</li>
     * </ul>
     * </p>
     *
     * @return 被修复的记录数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupZombieExecutingRecords() {
        log.info("开始清理历史'执行中'脏数据...");

        // 1. 查询所有 status=0 的记录
        List<FlowExecution> zombieRecords = this.lambdaQuery()
                .eq(FlowExecution::getStatus, 0)
                .list();

        if (zombieRecords.isEmpty()) {
            log.info("未发现'执行中'脏数据，无需清理");
            return 0;
        }

        log.info("发现 {} 条'执行中'记录，开始逐一核对 Flowable 实际状态", zombieRecords.size());

        int fixedCount = 0;
        for (FlowExecution execution : zombieRecords) {
            String procInstId = execution.getProcInstId();
            if (procInstId == null || procInstId.isBlank()) {
                // 缺少流程实例 ID，直接标记为失败
                execution.setStatus(2);
                execution.setErrorMsg("流程实例ID为空，无法核对状态");
                execution.setEndTime(LocalDateTime.now());
                execution.setUpdatedAt(LocalDateTime.now());
                this.updateById(execution);
                fixedCount++;
                continue;
            }

            try {
                // 检查 Flowable 运行中实例
                ProcessInstance runningInstance = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(procInstId)
                        .singleResult();

                if (runningInstance != null) {
                    // 真正执行中，跳过
                    log.debug("流程仍在运行，跳过: procInstId={}", procInstId);
                    continue;
                }

                // 查询历史实例
                HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(procInstId)
                        .singleResult();

                int status;
                String errorMsg = null;
                LocalDateTime endTime = null;
                Long duration = null;

                if (historicInstance != null) {
                    // 流程已结束
                    boolean hasError = historicInstance.getDeleteReason() != null;
                    status = hasError ? 2 : 1;
                    errorMsg = hasError ? historicInstance.getDeleteReason() : null;
                    if (historicInstance.getEndTime() != null) {
                        endTime = historicInstance.getEndTime().toInstant()
                                .atZone(ZoneId.systemDefault()).toLocalDateTime();
                        duration = historicInstance.getDurationInMillis();
                    }
                } else {
                    // 历史实例也不存在（可能已清理），标记为失败
                    status = 2;
                    errorMsg = "Flowable 流程实例不存在（可能已被清理）";
                    endTime = LocalDateTime.now();
                }

                execution.setStatus(status);
                execution.setEndTime(endTime);
                execution.setDuration(duration);
                execution.setErrorMsg(errorMsg);
                execution.setUpdatedAt(LocalDateTime.now());
                this.updateById(execution);
                fixedCount++;
                log.info("已修复脏数据: executionId={}, procInstId={}, status={}",
                        execution.getId(), procInstId, status);
            } catch (Exception e) {
                log.warn("核对流程状态失败，跳过: procInstId={}", procInstId, e);
            }
        }

        log.info("清理完成，共修复 {} 条'执行中'脏数据", fixedCount);
        return fixedCount;
    }
}

