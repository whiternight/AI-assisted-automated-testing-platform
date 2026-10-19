package com.example.testplatform.apicase.service.impl;

import com.example.testplatform.common.exception.BusinessException;
import com.example.testplatform.apicase.domain.dto.*;
import com.example.testplatform.app.domain.dto.*;
import com.example.testplatform.apicase.domain.po.ApiCase;
import com.example.testplatform.apicase.domain.po.ApiCaseHistory;
import com.example.testplatform.app.domain.po.Application;
import com.example.testplatform.app.domain.po.BusinessDomain;
import com.example.testplatform.apicase.domain.vo.ApiCaseStatisticsVO;
import com.example.testplatform.apicase.domain.vo.ApiCaseVO;
import com.example.testplatform.apicase.domain.vo.AssertionRuleVO;
import com.example.testplatform.apicase.domain.vo.BatchDebugResultVO;
import com.example.testplatform.apicase.domain.vo.CaseDebugResponseVO;
import com.example.testplatform.execution.domain.vo.DirectTestResponseVO;
import com.example.testplatform.apicase.domain.vo.DuplicateCheckResultVO;
import com.example.testplatform.execution.engine.CaseExecuteEngine;
import com.example.testplatform.enums.BusinessExceptionEnum;
import com.example.testplatform.enums.ProtocolTypeEnum;
import com.example.testplatform.enums.RequestTypeEnum;
import com.example.testplatform.auth.domain.po.User;
import com.example.testplatform.auth.service.IUserService;
import com.example.testplatform.apicase.mapper.ApiCaseMapper;
import com.example.testplatform.apicase.service.IApiCaseChangeLogService;
import com.example.testplatform.apicase.service.IApiCaseHistoryService;
import com.example.testplatform.apicase.service.IApiCaseService;
import com.example.testplatform.app.service.IApplicationService;
import com.example.testplatform.app.service.DirectTestService;
import com.example.testplatform.app.service.IBusinessDomainService;
import com.example.testplatform.common.util.AesEncryptUtil;
import com.example.testplatform.common.util.SecurityUtil;
import com.example.testplatform.apicase.util.CurlParserUtil;
import com.example.testplatform.originalservice.domain.po.OriginalService;
import com.example.testplatform.originalservice.service.IOriginalServiceService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 用例主表 服务实现类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCaseServiceImpl extends ServiceImpl<ApiCaseMapper, ApiCase> implements IApiCaseService {

    private final CaseExecuteEngine caseExecuteEngine;
    private final IApplicationService applicationService;
    private final IBusinessDomainService businessDomainService;
    private final IApiCaseHistoryService apiCaseHistoryService;
    private final IApiCaseChangeLogService apiCaseChangeLogService;
    private final IUserService userService;
    private final AesEncryptUtil aesEncryptUtil;
    private final DirectTestService directTestService;
    private final IOriginalServiceService originalServiceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 允许排序的列名白名单 */
    private static final java.util.Set<String> ALLOWED_ORDER_COLUMNS = java.util.Set.of(
            "updated_at", "created_at", "name", "id"
    );

    // ==================== 辅助方法 ====================

    /**
     * 根据用例配置构建直接测试请求
     */
    private DirectTestRequestDTO buildDirectTestRequest(ApiCase apiCase) {
        DirectTestRequestDTO dto = new DirectTestRequestDTO();
        dto.setProtocolType(apiCase.getProtocolTypeEnum() != null
                ? apiCase.getProtocolTypeEnum()
                : ProtocolTypeEnum.getByCode(apiCase.getProtocolType()));
        dto.setResponseFormat(apiCase.getResponseFormat());
        dto.setTimeout(apiCase.getTimeout());

        // 反序列化断言规则传入测试请求
        try {
            if (StringUtils.hasText(apiCase.getResponseAssertions())) {
                dto.setResponseAssertions(objectMapper.readValue(apiCase.getResponseAssertions(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<AssertionRuleDTO>>() {}));
            }
        } catch (Exception e) {
            log.warn("解析断言规则失败: caseId={}", apiCase.getId(), e);
        }

        if (dto.getProtocolType() == ProtocolTypeEnum.HTTP) {
            dto.setRequestType(apiCase.getRequestType() != null ? apiCase.getRequestType().getMethod() : null);
            dto.setAddress(apiCase.getAddress());
            try {
                if (StringUtils.hasText(apiCase.getRequestHeaders())) {
                    dto.setRequestHeaders(objectMapper.readValue(apiCase.getRequestHeaders(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {}));
                }
                if (StringUtils.hasText(apiCase.getRequestParams())) {
                    dto.setRequestParams(objectMapper.readValue(apiCase.getRequestParams(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}));
                }
            } catch (Exception e) {
                log.warn("解析用例请求配置失败: caseId={}", apiCase.getId(), e);
            }
            dto.setRequestBody(apiCase.getRequestBody());
        } else if (dto.getProtocolType() == ProtocolTypeEnum.DUBBO) {
            // Dubbo 地址格式: 接口全名.方法名:版本号
            String address = apiCase.getAddress();
            if (address != null && address.contains(".")) {
                int lastDot = address.lastIndexOf('.');
                dto.setServiceName(address.substring(0, lastDot));
                String methodAndVersion = address.substring(lastDot + 1);
                if (methodAndVersion.contains(":")) {
                    String[] parts = methodAndVersion.split(":");
                    dto.setMethodName(parts[0]);
                    dto.setVersion(parts[1]);
                }
            }
            // requestBody 存储的是 {paramType: paramValue} 的 JSON，还原为 paramTypes + paramValues
            try {
                if (StringUtils.hasText(apiCase.getRequestBody())) {
                    java.util.Map<String, Object> paramMap = objectMapper.readValue(
                            apiCase.getRequestBody(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                    dto.setParamTypes(new java.util.ArrayList<>(paramMap.keySet()));
                    dto.setParamValues(new java.util.ArrayList<>(paramMap.values()));
                }
            } catch (Exception e) {
                log.warn("解析Dubbo参数失败: caseId={}", apiCase.getId(), e);
            }
        }
        return dto;
    }

    /**
     * 执行用例并存入测试历史（快照），同时回写执行结果到 api_case 表
     */
    private void executeAndSaveHistory(ApiCase apiCase, Long executorId) {
        try {
            log.info("执行用例并存入历史快照: caseId={}", apiCase.getId());
            DirectTestRequestDTO testRequest = buildDirectTestRequest(apiCase);
            DirectTestResponseVO testResponse = directTestService.test(testRequest);

            String responseSnapshot = null;
            try {
                responseSnapshot = objectMapper.writeValueAsString(testResponse);
            } catch (Exception e) {
                log.warn("序列化响应快照失败: caseId={}", apiCase.getId(), e);
            }

            // 提取响应体字符串
            String responseBodyStr = null;
            if (testResponse.getResponse() != null && testResponse.getResponse().getBody() != null) {
                try {
                    responseBodyStr = objectMapper.writeValueAsString(testResponse.getResponse().getBody());
                } catch (Exception e) {
                    responseBodyStr = String.valueOf(testResponse.getResponse().getBody());
                }
            }

            boolean success = testResponse.getSuccess() != null && testResponse.getSuccess();

            // 1. 存入 api_case_history
            ApiCaseHistory history = new ApiCaseHistory();
            history.setCaseId(apiCase.getId());
            history.setHistoryType(1); // SINGLE_API
            history.setRequestSnapshot(null);
            history.setResponseSnapshot(responseSnapshot);
            history.setResult(success ? 1 : 0);
            history.setExecutorId(executorId);
            history.setEnv(apiCase.getEnv());
            history.setCreatedAt(java.time.LocalDateTime.now());
            history.setUpdatedAt(java.time.LocalDateTime.now());
            history.setDeleted(0);
            apiCaseHistoryService.save(history);

            // 2. 回写执行结果到 api_case 表（冗余存储，避免查询时 N+1）
            apiCase.setResponseBody(responseBodyStr);
            apiCase.setIsSuccessful(success ? 1 : 0);
            this.updateById(apiCase);

            log.info("执行用例并存入历史快照成功: caseId={}, result={}", apiCase.getId(), success);
        } catch (Exception e) {
            log.warn("执行用例并存入历史快照失败（不影响主流程）: caseId={}", apiCase.getId(), e);
        }
    }

    /**
     * 跟踪字段变更
     */
    private void trackFieldChange(java.util.Map<String, String[]> changes, String fieldName,
                                   String oldValue, String newValue) {
        if ((oldValue == null && newValue != null)
                || (oldValue != null && !oldValue.equals(newValue))) {
            changes.put(fieldName, new String[]{oldValue, newValue});
        }
    }

    /**
     * 记录字段级变更日志
     */
    private void saveFieldChangeLogs(String caseId, java.util.Map<String, String[]> changes) {
        changes.forEach((fieldName, values) -> {
            try {
                apiCaseChangeLogService.saveChangeLog(caseId, fieldName, values[0], values[1]);
            } catch (Exception e) {
                log.warn("记录字段变更日志失败: caseId={}, field={}", caseId, fieldName, e);
            }
        });
    }

    /**
     * 单用例调试执行
     *
     * @param caseId 用例ID
     * @param requestDTO 请求参数
     * @return 执行结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaseDebugResponseVO debug(String caseId, CaseDebugRequestDTO requestDTO) {
        log.info("开始调试用例: caseId={}", caseId);

        // 1. 校验用例是否存在且未删除
        ApiCase apiCase = validateCaseExists(caseId);

        // 2. 校验当前用户是否有该应用的执行权限
        Long executorId = validateExecutionPermission(apiCase.getApplicationId());

        // 3. 调用执行引擎
        try {
            CaseDebugResponseVO response = caseExecuteEngine.execute(caseId, requestDTO, executorId);
            log.info("用例调试完成: caseId={}, success={}", caseId, response.getResult());
            return response;
        } catch (Exception e) {
            log.error("用例调试失败: caseId={}", caseId, e);
            throw new RuntimeException("用例调试失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据ID查询用例详情（含完整参数）
     *
     * @param caseId 用例ID
     * @return 用例详情
     */
    @Override
    public ApiCase getCaseDetail(String caseId) {
        log.info("查询用例详情: caseId={}", caseId);

        // 校验用例是否存在且未删除
        ApiCase apiCase = validateCaseExists(caseId);

        log.info("查询用例详情成功: caseId={}, name={}", caseId, apiCase.getName());
        return apiCase;
    }

    /**
     * 重试执行历史用例
     *
     * @param historyId 历史记录ID
     * @return 执行结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaseDebugResponseVO retry(String historyId) {
        log.info("开始重试历史用例: historyId={}", historyId);

        // 1. 查询历史记录
        ApiCaseHistory history = apiCaseHistoryService.getById(historyId);
        if (history == null) {
            throw new IllegalArgumentException("历史记录不存在: " + historyId);
        }

        // 2. 解密请求快照，还原原始请求参数
        CaseDebugRequestDTO requestDTO = decryptAndParseRequestSnapshot(history.getRequestSnapshot());

        // 3. 调用执行引擎
        try {
            CaseDebugResponseVO response = caseExecuteEngine.execute(
                    history.getCaseId(), requestDTO, history.getExecutorId());
            log.info("历史用例重试完成: historyId={}, success={}", historyId, response.getResult());
            return response;
        } catch (Exception e) {
            log.error("历史用例重试失败: historyId={}", historyId, e);
            throw new RuntimeException("重试失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DuplicateCheckResultVO checkDuplicate(String applicationId, String address,
                                                 ProtocolTypeEnum protocolType, String excludeId) {
        // 构建查询条件（@TableLogic 启用后，MP 自动过滤 deleted=1，无需手动加）
        QueryWrapper<ApiCase> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("application_id", applicationId)
                .eq("address", address)
                .eq("protocol_type", protocolType.getCode());

        // 更新时排除自身
        if (StringUtils.hasText(excludeId)) {
            queryWrapper.ne("id", excludeId);
        }

        // 查询重复用例
        ApiCase duplicateCase = this.getOne(queryWrapper);

        // 构建返回结果
        DuplicateCheckResultVO result = new DuplicateCheckResultVO();
        if (duplicateCase != null) {
            result.setIsDuplicate(true);
            result.setDuplicateCaseId(duplicateCase.getId());
            result.setDuplicateCaseName(duplicateCase.getName());
            result.setMessage("已存在相同配置的用例: " + duplicateCase.getName());
        } else {
            result.setIsDuplicate(false);
            result.setMessage("未发现重复用例");
        }

        return result;
    }

    /**
     * 新增HTTP用例
     * <p>
     * 将 HttpCaseCreateDTO 转换为 ApiCase 实体并持久化，
     * 自动填充创建人、协议类型等字段
     *
     * @param dto HTTP用例创建参数
     * @return 新创建的用例ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createHttpCase(HttpCaseCreateDTO dto) {
        log.info("开始创建HTTP用例: name={}, address={}", dto.getName(), dto.getAddress());

        // 重复校验：同应用+同地址+同协议
        DuplicateCheckResultVO dupCheck = checkDuplicate(dto.getApplicationId(), dto.getAddress(), ProtocolTypeEnum.HTTP, null);
        if (Boolean.TRUE.equals(dupCheck.getIsDuplicate())) {
            throw new BusinessException(BusinessExceptionEnum.CASE_DUPLICATE, dupCheck.getMessage());
        }

        // 转换为 ApiCase 实体
        ApiCase apiCase = new ApiCase();
        apiCase.setName(dto.getName());
        apiCase.setAddress(dto.getAddress());
        apiCase.setProtocolType(ProtocolTypeEnum.HTTP.getCode());
        apiCase.setRequestType(dto.getRequestType());  // 直接设置枚举值
        apiCase.setTimeout(dto.getTimeout());
        apiCase.setEnv(dto.getEnv());
        apiCase.setApplicationId(dto.getApplicationId());
        apiCase.setBusinessDomainId(dto.getBusinessDomainId() != null ? String.valueOf(dto.getBusinessDomainId()) : null);

        // 序列化 JSON 字段
        try {
            if (dto.getRequestHeaders() != null && !dto.getRequestHeaders().isEmpty()) {
                apiCase.setRequestHeaders(objectMapper.writeValueAsString(dto.getRequestHeaders()));
            }
            if (dto.getRequestParams() != null && !dto.getRequestParams().isEmpty()) {
                apiCase.setRequestParams(objectMapper.writeValueAsString(dto.getRequestParams()));
            }
            apiCase.setRequestBody(dto.getRequestBody());
            if (dto.getResponseAssertions() != null && !dto.getResponseAssertions().isEmpty()) {
                apiCase.setResponseAssertions(objectMapper.writeValueAsString(dto.getResponseAssertions()));
            }
            apiCase.setResponseFormat(dto.getResponseFormat());
        } catch (Exception e) {
            log.error("序列化用例JSON字段失败: name={}", dto.getName(), e);
            throw new RuntimeException("序列化用例数据失败", e);
        }

        // 填充审计字段
        Long currentUserId = SecurityUtil.getCurrentUserId();
        apiCase.setCreatorId(currentUserId);
        apiCase.setOwnerId(currentUserId);
        apiCase.setIsAutomationEnabled(0);
        apiCase.setCreatedAt(LocalDateTime.now());
        apiCase.setUpdatedAt(LocalDateTime.now());
        apiCase.setDeleted(0);

        this.save(apiCase);
        log.info("HTTP用例创建成功: caseId={}, name={}", apiCase.getId(), apiCase.getName());

        // 记录变更日志 CREATE
        apiCaseChangeLogService.saveChangeLog(apiCase.getId(), "CREATE", null, apiCase.getName());

        // 执行用例并存入响应快照到 api_case_history
        executeAndSaveHistory(apiCase, currentUserId);
        return apiCase.getId();
    }

    /**
     * 新增Dubbo用例
     * <p>
     * 将 DubboCaseCreateDTO 转换为 ApiCase 实体并持久化，
     * address 字段拼接为 "接口全名.方法名:版本号" 格式
     *
     * @param dto Dubbo用例创建参数
     * @return 新创建的用例ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDubboCase(DubboCaseCreateDTO dto) {
        log.info("开始创建Dubbo用例: name={}, address={}, method={}", dto.getName(), dto.getAddress(), dto.getMethodName());

        // 拼接 Dubbo 地址格式：接口全名.方法名:版本号
        String fullAddress = dto.getAddress() + "." + dto.getMethodName() + ":" + dto.getVersion();

        // 重复校验：同应用+同地址+同协议
        DuplicateCheckResultVO dupCheck = checkDuplicate(dto.getApplicationId(), fullAddress, ProtocolTypeEnum.DUBBO, null);
        if (Boolean.TRUE.equals(dupCheck.getIsDuplicate())) {
            throw new BusinessException(BusinessExceptionEnum.CASE_DUPLICATE, dupCheck.getMessage());
        }

        // 转换为 ApiCase 实体
        ApiCase apiCase = new ApiCase();
        apiCase.setName(dto.getName());
        apiCase.setAddress(fullAddress);
        apiCase.setProtocolType(ProtocolTypeEnum.DUBBO.getCode());
        apiCase.setTimeout(dto.getTimeout());
        apiCase.setEnv(dto.getEnv());
        apiCase.setApplicationId(dto.getApplicationId());
        apiCase.setBusinessDomainId(dto.getBusinessDomainId() != null ? String.valueOf(dto.getBusinessDomainId()) : null);

        // 序列化 Dubbo 参数为 requestBody
        try {
            if (dto.getParamTypes() != null && dto.getParamValues() != null) {
                Map<String, Object> paramMap = new LinkedHashMap<>();
                for (int i = 0; i < Math.min(dto.getParamTypes().size(), dto.getParamValues().size()); i++) {
                    paramMap.put(dto.getParamTypes().get(i), dto.getParamValues().get(i));
                }
                apiCase.setRequestBody(objectMapper.writeValueAsString(paramMap));
            }
            if (dto.getResponseAssertions() != null && !dto.getResponseAssertions().isEmpty()) {
                apiCase.setResponseAssertions(objectMapper.writeValueAsString(dto.getResponseAssertions()));
            }
            apiCase.setResponseFormat(dto.getResponseFormat());
        } catch (Exception e) {
            log.error("序列化Dubbo用例JSON字段失败: name={}", dto.getName(), e);
            throw new RuntimeException("序列化用例数据失败", e);
        }

        // 填充审计字段
        Long currentUserId = SecurityUtil.getCurrentUserId();
        apiCase.setCreatorId(currentUserId);
        apiCase.setOwnerId(currentUserId);
        apiCase.setIsAutomationEnabled(0);
        apiCase.setCreatedAt(LocalDateTime.now());
        apiCase.setUpdatedAt(LocalDateTime.now());
        apiCase.setDeleted(0);

        this.save(apiCase);
        log.info("Dubbo用例创建成功: caseId={}, name={}", apiCase.getId(), apiCase.getName());

        // 记录变更日志 CREATE
        apiCaseChangeLogService.saveChangeLog(apiCase.getId(), "CREATE", null, apiCase.getName());

        // 执行用例并存入响应快照到 api_case_history
        executeAndSaveHistory(apiCase, currentUserId);

        return apiCase.getId();
    }

    /**
     * 更新HTTP用例
     * <p>
     * 仅更新 DTO 中非空字段，空字段保持原值不变
     *
     * @param dto HTTP用例更新参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateHttpCase(HttpCaseUpdateDTO dto) {
        log.info("开始更新HTTP用例: caseId={}", dto.getId());

        // 校验用例存在且未删除
        ApiCase apiCase = validateCaseExists(dto.getId());

        // 捕获旧值用于变更日志
        ApiCase oldCase = new ApiCase();
        org.springframework.beans.BeanUtils.copyProperties(apiCase, oldCase);

        // 仅更新非空字段
        if (StringUtils.hasText(dto.getName())) {
            apiCase.setName(dto.getName());
        }
        if (StringUtils.hasText(dto.getUrl())) {
            apiCase.setAddress(dto.getUrl());
        }
        if (StringUtils.hasText(dto.getMethod())) {
            // 将字符串转换为 RequestTypeEnum
            RequestTypeEnum requestType = RequestTypeEnum.getByMethod(dto.getMethod());
            if (requestType != null) {
                apiCase.setRequestType(requestType);
            } else {
                log.warn("无效的HTTP方法: {}", dto.getMethod());
            }
        }
        if (StringUtils.hasText(dto.getHeaders())) {
            apiCase.setRequestHeaders(dto.getHeaders());
        }
        if (dto.getBody() != null) {
            apiCase.setRequestBody(dto.getBody());
        }
        if (dto.getTimeout() != null) {
            apiCase.setTimeout(dto.getTimeout());
        }
        if (dto.getAssertionRules() != null && !dto.getAssertionRules().isEmpty()) {
            try {
                apiCase.setResponseAssertions(objectMapper.writeValueAsString(dto.getAssertionRules()));
            } catch (Exception e) {
                log.error("序列化断言规则失败: caseId={}", dto.getId(), e);
                throw new RuntimeException("序列化断言规则失败", e);
            }
        }

        // 更新时重复校验（地址变更时排除自身）
        if (!oldCase.getAddress().equals(apiCase.getAddress())) {
            DuplicateCheckResultVO dupCheck = checkDuplicate(
                    apiCase.getApplicationId(), apiCase.getAddress(),
                    ProtocolTypeEnum.HTTP, apiCase.getId());
            if (Boolean.TRUE.equals(dupCheck.getIsDuplicate())) {
                throw new BusinessException(BusinessExceptionEnum.CASE_DUPLICATE, dupCheck.getMessage());
            }
        }

        // 更新审计字段
        apiCase.setUpdaterId(SecurityUtil.getCurrentUserId());
        apiCase.setUpdatedAt(LocalDateTime.now());

        this.updateById(apiCase);
        log.info("HTTP用例更新成功: caseId={}", dto.getId());

        // 记录字段级变更日志
        java.util.Map<String, String[]> changes = new LinkedHashMap<>();
        trackFieldChange(changes, "name", oldCase.getName(), apiCase.getName());
        trackFieldChange(changes, "address", oldCase.getAddress(), apiCase.getAddress());
        trackFieldChange(changes, "requestType",
                oldCase.getRequestType() != null ? oldCase.getRequestType().name() : null,
                apiCase.getRequestType() != null ? apiCase.getRequestType().name() : null);
        trackFieldChange(changes, "requestHeaders", oldCase.getRequestHeaders(), apiCase.getRequestHeaders());
        trackFieldChange(changes, "requestBody", oldCase.getRequestBody(), apiCase.getRequestBody());
        trackFieldChange(changes, "timeout",
                oldCase.getTimeout() != null ? String.valueOf(oldCase.getTimeout()) : null,
                apiCase.getTimeout() != null ? String.valueOf(apiCase.getTimeout()) : null);
        trackFieldChange(changes, "responseAssertions", oldCase.getResponseAssertions(), apiCase.getResponseAssertions());
        saveFieldChangeLogs(dto.getId(), changes);

        // 更新后重新执行并存入响应快照
        executeAndSaveHistory(apiCase, SecurityUtil.getCurrentUserId());
    }

    /**
     * 更新Dubbo用例
     * <p>
     * 仅更新 DTO 中非空字段，空字段保持原值不变
     *
     * @param dto Dubbo用例更新参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDubboCase(DubboCaseUpdateDTO dto) {
        log.info("开始更新Dubbo用例: caseId={}", dto.getId());

        // 校验用例存在且未删除
        ApiCase apiCase = validateCaseExists(dto.getId());

        // 捕获旧值用于变更日志
        ApiCase oldCase = new ApiCase();
        org.springframework.beans.BeanUtils.copyProperties(apiCase, oldCase);

        // 仅更新非空字段
        if (StringUtils.hasText(dto.getName())) {
            apiCase.setName(dto.getName());
        }
        // Dubbo 地址更新：需要拼接完整格式 "接口全名.方法名:版本号"
        if (StringUtils.hasText(dto.getInterfaceName())) {
            // 从现有地址中解析出方法名和版本号
            String interfaceName = dto.getInterfaceName();
            String methodName = null;
            String version = null;

            // 从原地址解析: interface.method:version
            String oldAddress = apiCase.getAddress();
            if (oldAddress != null && oldAddress.contains(".")) {
                int lastDot = oldAddress.lastIndexOf('.');
                String methodAndVersion = oldAddress.substring(lastDot + 1);
                if (methodAndVersion.contains(":")) {
                    String[] parts = methodAndVersion.split(":");
                    methodName = parts[0];
                    version = parts[1];
                }
            }

            // DTO 中提供了新的方法名则覆盖
            if (StringUtils.hasText(dto.getMethodName())) {
                methodName = dto.getMethodName();
            }

            // 拼接完整地址
            if (methodName != null && version != null) {
                apiCase.setAddress(interfaceName + "." + methodName + ":" + version);
            } else {
                // 缺少方法名或版本号时直接设置接口名
                apiCase.setAddress(interfaceName);
            }
        }
        if (dto.getTimeout() != null) {
            apiCase.setTimeout(dto.getTimeout());
        }
        if (dto.getParameters() != null && !dto.getParameters().isEmpty()) {
            try {
                apiCase.setRequestBody(objectMapper.writeValueAsString(dto.getParameters()));
            } catch (Exception e) {
                log.error("序列化Dubbo参数失败: caseId={}", dto.getId(), e);
                throw new RuntimeException("序列化参数失败", e);
            }
        }
        if (dto.getAssertionRules() != null && !dto.getAssertionRules().isEmpty()) {
            try {
                apiCase.setResponseAssertions(objectMapper.writeValueAsString(dto.getAssertionRules()));
            } catch (Exception e) {
                log.error("序列化断言规则失败: caseId={}", dto.getId(), e);
                throw new RuntimeException("序列化断言规则失败", e);
            }
        }

        // 更新时重复校验（地址变更时排除自身）
        if (!oldCase.getAddress().equals(apiCase.getAddress())) {
            DuplicateCheckResultVO dupCheck = checkDuplicate(
                    apiCase.getApplicationId(), apiCase.getAddress(),
                    ProtocolTypeEnum.DUBBO, apiCase.getId());
            if (Boolean.TRUE.equals(dupCheck.getIsDuplicate())) {
                throw new BusinessException(BusinessExceptionEnum.CASE_DUPLICATE, dupCheck.getMessage());
            }
        }

        // 更新审计字段
        apiCase.setUpdaterId(SecurityUtil.getCurrentUserId());
        apiCase.setUpdatedAt(LocalDateTime.now());

        this.updateById(apiCase);
        log.info("Dubbo用例更新成功: caseId={}", dto.getId());

        // 记录字段级变更日志
        java.util.Map<String, String[]> changes = new LinkedHashMap<>();
        trackFieldChange(changes, "name", oldCase.getName(), apiCase.getName());
        trackFieldChange(changes, "address", oldCase.getAddress(), apiCase.getAddress());
        trackFieldChange(changes, "timeout",
                oldCase.getTimeout() != null ? String.valueOf(oldCase.getTimeout()) : null,
                apiCase.getTimeout() != null ? String.valueOf(apiCase.getTimeout()) : null);
        trackFieldChange(changes, "requestBody", oldCase.getRequestBody(), apiCase.getRequestBody());
        trackFieldChange(changes, "responseAssertions", oldCase.getResponseAssertions(), apiCase.getResponseAssertions());
        saveFieldChangeLogs(dto.getId(), changes);

        // 更新后重新执行并存入响应快照
        executeAndSaveHistory(apiCase, SecurityUtil.getCurrentUserId());
    }

    /**
     * 根据ID查询用例详情（返回VO）
     * <p>
     * 将 ApiCase 实体转换为前端友好的 ApiCaseVO，
     * 包含关联的应用名称、业务域名称，并反序列化 JSON 字段
     *
     * @param id 用例ID
     * @return 用例详情VO
     */
    @Override
    public ApiCaseVO getCaseById(String id) {
        log.info("查询用例详情: caseId={}", id);

        // 校验用例存在
        ApiCase apiCase = validateCaseExists(id);

        // 转换为 VO
        return convertToVO(apiCase);
    }

    /**
     * 分页查询用例列表
     * <p>
     * 支持按关键词、应用、业务域、协议类型、自动化开关、负责人、环境等条件筛选，
     * 结果按指定字段排序并转换为 VO 列表
     *
     * @param queryDTO 查询条件及分页参数
     * @return 分页的用例VO列表
     */
    @Override
    public Page<ApiCaseVO> listCases(ApiCaseQueryDTO queryDTO) {
        log.info("分页查询用例列表: keyword={}, page={}/{}",
                queryDTO.getKeyword(), queryDTO.getPageNum(), queryDTO.getPageSize());

        // 构建分页参数
        Page<ApiCase> pageParam = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        // 构建查询条件（@TableLogic 启用后，MP 自动过滤 deleted=1，无需手动加）
        QueryWrapper<ApiCase> queryWrapper = new QueryWrapper<>();

        if (StringUtils.hasText(queryDTO.getKeyword())) {
            queryWrapper.like("name", queryDTO.getKeyword());
        }
        if (queryDTO.getApplicationId() != null) {
            queryWrapper.eq("application_id", queryDTO.getApplicationId());
        }
        if (queryDTO.getBusinessDomainId() != null) {
            queryWrapper.eq("business_domain_id", queryDTO.getBusinessDomainId());
        }
        if (queryDTO.getProtocolType() != null) {
            queryWrapper.eq("protocol_type", queryDTO.getProtocolType().getCode());
        }
        if (queryDTO.getIsAutomationEnabled() != null) {
            queryWrapper.eq("is_automation_enabled", queryDTO.getIsAutomationEnabled() ? 1 : 0);
        }
        if (queryDTO.getOwnerId() != null) {
            queryWrapper.eq("owner_id", queryDTO.getOwnerId());
        }
        if (StringUtils.hasText(queryDTO.getEnv())) {
            queryWrapper.eq("env", queryDTO.getEnv());
        }

        // 排序列白名单校验
        String orderColumn = queryDTO.getOrderBy();
        if (orderColumn == null || !ALLOWED_ORDER_COLUMNS.contains(orderColumn)) {
            orderColumn = "updated_at";
        }
        if (queryDTO.getIsAsc() != null && queryDTO.getIsAsc()) {
            queryWrapper.orderByAsc(orderColumn);
        } else {
            queryWrapper.orderByDesc(orderColumn);
        }

        // 执行分页查询
        Page<ApiCase> entityPage = this.page(pageParam, queryWrapper);

        // 转换为 VO 分页结果
        Page<ApiCaseVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<ApiCaseVO> voList = entityPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    /**
     * 逻辑删除用例
     * <p>
     * 将 deleted 字段置为 1，不会物理删除数据
     *
     * @param id 用例ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(String id) {
        log.info("开始删除用例: caseId={}", id);

        ApiCase apiCase = validateCaseExists(id);

        // MyBatis-Plus 逻辑删除（ApiCase 已启用 @TableLogic）：UPDATE api_case SET deleted=1 WHERE id=? AND deleted=0
        boolean removed = this.removeById(id);
        if (!removed) {
            throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR, "删除失败，用例已被删除");
        }

        try {
            apiCaseChangeLogService.saveChangeLog(id, "DELETE", apiCase.getName(), null);
        } catch (Exception e) {
            log.warn("记录变更日志失败: caseId={}", id, e);
        }

        log.info("用例删除成功: caseId={}", id);
    }

    /**
     * 批量逻辑删除用例
     * <p>
     * 遍历ID列表，逐个校验并执行逻辑删除
     *
     * @param ids 用例ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteCases(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            log.warn("批量删除用例: ID列表为空");
            return;
        }
        log.info("开始批量删除用例: count={}", ids.size());

        for (String id : ids) {
            deleteCase(id);
        }

        log.info("批量删除用例完成: count={}", ids.size());
    }

    /**
     * 更新自动化开关（单条）
     * <p>
     * 开启或关闭某用例的自动化测试参与标记
     *
     * @param id 用例ID
     * @param isAutomationEnabled true=参与自动化，false=不参与
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAutomationFlag(String id, Boolean isAutomationEnabled) {
        log.info("开始更新自动化开关: caseId={}, flag={}", id, isAutomationEnabled);

        // 校验用例存在且未删除
        ApiCase apiCase = validateCaseExists(id);

        // 更新自动化标记
        apiCase.setIsAutomationEnabled(isAutomationEnabled != null && isAutomationEnabled ? 1 : 0);
        apiCase.setUpdatedAt(LocalDateTime.now());
        apiCase.setUpdaterId(SecurityUtil.getCurrentUserId());
        this.updateById(apiCase);

        log.info("自动化开关更新成功: caseId={}, flag={}", id, isAutomationEnabled);
    }

    /**
     * 批量更新自动化开关
     * <p>
     * 批量设置多个用例的自动化测试参与标记
     *
     * @param ids 用例ID列表
     * @param isAutomationEnabled true=参与自动化，false=不参与
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateAutomationFlag(List<String> ids, Boolean isAutomationEnabled) {
        if (ids == null || ids.isEmpty()) {
            log.warn("批量更新自动化开关: ID列表为空");
            return;
        }
        log.info("开始批量更新自动化开关: count={}, flag={}", ids.size(), isAutomationEnabled);

        for (String id : ids) {
            updateAutomationFlag(id, isAutomationEnabled);
        }

        log.info("批量更新自动化开关完成: count={}", ids.size());
    }

    /**
     * 批量变更负责人
     * <p>
     * 将多个用例的负责人统一变更为指定用户
     *
     * @param ids 用例ID列表
     * @param ownerId 新负责人ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateOwner(List<String> ids, Long ownerId) {
        if (ids == null || ids.isEmpty()) {
            log.warn("批量变更负责人: ID列表为空");
            return;
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("负责人ID不能为空");
        }
        log.info("开始批量变更负责人: count={}, ownerId={}", ids.size(), ownerId);

        for (String id : ids) {
            ApiCase apiCase = validateCaseExists(id);
            apiCase.setOwnerId(ownerId);
            apiCase.setUpdatedAt(LocalDateTime.now());
            apiCase.setUpdaterId(SecurityUtil.getCurrentUserId());
            this.updateById(apiCase);
        }

        log.info("批量变更负责人完成: count={}, ownerId={}", ids.size(), ownerId);
    }

    // ==================== 私有辅助：VO 转换 ====================

    /**
     * 将 ApiCase 实体转换为 ApiCaseVO
     * <p>
     * 反序列化 JSON 字段为结构化对象，
     * 并查询关联应用和业务域的名称
     *
     * @param apiCase 用例实体
     * @return 用例VO
     */
    private ApiCaseVO convertToVO(ApiCase apiCase) {
        ApiCaseVO vo = new ApiCaseVO();
        vo.setId(apiCase.getId());
        vo.setName(apiCase.getName());
        vo.setAddress(apiCase.getAddress());
        vo.setProtocolType(apiCase.getProtocolType() == 1 ? "HTTP" :
                           apiCase.getProtocolType() == 2 ? "DUBBO" : String.valueOf(apiCase.getProtocolType()));
        vo.setRequestType(apiCase.getRequestType() != null ? apiCase.getRequestType().getMethod() : null);  // 转换为字符串
        vo.setEnv(apiCase.getEnv());
        vo.setTimeout(apiCase.getTimeout());
        vo.setIsAutomationEnabled(apiCase.getIsAutomationEnabled() != null && apiCase.getIsAutomationEnabled() == 1);
        vo.setCreatorId(apiCase.getCreatorId());
        vo.setOwnerId(apiCase.getOwnerId());
        vo.setUpdaterId(apiCase.getUpdaterId());

        // 查询关联用户名称
        if (apiCase.getCreatorId() != null) {
            User creator = userService.getById(apiCase.getCreatorId());
            if (creator != null) vo.setCreatorName(creator.getRealName());
        }
        if (apiCase.getOwnerId() != null) {
            User owner = userService.getById(apiCase.getOwnerId());
            if (owner != null) vo.setOwnerName(owner.getRealName());
        }
        if (apiCase.getUpdaterId() != null) {
            User updater = userService.getById(apiCase.getUpdaterId());
            if (updater != null) vo.setUpdaterName(updater.getRealName());
        }
        vo.setCreatedAt(apiCase.getCreatedAt());
        vo.setUpdatedAt(apiCase.getUpdatedAt());
        vo.setResponseFormat(apiCase.getResponseFormat() != null ? apiCase.getResponseFormat().name() : null);

        // 设置 applicationId 和 businessDomainId
        vo.setApplicationId(apiCase.getApplicationId());
        if (StringUtils.hasText(apiCase.getApplicationId())) {
            // 查询应用名称
            Application app = applicationService.getById(apiCase.getApplicationId());
            if (app != null) {
                vo.setApplicationName(app.getName());
            }
        }
        vo.setBusinessDomainId(apiCase.getBusinessDomainId());
        if (StringUtils.hasText(apiCase.getBusinessDomainId())) {
            // 查询业务域名称
            BusinessDomain domain = businessDomainService.getById(apiCase.getBusinessDomainId());
            if (domain != null) {
                vo.setBusinessDomainName(domain.getName());
            }
        }

        // 反序列化 JSON 字段
        try {
            if (StringUtils.hasText(apiCase.getRequestHeaders())) {
                vo.setRequestHeaders(objectMapper.readValue(apiCase.getRequestHeaders(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}));
            }
            if (StringUtils.hasText(apiCase.getRequestParams())) {
                vo.setRequestParams(objectMapper.readValue(apiCase.getRequestParams(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            }
            if (StringUtils.hasText(apiCase.getResponseAssertions())) {
                List<AssertionRuleDTO> rules = objectMapper.readValue(apiCase.getResponseAssertions(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<AssertionRuleDTO>>() {});
                // 将 AssertionRuleDTO 列表转为 AssertionRuleVO 列表
                vo.setResponseAssertions(rules.stream().map(rule -> {
                    AssertionRuleVO ruleVO = new AssertionRuleVO();
                    ruleVO.setType(rule.getType());
                    ruleVO.setDescription(rule.getDescription());
                    ruleVO.setExpression(rule.getExpression());
                    ruleVO.setExpectedValue(rule.getExpectedValue());
                    ruleVO.setOperator(rule.getOperator());
                    return ruleVO;
                }).collect(Collectors.toList()));
            }
            vo.setRequestBody(apiCase.getRequestBody());
        } catch (Exception e) {
            log.warn("反序列化用例JSON字段失败: caseId={}", apiCase.getId(), e);
        }

        // 执行结果（直接从 api_case 冗余字段读取，无需多表查询）
        vo.setIsSuccessful(apiCase.getIsSuccessful() != null && apiCase.getIsSuccessful() == 1);
        vo.setResponseBody(apiCase.getResponseBody());

        return vo;
    }


    /**
     * 校验用例是否存在且未删除
     *
     * @param caseId 用例ID
     * @return 用例对象
     */
    private ApiCase validateCaseExists(String caseId) {
        if (caseId == null || caseId.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR, "用例ID不能为空");
        }

        // @TableLogic 启用后，getById 自动过滤 deleted=1
        ApiCase apiCase = this.getById(caseId);

        if (apiCase == null) {
            throw new BusinessException(BusinessExceptionEnum.CASE_NOT_FOUND, "用例不存在或已删除: " + caseId);
        }

        return apiCase;
    }

    /**
     * 校验当前用户是否有该应用的执行权限
     *
     * @param applicationId 应用ID
     * @return 当前用户ID
     */
    private Long validateExecutionPermission(String applicationId) {
        // 从 SecurityContext 获取当前用户ID
        Long executorId = com.example.testplatform.common.util.SecurityUtil.getCurrentUserId();

        if (executorId == null) {
            throw new IllegalStateException("用户未登录，无法执行用例");
        }

        // 如果应用ID不为空，校验用户是否有该应用的执行权限
        if (applicationId != null && !applicationId.isEmpty()) {
            Application application = applicationService.getById(applicationId);

            if (application == null) {
                log.warn("应用不存在: applicationId={}", applicationId);
                // 这里可以根据业务需求决定是否抛出异常
                // 暂时不抛异常，允许继续执行
            } else {
                // 校验权限：应用负责人或部门成员可以执行
                boolean hasPermission = checkApplicationPermission(executorId, application);

                if (!hasPermission) {
                    log.warn("用户无执行权限: userId={}, applicationId={}", executorId, applicationId);
                    throw new IllegalStateException("您没有该应用的执行权限");
                }
            }
        }

        log.debug("权限校验通过: userId={}, applicationId={}", executorId, applicationId);
        return executorId;
    }

    /**
     * 检查用户对应用的执行权限
     *
     * @param userId 用户ID
     * @param application 应用对象
     * @return true=有权限，false=无权限
     */
    private boolean checkApplicationPermission(Long userId, Application application) {
        // 1. 应用负责人有权限
        if (application.getOwnerId() != null && application.getOwnerId().equals(userId)) {
            return true;
        }

        // 2. TODO: 检查用户是否属于应用所属部门
        // 可以查询 user_department 关系表
        // if (userDepartmentService.isUserInDepartment(userId, application.getDepartmentId())) {
        //     return true;
        // }

        // 3. TODO: 检查用户是否有该应用的角色权限
        // 可以查询 user_role 和 role_permission 关系表

        // 4. 默认策略：为了开发方便，暂时允许所有登录用户执行
        // 生产环境应该根据实际权限模型进行严格控制
        return true;
    }

    /**
     * 解密并解析请求快照，还原 CaseDebugRequestDTO
     *
     * @param encryptedSnapshot 加密的请求快照
     * @return CaseDebugRequestDTO
     */
    private CaseDebugRequestDTO decryptAndParseRequestSnapshot(String encryptedSnapshot) {
        try {
            // 解密快照
            String decryptedJson = aesEncryptUtil.decrypt(encryptedSnapshot);

            // 解析 JSON
            Map<String, Object> snapshot = objectMapper.readValue(decryptedJson, Map.class);

            // 构建 CaseDebugRequestDTO
            CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();

            // 提取环境（如果有）
            if (snapshot.containsKey("env")) {
                requestDTO.setEnv((String) snapshot.get("env"));
            }

            // 提取 headers
            if (snapshot.containsKey("headers")) {
                requestDTO.setHeaders((Map<String, String>) snapshot.get("headers"));
            }

            // 提取 params
            if (snapshot.containsKey("params")) {
                requestDTO.setParams((Map<String, Object>) snapshot.get("params"));
            }

            // 提取 body
            if (snapshot.containsKey("body")) {
                requestDTO.setBody(snapshot.get("body"));
            }

            // 提取 timeout
            if (snapshot.containsKey("timeout")) {
                Object timeoutObj = snapshot.get("timeout");
                if (timeoutObj instanceof Number) {
                    requestDTO.setTimeout(((Number) timeoutObj).intValue());
                }
            }

            return requestDTO;

        } catch (Exception e) {
            log.error("解密并解析请求快照失败", e);
            throw new RuntimeException("解析历史请求参数失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据应用ID分页查询用例列表
     */
    @Override
    public Page<ApiCaseVO> listByApplicationId(String applicationId, Long pageNum, Long pageSize) {
        log.info("根据应用ID分页查询用例: applicationId={}, page={}/{}", applicationId, pageNum, pageSize);

        Page<ApiCase> pageParam = new Page<>(pageNum, pageSize);

        // @TableLogic 启用后，MP 自动在所有 SELECT 上加 deleted=0，无需手动加
        QueryWrapper<ApiCase> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("application_id", applicationId);
        queryWrapper.orderByDesc("updated_at");

        Page<ApiCase> entityPage = this.page(pageParam, queryWrapper);

        // 转换为 VO 分页结果
        Page<ApiCaseVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<ApiCaseVO> voList = entityPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    /**
     * 批量调试执行用例
     * <p>
     * 接收用例 ID 列表，逐个调用 debug 方法执行调试。
     * 单个用例执行失败不会中断整体流程，异常信息会被捕获并填充到结果中。
     *
     * @param caseIds    用例 ID 列表
     * @param requestDTO 调试请求参数（环境、请求头、请求体等覆盖参数）
     * @return 每个用例的调试结果列表
     */
    @Override
    public List<BatchDebugResultVO> batchDebug(List<String> caseIds, CaseDebugRequestDTO requestDTO) {
        if (caseIds == null || caseIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR, "用例ID列表不能为空");
        }
        log.info("开始批量调试用例: count={}", caseIds.size());

        List<BatchDebugResultVO> results = caseIds.stream().map(caseId -> {
            BatchDebugResultVO.BatchDebugResultVOBuilder builder = BatchDebugResultVO.builder().caseId(caseId);
            try {
                // 校验用例是否存在并获取用例名称
                ApiCase apiCase = validateCaseExists(caseId);
                builder.caseName(apiCase.getName());

                // 调用单用例调试方法
                CaseDebugResponseVO response = debug(caseId, requestDTO);
                builder.success(response.getResult());
                builder.response(response);
            } catch (Exception e) {
                log.error("批量调试中用例执行失败: caseId={}", caseId, e);
                builder.success(false);
                builder.errorMsg(e.getMessage());
            }
            return builder.build();
        }).collect(Collectors.toList());

        log.info("批量调试完成: count={}, 成功数={}", results.size(),
                results.stream().filter(r -> Boolean.TRUE.equals(r.getSuccess())).count());
        return results;
    }

    /**
     * 从 cURL 命令导入创建 HTTP 用例
     * <p>
     * 行为说明：
     * 1. 解析 cURL 命令，提取 method/url/headers/body
     * 2. 重复判定标准：同 applicationId + 同 method + 同 address + 同 protocolType 视为同一用例
     * 3. 若已存在相同用例：返回该已存在用例的 ID（code=200），不抛异常，实现幂等导入
     * 4. 若不存在：创建新用例并返回新 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importCurl(CurlImportDTO dto) {
        log.info("开始导入cURL用例: applicationId={}", dto.getApplicationId());

        // 解析 cURL 命令
        CurlParserUtil.ParseResult parsed = CurlParserUtil.parse(dto.getCurlCommand());

        // 自动生成名称（如果未提供）
        String name = dto.getName();
        if (name == null || name.trim().isEmpty()) {
            try {
                java.net.URL urlObj = new java.net.URL(parsed.getUrl());
                String path = urlObj.getPath();
                if (path != null && !path.isEmpty() && !"/".equals(path)) {
                    String[] parts = path.split("/");
                    name = parts[parts.length - 1];
                } else {
                    name = urlObj.getHost();
                }
            } catch (Exception e) {
                name = "imported-curl-" + System.currentTimeMillis();
            }
        }

        // 映射 HTTP 方法
        RequestTypeEnum requestType = RequestTypeEnum.getByMethod(parsed.getMethod());
        if (requestType == null) {
            requestType = RequestTypeEnum.GET;
        }

        // 重复校验（判定标准：同应用 + 同方法 + 同地址 + 同协议）
        ApiCase existing = findExistingCase(
                dto.getApplicationId(), parsed.getUrl(),
                ProtocolTypeEnum.HTTP, requestType, null);
        if (existing != null) {
            // 幂等返回：导入同一个 curl 不再报错
            log.info("已存在相同配置的用例, 返回已存在用例ID: caseId={}, name={}",
                    existing.getId(), existing.getName());
            return existing.getId();
        }

        // 构建 ApiCase 实体
        ApiCase apiCase = new ApiCase();
        apiCase.setName(name);
        apiCase.setAddress(parsed.getUrl());
        apiCase.setProtocolType(ProtocolTypeEnum.HTTP.getCode());
        apiCase.setRequestType(requestType);
        // 优先使用 cURL 中指定的超时，否则默认 30 秒
        apiCase.setTimeout(parsed.getMaxTime() > 0 ? parsed.getMaxTime() * 1000 : 30000);
        apiCase.setEnv("default");
        apiCase.setApplicationId(dto.getApplicationId());
        apiCase.setResponseFormat(com.example.testplatform.enums.ResponseFormatEnum.JSON);

        // 序列化 JSON 字段
        try {
            if (parsed.getHeaders() != null && !parsed.getHeaders().isEmpty()) {
                apiCase.setRequestHeaders(objectMapper.writeValueAsString(parsed.getHeaders()));
            }
            if (parsed.getQueryParams() != null && !parsed.getQueryParams().isEmpty()) {
                apiCase.setRequestParams(objectMapper.writeValueAsString(parsed.getQueryParams()));
            }
            if (parsed.getBody() != null && !parsed.getBody().isEmpty()) {
                apiCase.setRequestBody(parsed.getBody());
            }
        } catch (Exception e) {
            log.error("序列化cURL导入用例JSON字段失败", e);
            throw new RuntimeException("序列化用例数据失败", e);
        }

        // 填充审计字段
        Long currentUserId = SecurityUtil.getCurrentUserId();
        apiCase.setCreatorId(currentUserId);
        apiCase.setOwnerId(currentUserId);
        apiCase.setIsAutomationEnabled(0);
        apiCase.setCreatedAt(LocalDateTime.now());
        apiCase.setUpdatedAt(LocalDateTime.now());
        apiCase.setDeleted(0);

        this.save(apiCase);
        log.info("cURL用例导入成功: caseId={}, name={}", apiCase.getId(), apiCase.getName());

        // 记录变更日志
        apiCaseChangeLogService.saveChangeLog(apiCase.getId(), "CREATE", null, apiCase.getName());

        // 自动同步创建 original_service 记录，确保 Apihub 列表可展示
        syncToOriginalService(apiCase, parsed);

        return apiCase.getId();
    }

    /**
     * 将新导入的 api_case 同步创建为 original_service 记录
     */
    private void syncToOriginalService(ApiCase apiCase, CurlParserUtil.ParseResult parsed) {
        try {
            OriginalService service = new OriginalService();
            service.setCaseId(apiCase.getId());
            service.setName(apiCase.getName());
            service.setProtocolType(apiCase.getProtocolType());
            service.setRequestType(apiCase.getRequestType() != null ? apiCase.getRequestType().getMethod() : null);
            service.setAddress(apiCase.getAddress());
            service.setRequestHeaders(apiCase.getRequestHeaders());
            service.setRequestBody(apiCase.getRequestBody());
            service.setIsAvailable(1);
            service.setInputParams(apiCase.getRequestParams());
            service.setOutputParams(null);
            service.setAssertions(null);
            service.setServiceType(null);
            service.setApplicationId(apiCase.getApplicationId());
            service.setCreatorId(apiCase.getCreatorId());
            service.setOwnerId(apiCase.getOwnerId());
            service.setUpdaterId(apiCase.getCreatorId());
            service.setCreatedAt(apiCase.getCreatedAt());
            service.setUpdatedAt(apiCase.getUpdatedAt());
            originalServiceService.save(service);
            log.info("cURL用例已同步创建原服务记录: originalServiceId={}, caseId={}",
                    service.getId(), apiCase.getId());
        } catch (Exception e) {
            // 同步失败不阻断主流程，仅记录日志
            log.error("同步创建原服务记录失败: caseId={}", apiCase.getId(), e);
        }
    }

    /**
     * 按 applicationId + address + protocolType + requestType 查找已存在的用例
     */
    private ApiCase findExistingCase(String applicationId, String address,
                                     ProtocolTypeEnum protocolType,
                                     RequestTypeEnum requestType, String excludeId) {
        QueryWrapper<ApiCase> qw = new QueryWrapper<>();
        qw.eq("application_id", applicationId)
                .eq("address", address)
                .eq("protocol_type", protocolType.getCode());
        if (requestType != null) {
            qw.eq("request_type", requestType);
        }
        if (StringUtils.hasText(excludeId)) {
            qw.ne("id", excludeId);
        }
        return this.getOne(qw);
    }

    @Override
    public ApiCaseStatisticsVO getStatistics() {
        // 1. 总用例数（逻辑删除自动过滤）
        long totalCaseCount = this.lambdaQuery().count();

        // 2. HTTP 用例数：protocolType = 1
        long httpCaseCount = this.lambdaQuery()
                .eq(ApiCase::getProtocolType, ProtocolTypeEnum.HTTP.getCode())
                .count();

        // 3. Dubbo 用例数：protocolType = 2
        long dubboCaseCount = this.lambdaQuery()
                .eq(ApiCase::getProtocolType, ProtocolTypeEnum.DUBBO.getCode())
                .count();

        // 4. 成功率：基于最近一次执行状态 isSuccessful = 1 的用例占比
        double successRate = 0.0;
        if (totalCaseCount > 0) {
            long successCount = this.lambdaQuery()
                    .eq(ApiCase::getIsSuccessful, 1)
                    .count();
            successRate = Math.round(successCount * 1000.0 / totalCaseCount) / 10.0;
        }

        return ApiCaseStatisticsVO.builder()
                .totalCaseCount(totalCaseCount)
                .httpCaseCount(httpCaseCount)
                .dubboCaseCount(dubboCaseCount)
                .successRate(successRate)
                .build();
    }
}
