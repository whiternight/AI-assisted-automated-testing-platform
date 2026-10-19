package com.example.testplatform.execution.engine;

import com.example.testplatform.apicase.domain.dto.CaseDebugRequestDTO;
import com.example.testplatform.apicase.domain.po.ApiCase;
import com.example.testplatform.apicase.domain.po.ApiCaseHistory;
import com.example.testplatform.execution.domain.po.Task;
import com.example.testplatform.execution.domain.po.TaskNodeInstance;
import com.example.testplatform.apicase.domain.vo.*;
import com.example.testplatform.execution.domain.vo.*;
import com.example.testplatform.execution.engine.assertion.AssertionEngine;
import com.example.testplatform.execution.engine.executor.DubboCaseExecutor;
import com.example.testplatform.execution.engine.executor.HttpCaseExecutor;
import com.example.testplatform.enums.*;
import com.example.testplatform.apicase.mapper.ApiCaseMapper;
import com.example.testplatform.apicase.service.*;
import com.example.testplatform.execution.service.*;
import com.example.testplatform.common.util.AesEncryptUtil;
import com.example.testplatform.common.util.EnumConvertUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用例执行引擎实现类
 *
 * @author TestPlatform
 */
@Slf4j
@Service
public class CaseExecuteEngineImpl implements CaseExecuteEngine {

    private final ITaskService taskService;
    private final ITaskNodeInstanceService taskNodeInstanceService;
    private final IApiCaseHistoryService apiCaseHistoryService;
    private final IExecutionLogService executionLogService;
    private final ICaseActivityService caseActivityService;
    private final AesEncryptUtil aesEncryptUtil;
    private final HttpCaseExecutor httpCaseExecutor;
    private final DubboCaseExecutor dubboCaseExecutor;
    private final AssertionEngine assertionEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiCaseMapper apiCaseMapper;
    private final Executor asyncTaskExecutor;

    public CaseExecuteEngineImpl(ITaskService taskService,
                                 ITaskNodeInstanceService taskNodeInstanceService,
                                 IApiCaseHistoryService apiCaseHistoryService,
                                 IExecutionLogService executionLogService,
                                 ICaseActivityService caseActivityService,
                                 AesEncryptUtil aesEncryptUtil,
                                 HttpCaseExecutor httpCaseExecutor,
                                 DubboCaseExecutor dubboCaseExecutor,
                                 AssertionEngine assertionEngine,
                                 ApiCaseMapper apiCaseMapper,
                                 @Qualifier("asyncTaskExecutor") Executor asyncTaskExecutor) {
        this.taskService = taskService;
        this.taskNodeInstanceService = taskNodeInstanceService;
        this.apiCaseHistoryService = apiCaseHistoryService;
        this.executionLogService = executionLogService;
        this.caseActivityService = caseActivityService;
        this.aesEncryptUtil = aesEncryptUtil;
        this.httpCaseExecutor = httpCaseExecutor;
        this.dubboCaseExecutor = dubboCaseExecutor;
        this.assertionEngine = assertionEngine;
        this.apiCaseMapper = apiCaseMapper;
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    // 环境变量替换正则：${env.xxx}
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{env\\.([^}]+)\\}");

    /**
     * 执行用例（核心入口）
     * <p>按阶段顺序执行，任一阶段失败即终止并分类错误：
     * <ol>
     *   <li>参数组装：合并用例默认配置与用户输入，环境变量替换，AES 加密快照</li>
     *   <li>任务创建：创建 task 和 task_node_instance 记录，状态 RUNNING</li>
     *   <li>协议路由：按 HTTP/Dubbo 分发到对应执行器发送请求</li>
     *   <li>响应处理：解析响应状态码、头、体，构建 ResponseInfoVO</li>
     *   <li>断言执行：协议级成功（statusCode==200）与断言结果联合判定（AND）</li>
     *   <li>失败分类：ASSERTION_FAIL / ENV_ERROR / PLATFORM_ERROR / CASE_ERROR</li>
     *   <li>结果持久化：更新 task、node_instance 状态，写入 api_case_history</li>
     *   <li>活跃度更新：异步更新用例活跃度</li>
     * </ol>
     * </p>
     *
     * @param caseId     用例 ID
     * @param requestDTO 执行请求（含用户输入参数、环境、超时）
     * @param executorId 执行人 ID
     * @return 执行响应（含状态、响应信息、错误类型、taskId、nodeInstanceId）
     */
    @Override
    public CaseDebugResponseVO execute(String caseId, CaseDebugRequestDTO requestDTO, Long executorId) {
        LocalDateTime startTime = LocalDateTime.now();
        ExecuteContext context = ExecuteContext.builder()
                .caseId(caseId)
                .executorId(executorId)
                .env(requestDTO.getEnv())
                .startTime(startTime)
                .build();

        try {
            // 1. 参数组装阶段
            ApiCase apiCase = assembleParameters(context, requestDTO);

            // 2. 任务创建阶段
            createTaskAndNode(context, apiCase);

            // 3. 协议路由和请求发送阶段
            executeProtocol(context, apiCase, requestDTO);

            // 4. 响应处理阶段
            processResponse(context);

            // 5. 断言执行阶段
            executeAssertions(context, apiCase);

            // 6. 失败分类阶段
            classifyFailure(context);

            // 7. 结果持久化阶段
            persistResults(context, apiCase);

            // 8. 活跃度更新和结果返回
            updateActivityAsync(context);
            return buildResponse(context);

        } catch (Exception e) {
            log.error("用例执行异常: caseId={}", caseId, e);
            handleExecutionError(context, e);
            return buildErrorResponse(context, e);
        }
    }

    /**
     * 1. 参数组装阶段
     */
    private ApiCase assembleParameters(ExecuteContext context, CaseDebugRequestDTO requestDTO) {
        // 读取用例定义
        ApiCase apiCase = apiCaseMapper.selectById(context.getCaseId());
        if (apiCase == null) {
            throw new IllegalArgumentException("用例不存在: " + context.getCaseId());
        }

        // 转换枚举字段
        apiCase.setProtocolTypeEnum(EnumConvertUtil.toProtocolTypeEnum(apiCase.getProtocolType()));

        try {
            // 解析用例默认配置
            Map<String, String> caseHeaders = parseJsonToMap(apiCase.getRequestHeaders());
            Map<String, Object> caseParams = parseJsonToMapObject(apiCase.getRequestParams());
            Object caseBody = parseJson(apiCase.getRequestBody());

            // 合并 headers：用户输入覆盖用例默认（同名Key覆盖）
            Map<String, String> mergedHeaders = mergeMaps(caseHeaders, requestDTO.getHeaders());

            // 合并 params：用户输入覆盖用例默认（同名Key覆盖）
            Map<String, Object> mergedParams = mergeMaps(caseParams, requestDTO.getParams());

            // 覆盖 body：用户输入完全覆盖用例默认
            Object finalBody = requestDTO.getBody() != null ? requestDTO.getBody() : caseBody;

            // 环境变量替换
            String address = replaceEnvVariables(apiCase.getAddress(), context.getEnv());
            apiCase.setAddress(address);

            // 构建请求快照
            Map<String, Object> requestSnapshot = new LinkedHashMap<>();
            requestSnapshot.put("address", address);
            requestSnapshot.put("protocolType", apiCase.getProtocolType());
            requestSnapshot.put("requestType", apiCase.getRequestType());
            requestSnapshot.put("headers", mergedHeaders);
            requestSnapshot.put("params", mergedParams);
            requestSnapshot.put("body", finalBody);
            requestSnapshot.put("timeout", requestDTO.getTimeout() != null ? requestDTO.getTimeout() : apiCase.getTimeout());

            // JSON序列化后 AES-256 加密
            String snapshotJson = objectMapper.writeValueAsString(requestSnapshot);
            String encryptedSnapshot = aesEncryptUtil.encrypt(snapshotJson);
            context.setRequestSnapshot(encryptedSnapshot);

            // 存储合并后的请求参数到 context，供 executeProtocol 使用
            context.setMergedHeaders(mergedHeaders);
            context.setMergedParams(mergedParams);
            context.setMergedBody(finalBody);

            log.info("参数组装完成: caseId={}, address={}", context.getCaseId(), address);
            return apiCase;

        } catch (Exception e) {
            log.error("参数组装失败: caseId={}", context.getCaseId(), e);
            throw new RuntimeException("参数组装失败", e);
        }
    }

    /**
     * 2. 任务创建阶段
     */
    private void createTaskAndNode(ExecuteContext context, ApiCase apiCase) {
        try {
            // 生成 task_no（格式：T-yyyyMMdd-{6位序号}）
            String taskNo = generateTaskNo();

            // 创建 task 记录
            Task task = new Task();
            task.setTaskNo(taskNo);
            task.setApplicationId(apiCase.getApplicationId());
            task.setExecutorId(context.getExecutorId());
            task.setExecuteMode(EnumConvertUtil.fromExecuteModeEnum(ExecuteModeEnum.SYNC));
            task.setStatusEnum(TaskStatusEnum.RUNNING);
            task.setStatus(EnumConvertUtil.fromTaskStatusEnum(TaskStatusEnum.RUNNING));
            task.setTotalCases(1);
            task.setSuccessCount(0);
            task.setFailCount(0);
            task.setBlockedCount(0);
            task.setSkipCount(0);
            task.setStartTime(context.getStartTime());
            task.setEnv(context.getEnv());
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            task.setDeleted(0);

            taskService.save(task);
            context.setTaskId(task.getId());

            // 创建 task_node_instance 记录
            TaskNodeInstance nodeInstance = new TaskNodeInstance();
            nodeInstance.setTaskId(task.getId());
            nodeInstance.setCaseId(context.getCaseId());
            nodeInstance.setStatusEnum(NodeInstanceStatusEnum.RUNNING);
            nodeInstance.setStatus(EnumConvertUtil.fromNodeInstanceStatusEnum(NodeInstanceStatusEnum.RUNNING));
            nodeInstance.setInputSnapshot(context.getRequestSnapshot());
            nodeInstance.setStartTime(context.getStartTime());
            nodeInstance.setRetryCount(0);
            nodeInstance.setCreatedAt(LocalDateTime.now());
            nodeInstance.setUpdatedAt(LocalDateTime.now());
            nodeInstance.setDeleted(0);

            taskNodeInstanceService.save(nodeInstance);
            context.setNodeInstanceId(nodeInstance.getId());

            log.info("任务创建成功: taskId={}, taskNo={}, nodeInstanceId={}",
                    task.getId(), taskNo, nodeInstance.getId());

        } catch (Exception e) {
            log.error("任务创建失败: caseId={}", context.getCaseId(), e);
            throw new RuntimeException("任务创建失败", e);
        }
    }

    /**
     * 3. 协议路由和请求发送阶段
     */
    private void executeProtocol(ExecuteContext context, ApiCase apiCase, CaseDebugRequestDTO requestDTO) {
        ProtocolTypeEnum protocolType = apiCase.getProtocolTypeEnum();
        if (protocolType == null) {
            throw new IllegalArgumentException("协议类型不支持: " + apiCase.getProtocolType());
        }

        // 使用 assembleParameters 阶段合并后的请求参数（用户覆盖 + 环境变量替换）
        Map<String, String> headers = context.getMergedHeaders() != null
                ? context.getMergedHeaders() : parseJsonToMap(apiCase.getRequestHeaders());
        Map<String, Object> params = context.getMergedParams() != null
                ? context.getMergedParams() : parseJsonToMapObject(apiCase.getRequestParams());
        Object body = context.getMergedBody() != null
                ? context.getMergedBody() : parseJson(apiCase.getRequestBody());
        
        // 确定超时时间
        int timeout = requestDTO.getTimeout() != null ? requestDTO.getTimeout() : 
                      (apiCase.getTimeout() != null ? apiCase.getTimeout() : 30000);

        switch (protocolType) {
            case HTTP:
                executeHttpProtocol(context, apiCase, headers, params, body, timeout);
                break;
            case DUBBO:
                executeDubboProtocol(context, apiCase, headers, params, body, timeout);
                break;
            default:
                throw new IllegalArgumentException("未实现的协议类型: " + protocolType);
        }
    }

    /**
     * 执行 HTTP 协议
     */
    private void executeHttpProtocol(ExecuteContext context, ApiCase apiCase,
                                     Map<String, String> headers,
                                     Map<String, Object> params,
                                     Object body,
                                     int timeout) {
        try {
            // 调用 HTTP 执行器
            HttpResponse httpResponse = httpCaseExecutor.execute(
                    context, apiCase, headers, params, body, timeout);

            // 保存响应信息到 context
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("statusCode", httpResponse.getStatusCode());
            responseData.put("headers", httpResponse.getHeaders());
            responseData.put("body", httpResponse.getBody());
            responseData.put("size", httpResponse.getSize());
            responseData.put("duration", httpResponse.getDuration());

            context.setResponseSnapshot(objectMapper.writeValueAsString(responseData));
            log.info("HTTP 请求成功: statusCode={}, duration={}ms",
                    httpResponse.getStatusCode(), httpResponse.getDuration());

        } catch (Exception e) {
            log.error("HTTP 请求失败: caseId={}", context.getCaseId(), e);
            throw new RuntimeException("HTTP 请求失败", e);
        }
    }

    /**
     * 执行 Dubbo 协议
     */
    private void executeDubboProtocol(ExecuteContext context, ApiCase apiCase,
                                      Map<String, String> headers,
                                      Map<String, Object> params,
                                      Object body,
                                      int timeout) {
        try {
            // 调用 Dubbo 执行器
            DubboResponse dubboResponse = dubboCaseExecutor.execute(
                    context, apiCase, headers, params, body, timeout);

            // 保存响应信息到 context
            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("statusCode", dubboResponse.getSuccess() != null && dubboResponse.getSuccess() ? 200 : 500);
            responseData.put("headers", new HashMap<>());
            responseData.put("body", dubboResponse.getResult());
            responseData.put("size", 0L);
            responseData.put("duration", dubboResponse.getDuration());

            context.setResponseSnapshot(objectMapper.writeValueAsString(responseData));
            log.info("Dubbo 请求成功: success={}, duration={}ms",
                    dubboResponse.getSuccess(), dubboResponse.getDuration());

        } catch (Exception e) {
            log.error("Dubbo 请求失败: caseId={}", context.getCaseId(), e);
            throw new RuntimeException("Dubbo 请求失败", e);
        }
    }

    /**
     * 4. 响应处理阶段
     */
    @SuppressWarnings("unchecked")
    private void processResponse(ExecuteContext context) {
        try {
            if (context.getResponseSnapshot() == null) {
                return;
            }

            Map<String, Object> responseData = objectMapper.readValue(
                    context.getResponseSnapshot(), Map.class);

            ResponseInfoVO responseInfo = ResponseInfoVO.builder()
                    .statusCode((Integer) responseData.get("statusCode"))
                    .headers((Map<String, String>) responseData.get("headers"))
                    .body(responseData.get("body"))
                    .format("JSON")
                    .size(((Number) responseData.get("size")).longValue())
                    .duration(responseData.get("duration") instanceof Number
                            ? ((Number) responseData.get("duration")).longValue() : null)
                    .build();

            // 将响应信息存储到context中，供后续使用
            context.setResponseSnapshot(objectMapper.writeValueAsString(responseInfo));
            log.info("响应处理完成: statusCode={}, size={}",
                    responseInfo.getStatusCode(), responseInfo.getSize());

        } catch (Exception e) {
            log.error("响应处理失败: caseId={}", context.getCaseId(), e);
            throw new RuntimeException("响应处理失败", e);
        }
    }

    /**
     * 5. 断言执行阶段
     */
    @SuppressWarnings("unchecked")
    private void executeAssertions(ExecuteContext context, ApiCase apiCase) {
        try {
            // 1. 协议级成功判定：HTTP 以 statusCode==200 判定成功，Dubbo 也映射为 200/500
            Map<String, Object> responseData = objectMapper.readValue(
                    context.getResponseSnapshot(), Map.class);
            Integer statusCode = responseData.get("statusCode") instanceof Number
                    ? ((Number) responseData.get("statusCode")).intValue() : null;
            boolean protocolSuccess = statusCode != null && statusCode == 200;

            // 2. 无断言配置时，仅以协议级结果判定
            if (apiCase.getResponseAssertions() == null || apiCase.getResponseAssertions().isEmpty()) {
                context.setAssertionResults(new ArrayList<>());
                context.setSuccess(protocolSuccess);
                return;
            }

            // 3. 提取响应头
            Map<String, String> headers = new HashMap<>();
            if (responseData.get("headers") instanceof Map) {
                headers = (Map<String, String>) responseData.get("headers");
            }

            // 提取耗时
            long duration = 0L;
            if (responseData.get("duration") instanceof Number) {
                duration = ((Number) responseData.get("duration")).longValue();
            }

            // 4. 调用断言引擎执行断言
            List<AssertionResultVO> assertionResults = assertionEngine.executeAssertions(
                    apiCase.getResponseAssertions(),
                    responseData,
                    headers,
                    duration);

            // 5. 断言结果 + 协议级结果 联合判定（AND 关系）
            boolean allPassed = assertionResults.stream()
                    .allMatch(AssertionResultVO::getPassed);
            boolean finalSuccess = protocolSuccess && allPassed;

            context.setAssertionResults(assertionResults);
            context.setSuccess(finalSuccess);

            long passedCount = assertionResults.stream()
                    .filter(AssertionResultVO::getPassed).count();
            log.info("断言执行完成: total={}, passed={}, failed={}, protocolSuccess={}, finalSuccess={}",
                    assertionResults.size(), passedCount, assertionResults.size() - passedCount,
                    protocolSuccess, finalSuccess);

        } catch (Exception e) {
            log.error("断言执行失败: caseId={}", context.getCaseId(), e);
            context.setAssertionResults(new ArrayList<>());
            context.setSuccess(false);
            context.setErrorType(ErrorTypeEnum.PLATFORM_ERROR);
            context.setErrorMsg("断言执行异常: " + e.getMessage());
        }
    }


    /**
     * 6. 失败分类阶段
     * 提取具体错误信息，而不是笼统的“断言失败”
     */
    private void classifyFailure(ExecuteContext context) {
        if (context.getSuccess()) {
            return;
        }

        // 如果已经有错误类型，直接返回
        if (context.getErrorType() != null) {
            return;
        }

        // 判断失败类型
        if (context.getAssertionResults() != null
                && !context.getAssertionResults().isEmpty()) {
            // 断言失败：尝试从响应体中提取 message 字段作为具体错误信息
            String responseMessage = extractResponseMessage(context);
            if (responseMessage != null) {
                context.setErrorType(ErrorTypeEnum.ASSERTION_FAIL);
                context.setErrorMsg(responseMessage);
            } else {
                // 回退：拼接所有失败断言的具体详情
                String failDetails = context.getAssertionResults().stream()
                        .filter(a -> !a.getPassed())
                        .map(a -> {
                            String detail = a.getDescription() != null ? a.getDescription() : a.getType();
                            return detail + "：期望[" + a.getExpected() + "]，实际[" + a.getActual() + "]"
                                    + (a.getFailReason() != null ? "（" + a.getFailReason() + "）" : "");
                        })
                        .collect(java.util.stream.Collectors.joining("；"));
                context.setErrorType(ErrorTypeEnum.ASSERTION_FAIL);
                context.setErrorMsg(failDetails.isEmpty() ? "断言失败" : failDetails);
            }
        } else if (context.getErrorMsg() != null) {
            // 根据错误信息判断
            String errorMsg = context.getErrorMsg().toLowerCase();
            if (errorMsg.contains("timeout") || errorMsg.contains("dns")
                    || errorMsg.contains("connection") || errorMsg.contains("interrupt")) {
                context.setErrorType(ErrorTypeEnum.ENV_ERROR);
            } else if (errorMsg.contains("jsonpath") || errorMsg.contains("parse")
                    || errorMsg.contains("replace")) {
                context.setErrorType(ErrorTypeEnum.PLATFORM_ERROR);
            } else {
                context.setErrorType(ErrorTypeEnum.ENV_ERROR);
            }
        } else {
            context.setErrorType(ErrorTypeEnum.ASSERTION_FAIL);
        }
    }

    /**
     * 7. 结果持久化阶段
     * <p>
     * 同步操作（影响响应返回，必须等待）：
     *   - 更新 nodeInstance 状态（前端需要查询）
     *   - 更新 task 状态（前端需要查询）
     * 异步操作（不影响响应，后台执行）：
     *   - 插入 api_case_history
     *   - 回写 api_case 冗余字段
     *   - 插入 execution_log
     */
    private void persistResults(ExecuteContext context, ApiCase apiCase) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(context.getStartTime(), endTime).toMillis();
            context.setDuration(duration);

            // 对响应快照进行加密存储
            String encryptedResponseSnapshot = null;
            if (context.getResponseSnapshot() != null) {
                try {
                    encryptedResponseSnapshot = aesEncryptUtil.encrypt(context.getResponseSnapshot());
                } catch (Exception e) {
                    log.error("响应快照加密失败: nodeInstanceId={}", context.getNodeInstanceId(), e);
                    encryptedResponseSnapshot = context.getResponseSnapshot();
                }
            }

            // === 同步：直接构造 nodeInstance 更新（省掉 getById 查询） ===
            TaskNodeInstance nodeUpdate = new TaskNodeInstance();
            nodeUpdate.setId(context.getNodeInstanceId());
            nodeUpdate.setStatusEnum(context.getSuccess() ?
                    NodeInstanceStatusEnum.COMPLETED : NodeInstanceStatusEnum.FAILED);
            nodeUpdate.setStatus(EnumConvertUtil.fromNodeInstanceStatusEnum(
                    context.getSuccess() ? NodeInstanceStatusEnum.COMPLETED : NodeInstanceStatusEnum.FAILED));
            nodeUpdate.setOutputSnapshot(encryptedResponseSnapshot);
            nodeUpdate.setErrorTypeEnum(context.getErrorType());
            nodeUpdate.setErrorType(context.getErrorType() != null ?
                    EnumConvertUtil.fromErrorTypeEnum(context.getErrorType()) : null);
            nodeUpdate.setErrorMsg(context.getErrorMsg());
            nodeUpdate.setEndTime(endTime);
            nodeUpdate.setUpdatedAt(LocalDateTime.now());
            taskNodeInstanceService.updateById(nodeUpdate);

            // === 同步：直接构造 task 更新（省掉 getById 查询） ===
            Task taskUpdate = new Task();
            taskUpdate.setId(context.getTaskId());
            taskUpdate.setStatusEnum(context.getSuccess() ?
                    TaskStatusEnum.SUCCESS : TaskStatusEnum.FAILED);
            taskUpdate.setStatus(EnumConvertUtil.fromTaskStatusEnum(
                    context.getSuccess() ? TaskStatusEnum.SUCCESS : TaskStatusEnum.FAILED));
            if (context.getSuccess()) {
                taskUpdate.setSuccessCount(1);
            } else {
                taskUpdate.setFailCount(1);
            }
            taskUpdate.setEndTime(endTime);
            taskUpdate.setUpdatedAt(LocalDateTime.now());
            taskService.updateById(taskUpdate);

            // === 异步：history 插入 + api_case 回写 + execution_log ===
            final String responseSnapshot = context.getResponseSnapshot();
            final String requestSnapshot = context.getRequestSnapshot();
            final Boolean success = context.getSuccess();
            final ErrorTypeEnum errorType = context.getErrorType();
            final String caseId = context.getCaseId();
            final String taskId = context.getTaskId();
            final Long executorId = context.getExecutorId();
            final String env = context.getEnv();

            CompletableFuture.runAsync(() -> {
                try {
                    // 插入 api_case_history
                    ApiCaseHistory history = new ApiCaseHistory();
                    history.setCaseId(caseId);
                    history.setHistoryTypeEnum(HistoryTypeEnum.SINGLE_API);
                    history.setHistoryType(EnumConvertUtil.fromHistoryTypeEnum(HistoryTypeEnum.SINGLE_API));
                    history.setRequestSnapshot(requestSnapshot);
                    history.setResponseSnapshot(responseSnapshot);
                    history.setResultBoolean(success);
                    history.setResult(EnumConvertUtil.fromResultBoolean(success));
                    history.setExecutorId(executorId);
                    history.setEnv(env);
                    history.setErrorTypeEnum(errorType);
                    history.setErrorType(errorType != null ?
                            EnumConvertUtil.fromErrorTypeEnum(errorType) : null);
                    history.setTaskId(taskId);
                    history.setCreatedAt(LocalDateTime.now());
                    history.setUpdatedAt(LocalDateTime.now());
                    history.setDeleted(0);
                    apiCaseHistoryService.save(history);

                    // 回写执行结果到 api_case 表
                    try {
                        String responseBodyStr = null;
                        if (responseSnapshot != null) {
                            Map<String, Object> respMap = parseJsonToMapObject(responseSnapshot);
                            Object body = respMap.get("body");
                            if (body != null) {
                                responseBodyStr = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
                            }
                        }
                        apiCase.setResponseBody(responseBodyStr);
                        apiCase.setIsSuccessful(success != null && success ? 1 : 0);
                        apiCaseMapper.updateById(apiCase);
                    } catch (Exception ex) {
                        log.warn("回写执行结果到api_case失败: caseId={}", caseId, ex);
                    }

                    // 插入 execution_log
                    insertExecutionLogAsync(context, duration);

                    log.info("异步持久化完成: caseId={}, taskId={}", caseId, taskId);
                } catch (Exception e) {
                    log.error("异步持久化失败: caseId={}", caseId, e);
                }
            }, asyncTaskExecutor);

            log.info("同步持久化完成: taskId={}, success={}", context.getTaskId(), context.getSuccess());

        } catch (Exception e) {
            log.error("结果持久化失败: caseId={}", context.getCaseId(), e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 8. 活跃度更新阶段（异步）
     */
    private void updateActivityAsync(ExecuteContext context) {
        CompletableFuture.runAsync(() -> {
            try {
                caseActivityService.recordDebugActivity(context.getCaseId());
                log.info("活跃度更新完成: caseId={}", context.getCaseId());
            } catch (Exception e) {
                log.error("活跃度更新失败: caseId={}", context.getCaseId(), e);
            }
        }, asyncTaskExecutor);
    }

    /**
     * 构建响应结果
     */
    private CaseDebugResponseVO buildResponse(ExecuteContext context) {
        try {
            ResponseInfoVO responseInfo = null;
            if (context.getResponseSnapshot() != null) {
                responseInfo = objectMapper.readValue(
                        context.getResponseSnapshot(), ResponseInfoVO.class);
            }

            return CaseDebugResponseVO.builder()
                    .taskId(context.getTaskId())
                    .nodeInstanceId(context.getNodeInstanceId())
                    .status(context.getSuccess() ? "SUCCESS" : "FAILED")
                    .result(context.getPassed() != null ? context.getPassed() : context.getSuccess())
                    .duration(context.getDuration())
                    .response(responseInfo)
                    .assertions(context.getAssertionResults())
                    .errorType(context.getErrorType() != null ?
                            context.getErrorType().name() : null)
                    .errorMsg(context.getErrorMsg())
                    .build();

        } catch (Exception e) {
            log.error("构建响应失败", e);
            return CaseDebugResponseVO.builder()
                    .taskId(context.getTaskId())
                    .nodeInstanceId(context.getNodeInstanceId())
                    .status("FAILED")
                    .result(false)
                    .duration(context.getDuration())
                    .errorType("PLATFORM_ERROR")
                    .errorMsg("构建响应失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 处理执行异常
     */
    private void handleExecutionError(ExecuteContext context, Exception e) {
        context.setSuccess(false);
        context.setErrorType(ErrorTypeEnum.PLATFORM_ERROR);
        String exceptionMsg = formatExceptionMessage(e);
        context.setErrorMsg(context.getErrorMsg() != null
                ? context.getErrorMsg() + "；" + exceptionMsg
                : exceptionMsg);
        context.setDuration(java.time.Duration.between(
                context.getStartTime(), LocalDateTime.now()).toMillis());

        // 尝试持久化错误信息
        try {
            if (context.getNodeInstanceId() != null) {
                TaskNodeInstance nodeInstance = taskNodeInstanceService.getById(context.getNodeInstanceId());
                if (nodeInstance != null) {
                    nodeInstance.setStatusEnum(NodeInstanceStatusEnum.FAILED);
                    nodeInstance.setStatus(EnumConvertUtil.fromNodeInstanceStatusEnum(NodeInstanceStatusEnum.FAILED));
                    nodeInstance.setErrorTypeEnum(ErrorTypeEnum.PLATFORM_ERROR);
                    nodeInstance.setErrorType(EnumConvertUtil.fromErrorTypeEnum(ErrorTypeEnum.PLATFORM_ERROR));
                    nodeInstance.setErrorMsg(context.getErrorMsg());
                    nodeInstance.setEndTime(LocalDateTime.now());
                    taskNodeInstanceService.updateById(nodeInstance);
                }
            }
        } catch (Exception ex) {
            log.error("错误持久化失败", ex);
        }
    }

    /**
     * 构建错误响应
     */
    private CaseDebugResponseVO buildErrorResponse(ExecuteContext context, Exception e) {
        String errorMsg = context.getErrorMsg() != null
                ? context.getErrorMsg()
                : formatExceptionMessage(e);
        return CaseDebugResponseVO.builder()
                .taskId(context.getTaskId())
                .nodeInstanceId(context.getNodeInstanceId())
                .status("FAILED")
                .result(false)
                .duration(context.getDuration())
                .errorType(context.getErrorType() != null ?
                        context.getErrorType().name() : "PLATFORM_ERROR")
                .errorMsg(errorMsg)
                .build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化异常信息：确保 NullPointerException 等 getMessage() 为 null 的异常也能返回有意义的错误描述
     */
    private String formatExceptionMessage(Exception e) {
        String className = e.getClass().getSimpleName();
        String message = e.getMessage();
        if (message != null && !message.isEmpty()) {
            return className + ": " + message;
        }
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            return className + " at " + stackTrace[0].toString();
        }
        return className;
    }

    /**
     * 从响应体中提取 message 字段作为具体错误信息
     * 当断言失败时，尝试从 API 响应体的 body.message 字段获取更具体的错误描述
     */
    private String extractResponseMessage(ExecuteContext context) {
        try {
            if (context.getResponseSnapshot() == null) return null;
            Map<String, Object> responseData = parseJsonToMapObject(context.getResponseSnapshot());
            Object body = responseData.get("body");
            if (body == null) return null;

            Map<String, Object> bodyMap = null;
            if (body instanceof Map) {
                bodyMap = (Map<String, Object>) body;
            } else if (body instanceof String) {
                bodyMap = parseJsonToMapObject((String) body);
            }

            if (bodyMap != null && bodyMap.containsKey("message")) {
                Object msg = bodyMap.get("message");
                if (msg != null && !msg.toString().isEmpty()) {
                    return msg.toString();
                }
            }
        } catch (Exception e) {
            log.debug("提取响应体message字段失败", e);
        }
        return null;
    }

    /**
     * 判断响应是否为 HTTP 2xx 成功状态码
     */
    private boolean isHttpStatusSuccess(ExecuteContext context) {
        Integer statusCode = extractStatusCode(context);
        return statusCode != null && statusCode == 200;
    }

    /**
     * 从响应快照中提取 HTTP 状态码
     */
    private Integer extractStatusCode(ExecuteContext context) {
        try {
            if (context.getResponseSnapshot() == null) return null;
            Map<String, Object> responseData = parseJsonToMapObject(context.getResponseSnapshot());
            Object sc = responseData.get("statusCode");
            if (sc instanceof Number) {
                return ((Number) sc).intValue();
            }
        } catch (Exception e) {
            log.debug("提取状态码失败", e);
        }
        return null;
    }

    @Override
    public CaseDebugResponseVO executeInBatch(String caseId, CaseDebugRequestDTO requestDTO, Long executorId, String taskId) {
        LocalDateTime startTime = LocalDateTime.now();
        ExecuteContext context = ExecuteContext.builder()
                .caseId(caseId)
                .executorId(executorId)
                .env(requestDTO.getEnv())
                .startTime(startTime)
                .build();
        context.setTaskId(taskId);

        try {
            // 1. 参数组装
            ApiCase apiCase = assembleParameters(context, requestDTO);

            // 2. 仅创建节点实例（不创建 Task）
            createNodeForBatch(context);

            // 3. 协议路由和请求发送
            executeProtocol(context, apiCase, requestDTO);

            // 4. 响应处理（执行到这里说明协议级执行成功，请求已发出且收到了响应）
            processResponse(context);

            // 5. 断言执行（仅用于提取错误详情，不影响成功/失败判定）
            executeAssertions(context, apiCase);

            // 6. 失败分类（提取具体错误信息）
            classifyFailure(context);

            // ===== 核心逻辑：自动化场景下，历史展示与通过率分离 =====
            // success（历史展示）：只要有响应体即为 true，显示"成功"
            // passed（通过率计算）：仅 HTTP 2xx 为 true
            boolean hasResponse = context.getResponseSnapshot() != null;
            if (hasResponse) {
                context.setSuccess(true); // 有响应 → 历史显示"成功"
                context.setPassed(isHttpStatusSuccess(context)); // 通过率按 2xx 判定

                if (Boolean.TRUE.equals(context.getPassed())) {
                    // 200：清除错误信息
                    context.setErrorType(null);
                    context.setErrorMsg(null);
                } else {
                    // 4xx/5xx：保留/提取具体错误信息，标记未通过
                    String detailedMessage = extractResponseMessage(context);
                    if (detailedMessage != null) {
                        context.setErrorMsg(detailedMessage);
                    } else if (context.getErrorMsg() == null) {
                        Integer statusCode = extractStatusCode(context);
                        context.setErrorMsg(statusCode != null ? "HTTP " + statusCode + " 响应" : "HTTP 非 2xx 响应");
                    }
                    if (context.getErrorType() == null) {
                        context.setErrorType(ErrorTypeEnum.ENV_ERROR);
                    }
                }
            } else {
                context.setPassed(false);
            }

            // 7. 结果持久化（仅更新 NodeInstance 和 CaseHistory，不更新 Task）
            persistBatchNodeResult(context, apiCase);

            // 8. 活跃度更新
            updateActivityAsync(context);
            return buildResponse(context);

        } catch (Exception e) {
            log.error("批量用例执行异常: caseId={}", caseId, e);
            handleBatchExecutionError(context, e);
            return buildErrorResponse(context, e);
        }
    }

    /**
     * 批量模式下仅创建节点实例（不创建 Task）
     */
    private void createNodeForBatch(ExecuteContext context) {
        TaskNodeInstance nodeInstance = new TaskNodeInstance();
        nodeInstance.setTaskId(context.getTaskId());
        nodeInstance.setCaseId(context.getCaseId());
        nodeInstance.setStatusEnum(NodeInstanceStatusEnum.RUNNING);
        nodeInstance.setStatus(EnumConvertUtil.fromNodeInstanceStatusEnum(NodeInstanceStatusEnum.RUNNING));
        nodeInstance.setInputSnapshot(context.getRequestSnapshot());
        nodeInstance.setStartTime(context.getStartTime());
        nodeInstance.setRetryCount(0);
        nodeInstance.setCreatedAt(LocalDateTime.now());
        nodeInstance.setUpdatedAt(LocalDateTime.now());
        nodeInstance.setDeleted(0);

        taskNodeInstanceService.save(nodeInstance);
        context.setNodeInstanceId(nodeInstance.getId());

        log.info("批量模式节点创建成功: taskId={}, caseId={}, nodeInstanceId={}",
                context.getTaskId(), context.getCaseId(), nodeInstance.getId());
    }

    /**
     * 批量模式下的结果持久化（仅更新 NodeInstance 和 CaseHistory，不更新 Task）
     * <p>
     * 同步：直接构造 nodeInstance 更新（省掉 getById 查询）
     * 异步：history 插入 + api_case 回写 + execution_log
     */
    private void persistBatchNodeResult(ExecuteContext context, ApiCase apiCase) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(context.getStartTime(), endTime).toMillis();
            context.setDuration(duration);

            // 对响应快照进行加密存储
            String encryptedResponseSnapshot = null;
            if (context.getResponseSnapshot() != null) {
                try {
                    encryptedResponseSnapshot = aesEncryptUtil.encrypt(context.getResponseSnapshot());
                } catch (Exception e) {
                    log.error("响应快照加密失败: nodeInstanceId={}", context.getNodeInstanceId(), e);
                    encryptedResponseSnapshot = context.getResponseSnapshot();
                }
            }

            // === 同步：直接构造 nodeInstance 更新（省掉 getById 查询） ===
            TaskNodeInstance nodeUpdate = new TaskNodeInstance();
            nodeUpdate.setId(context.getNodeInstanceId());
            nodeUpdate.setStatusEnum(context.getSuccess() ?
                    NodeInstanceStatusEnum.COMPLETED : NodeInstanceStatusEnum.FAILED);
            nodeUpdate.setStatus(EnumConvertUtil.fromNodeInstanceStatusEnum(
                    context.getSuccess() ? NodeInstanceStatusEnum.COMPLETED : NodeInstanceStatusEnum.FAILED));
            nodeUpdate.setOutputSnapshot(encryptedResponseSnapshot);
            nodeUpdate.setErrorTypeEnum(context.getErrorType());
            nodeUpdate.setErrorType(context.getErrorType() != null ?
                    EnumConvertUtil.fromErrorTypeEnum(context.getErrorType()) : null);
            nodeUpdate.setErrorMsg(context.getErrorMsg());
            nodeUpdate.setEndTime(endTime);
            nodeUpdate.setPassed(context.getPassed() != null && context.getPassed() ? 1 : 0);
            nodeUpdate.setUpdatedAt(LocalDateTime.now());
            taskNodeInstanceService.updateById(nodeUpdate);

            // === 异步：history 插入 + api_case 回写 + execution_log ===
            final String responseSnapshot = context.getResponseSnapshot();
            final String requestSnapshot = context.getRequestSnapshot();
            final Boolean success = context.getSuccess();
            final ErrorTypeEnum errorType = context.getErrorType();
            final String caseId = context.getCaseId();
            final String taskId = context.getTaskId();
            final Long executorId = context.getExecutorId();
            final String env = context.getEnv();

            CompletableFuture.runAsync(() -> {
                try {
                    // 插入 api_case_history
                    ApiCaseHistory history = new ApiCaseHistory();
                    history.setCaseId(caseId);
                    history.setHistoryTypeEnum(HistoryTypeEnum.AUTOMATION);
                    history.setHistoryType(EnumConvertUtil.fromHistoryTypeEnum(HistoryTypeEnum.AUTOMATION));
                    history.setRequestSnapshot(requestSnapshot);
                    history.setResponseSnapshot(responseSnapshot);
                    history.setResultBoolean(success);
                    history.setResult(EnumConvertUtil.fromResultBoolean(success));
                    history.setExecutorId(executorId);
                    history.setEnv(env);
                    history.setErrorTypeEnum(errorType);
                    history.setErrorType(errorType != null ?
                            EnumConvertUtil.fromErrorTypeEnum(errorType) : null);
                    history.setTaskId(taskId);
                    history.setCreatedAt(LocalDateTime.now());
                    history.setUpdatedAt(LocalDateTime.now());
                    history.setDeleted(0);
                    apiCaseHistoryService.save(history);

                    // 回写执行结果到 api_case 表
                    try {
                        String responseBodyStr = null;
                        if (responseSnapshot != null) {
                            Map<String, Object> respMap = parseJsonToMapObject(responseSnapshot);
                            Object body = respMap.get("body");
                            if (body != null) {
                                responseBodyStr = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
                            }
                        }
                        apiCase.setResponseBody(responseBodyStr);
                        apiCase.setIsSuccessful(success != null && success ? 1 : 0);
                        apiCaseMapper.updateById(apiCase);
                    } catch (Exception ex) {
                        log.warn("回写执行结果到api_case失败: caseId={}", caseId, ex);
                    }

                    // 插入 execution_log
                    insertExecutionLogAsync(context, duration);

                    log.info("批量异步持久化完成: caseId={}, nodeInstanceId={}", caseId, context.getNodeInstanceId());
                } catch (Exception e) {
                    log.error("批量异步持久化失败: caseId={}", caseId, e);
                }
            }, asyncTaskExecutor);

            log.info("批量同步持久化完成: nodeInstanceId={}, success={}", context.getNodeInstanceId(), context.getSuccess());
        } catch (Exception e) {
            log.error("批量模式结果持久化失败: caseId={}", context.getCaseId(), e);
        }
    }

    /**
     * 批量模式下执行异常处理
     */
    private void handleBatchExecutionError(ExecuteContext context, Exception e) {
        context.setSuccess(false);
        context.setPassed(false);
        String exceptionMsg = formatExceptionMessage(e);
        context.setErrorMsg(context.getErrorMsg() != null
                ? context.getErrorMsg() + "；" + exceptionMsg
                : exceptionMsg);
        context.setDuration(java.time.Duration.between(context.getStartTime(), LocalDateTime.now()).toMillis());

        // 根据异常信息动态分类错误类型（与 classifyFailure 逻辑保持一致）
        String rawMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : e.getClass().getSimpleName().toLowerCase();
        if (rawMsg.contains("timeout") || rawMsg.contains("dns")
                || rawMsg.contains("connection") || rawMsg.contains("interrupt")) {
            context.setErrorType(ErrorTypeEnum.ENV_ERROR);
        } else if (rawMsg.contains("jsonpath") || rawMsg.contains("parse")
                || rawMsg.contains("replace")) {
            context.setErrorType(ErrorTypeEnum.PLATFORM_ERROR);
        } else {
            context.setErrorType(ErrorTypeEnum.PLATFORM_ERROR);
        }

        try {
            if (context.getNodeInstanceId() != null) {
                TaskNodeInstance nodeInstance = taskNodeInstanceService.getById(context.getNodeInstanceId());
                if (nodeInstance != null) {
                    nodeInstance.setStatusEnum(NodeInstanceStatusEnum.FAILED);
                    nodeInstance.setStatus(EnumConvertUtil.fromNodeInstanceStatusEnum(NodeInstanceStatusEnum.FAILED));
                    nodeInstance.setErrorTypeEnum(context.getErrorType());
                    nodeInstance.setErrorType(EnumConvertUtil.fromErrorTypeEnum(context.getErrorType()));
                    nodeInstance.setErrorMsg(context.getErrorMsg());
                    nodeInstance.setEndTime(LocalDateTime.now());
                    nodeInstance.setPassed(0);
                    taskNodeInstanceService.updateById(nodeInstance);
                }
            }
        } catch (Exception ex) {
            log.error("批量模式错误持久化失败", ex);
        }
    }

    /**
     * 生成任务编号：T-yyyyMMdd-{6位序号}
     */
    private String generateTaskNo() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 使用 UUID 的前6位作为序号，避免高并发冲突
        String sequence = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "T-" + dateStr + "-" + sequence;
    }

    /**
     * 合并两个 Map，source 覆盖 target 的同名 Key
     */
    private <K, V> Map<K, V> mergeMaps(Map<K, V> target, Map<K, V> source) {
        Map<K, V> result = new LinkedHashMap<>();
        if (target != null) {
            result.putAll(target);
        }
        if (source != null) {
            result.putAll(source);
        }
        return result;
    }

    /**
     * 环境变量替换：如 ${env.baseUrl} 替换为实际地址
     */
    private String replaceEnvVariables(String text, String env) {
        if (text == null || !text.contains("${env.")) {
            return text;
        }

        try {
            Matcher matcher = ENV_VAR_PATTERN.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String varName = matcher.group(1);
                String value = getEnvVariable(varName);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : matcher.group(0)));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            log.error("环境变量替换失败: text={}, env={}", text, env, e);
            throw new RuntimeException("环境变量替换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取环境变量值，找不到时返回 null
     */
    private String getEnvVariable(String varName) {
        // 从系统属性或配置中读取环境变量
        String value = System.getProperty("env." + varName);
        if (value != null) {
            return value;
        }

        // 从环境变量中读取
        value = System.getenv(varName);
        if (value != null) {
            return value;
        }

        // 如果都找不到，返回 null（调用方将保留原始占位符）
        return null;
    }


    /**
     * 解析 JSON 字符串为 Map<String, String>
     */
    private Map<String, String> parseJsonToMap(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("JSON解析失败: {}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * 解析 JSON 字符串为 Map<String, Object>
     */
    private Map<String, Object> parseJsonToMapObject(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("JSON解析失败: {}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * 解析 JSON 字符串
     */
    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.warn("JSON解析失败: {}", json, e);
            return json;
        }
    }

    /**
     * 异步插入执行日志
     */
    private void insertExecutionLogAsync(ExecuteContext context, long duration) {
        CompletableFuture.runAsync(() -> {
            try {
                // 记录请求日志
                executionLogService.saveLogAsync(
                        context.getTaskId(),
                        context.getNodeInstanceId(),
                        LogTypeEnum.REQUEST,
                        "请求发送完成",
                        context.getRequestSnapshot()
                );

                // 记录响应日志
                executionLogService.saveLogAsync(
                        context.getTaskId(),
                        context.getNodeInstanceId(),
                        LogTypeEnum.RESPONSE,
                        "响应接收完成",
                        context.getResponseSnapshot()
                );

                // 记录错误日志（如果有）
                if (!context.getSuccess() && context.getErrorMsg() != null) {
                    executionLogService.saveLogAsync(
                            context.getTaskId(),
                            context.getNodeInstanceId(),
                            LogTypeEnum.ERROR,
                            context.getErrorMsg(),
                            context.getErrorType() != null ? context.getErrorType().name() : "UNKNOWN"
                    );
                }

                log.info("异步插入执行日志完成: caseId={}, duration={}ms",
                        context.getCaseId(), duration);
            } catch (Exception e) {
                log.error("插入执行日志失败: caseId={}", context.getCaseId(), e);
            }
        }, asyncTaskExecutor);
    }
}
