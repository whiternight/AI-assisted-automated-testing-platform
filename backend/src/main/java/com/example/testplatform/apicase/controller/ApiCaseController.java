package com.example.testplatform.apicase.controller;


import com.example.testplatform.common.result.Result;
import com.example.testplatform.apicase.domain.dto.*;
import com.example.testplatform.apicase.domain.vo.ApiCaseStatisticsVO;
import com.example.testplatform.apicase.domain.vo.ApiCaseVO;
import com.example.testplatform.apicase.domain.vo.BatchDebugResultVO;
import com.example.testplatform.apicase.domain.vo.CaseDebugResponseVO;
import com.example.testplatform.apicase.domain.vo.DuplicateCheckResultVO;
import com.example.testplatform.apicase.service.IApiCaseService;
import com.example.testplatform.common.util.SecurityUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用例主表 前端控制器
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Tag(name = "用例管理", description = "ApiHub HTTP/Dubbo用例CRUD接口")
@RestController
@RequestMapping("/api-case")
public class ApiCaseController {

    @Resource
    private IApiCaseService apiCaseService;

    /**
     * 单用例调试执行
     * POST /api/case/{id}/debug
     */
    @PostMapping("/{id}/debug")
    public Result<CaseDebugResponseVO> debug(
            @PathVariable("id") String caseId,
            @RequestBody @Valid CaseDebugRequestDTO requestDTO) {
        // 从 SecurityContext 获取当前用户ID
        Long executorId = SecurityUtil.getCurrentUserId();
        CaseDebugResponseVO response = apiCaseService.debug(caseId, requestDTO);
        return Result.success(response);
    }

    /**
     * 重试历史用例
     * POST /api-case/{historyId}/retry
     */
    @PostMapping("/{historyId}/retry")
    public Result<CaseDebugResponseVO> retry(@PathVariable("historyId") String historyId) {
        CaseDebugResponseVO response = apiCaseService.retry(historyId);
        return Result.success(response);
    }

    // 只保留 POST 接口，更统一、更安全
    @PostMapping("/check-duplicate")
    public Result<DuplicateCheckResultVO> checkDuplicate(
            @RequestBody @Valid DuplicateCheckRequestDTO requestDTO) {
        // 统一逻辑
        return Result.success(apiCaseService.checkDuplicate(
                requestDTO.getApplicationId(),
                requestDTO.getAddress(),
                requestDTO.getProtocolType(),
                requestDTO.getExcludeId()
        ));
    }

    // ========== 新增接口 ==========

    @Operation(summary = "新增HTTP用例")
    @PostMapping("/http")
    public Result<String> createHttpCase(@RequestBody @Valid HttpCaseCreateDTO dto) {
        String caseId = apiCaseService.createHttpCase(dto);
        return Result.success(caseId);
    }

    @Operation(summary = "新增Dubbo用例")
    @PostMapping("/dubbo")
    public Result<String> createDubboCase(@RequestBody @Valid DubboCaseCreateDTO dto) {
        String caseId = apiCaseService.createDubboCase(dto);
        return Result.success(caseId);
    }

    // ========== 更新接口 ==========

    @Operation(summary = "更新HTTP用例")
    @PutMapping("/http")
    public Result<Void> updateHttpCase(@RequestBody @Valid HttpCaseUpdateDTO dto) {
        apiCaseService.updateHttpCase(dto);
        return Result.success();
    }

    @Operation(summary = "更新Dubbo用例")
    @PutMapping("/dubbo")
    public Result<Void> updateDubboCase(@RequestBody @Valid DubboCaseUpdateDTO dto) {
        apiCaseService.updateDubboCase(dto);
        return Result.success();
    }

    // ========== 查询接口 ==========

    @Operation(summary = "根据ID查询用例详情")
    @GetMapping("/{id}")
    public Result<ApiCaseVO> getCaseById(@PathVariable String id) {
        return Result.success(apiCaseService.getCaseById(id));
    }

    @Operation(summary = "分页查询用例列表")
    @GetMapping("/list")
    public Result<Page<ApiCaseVO>> listCases(@Valid @ModelAttribute ApiCaseQueryDTO queryDTO) {
        return Result.success(apiCaseService.listCases(queryDTO));
    }

    // ========== 删除接口 ==========

    @Operation(summary = "逻辑删除用例")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCase(@PathVariable String id) {
        apiCaseService.deleteCase(id);
        return Result.success();
    }

    @Operation(summary = "批量删除用例")
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteCases(@RequestBody List<String> ids) {
        apiCaseService.batchDeleteCases(ids);
        return Result.success();
    }

    // ========== 自动化开关接口 ==========

    @Operation(summary = "单条更新自动化开关")
    @PatchMapping("/{id}/auto")
    public Result<Void> updateAutomationFlag(
            @PathVariable String id,
            @RequestParam Boolean isAutomationEnabled) {
        apiCaseService.updateAutomationFlag(id, isAutomationEnabled);
        return Result.success();
    }

    @Operation(summary = "批量更新自动化开关")
    @PatchMapping("/batch/auto")
    public Result<Void> batchUpdateAutomationFlag(
            @RequestBody List<String> ids,
            @RequestParam Boolean isAutomationEnabled) {
        apiCaseService.batchUpdateAutomationFlag(ids, isAutomationEnabled);
        return Result.success();
    }

    // ========== 负责人变更接口 ==========

    @Operation(summary = "批量变更负责人")
    @PutMapping("/batch/owner")
    public Result<Void> batchUpdateOwner(
            @RequestBody List<String> ids,
            @RequestParam Long ownerId) {
        apiCaseService.batchUpdateOwner(ids, ownerId);
        return Result.success();
    }

    // ========== 批量调试接口 ==========

    /**
     * 批量调试执行用例
     * POST /api-case/batch/debug
     * <p>
     * 接收前端传递的用例 ID 数组，逐个执行调试，
     * 单个用例失败不影响其他用例执行。
     */
    @Operation(summary = "批量调试执行用例", description = "接收用例ID数组，逐个执行调试，单个失败不影响其他用例")
    @PostMapping("/batch/debug")
    public Result<List<BatchDebugResultVO>> batchDebug(
            @RequestBody List<String> caseIds,
            @RequestParam(required = false) String env,
            @RequestParam(required = false) Integer timeout) {
        // 构建调试请求参数
        CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();
        if (env != null) requestDTO.setEnv(env);
        if (timeout != null) requestDTO.setTimeout(timeout);

        List<BatchDebugResultVO> results = apiCaseService.batchDebug(caseIds, requestDTO);
        return Result.success(results);
    }

    // ========== cURL 导入/导出接口 ==========

    @Operation(summary = "导入cURL命令创建用例", description = "解析cURL命令，自动创建HTTP用例")
    @PostMapping("/import-curl")
    public Result<String> importCurl(@RequestBody @Valid CurlImportDTO dto) {
        String caseId = apiCaseService.importCurl(dto);
        return Result.success(caseId);
    }

    @Operation(summary = "导出用例为cURL命令", description = "根据用例ID生成对应的cURL命令字符串")
    @GetMapping("/{id}/export-curl")
    public Result<String> exportCurl(@PathVariable String id) {
        ApiCaseVO caseVO = apiCaseService.getCaseById(id);
        if (caseVO == null) {
            return Result.error("用例不存在");
        }

        // 清洗 URL：去除反引号、`{{...}}`、`${...}`、多余花括号等模板占位符
        String cleanedAddress = cleanCurlAddress(caseVO.getAddress());

        StringBuilder sb = new StringBuilder();
        String method = caseVO.getRequestType() != null ? caseVO.getRequestType() : "GET";
        sb.append("curl -X ").append(method).append(" \\\n");
        sb.append("  '").append(cleanedAddress.replace("'", "'\\''")).append("'");

        // 拼接请求头
        if (caseVO.getRequestHeaders() != null && !caseVO.getRequestHeaders().isEmpty()) {
            for (Map.Entry<String, String> entry : caseVO.getRequestHeaders().entrySet()) {
                String key = entry.getKey().replace("'", "'\\''");
                String value = entry.getValue() == null ? "" : entry.getValue().replace("'", "'\\''");
                sb.append(" \\\n  -H '").append(key).append(": ").append(value).append("'");
            }
        }

        // 拼接 URL 查询参数（若 requestParams 存在）
        if (caseVO.getRequestParams() != null && !caseVO.getRequestParams().isEmpty()) {
            for (Map.Entry<String, Object> entry : caseVO.getRequestParams().entrySet()) {
                String key = entry.getKey().replace("'", "'\\''");
                String value = entry.getValue() == null ? "" : entry.getValue().toString().replace("'", "'\\''");
                sb.append(" \\\n  -G -d '").append(key).append("=").append(value).append("'");
            }
        }

        // 拼接请求体
        if (caseVO.getRequestBody() != null && !caseVO.getRequestBody().isEmpty()) {
            String body = caseVO.getRequestBody();
            body = body.replace("'", "'\\''");
            sb.append(" \\\n  -d '").append(body).append("'");
        }

        // 拼接超时
        if (caseVO.getTimeout() != null && caseVO.getTimeout() > 0) {
            sb.append(" \\\n  --max-time ").append(caseVO.getTimeout() / 1000);
        }

        // 拼接断言信息（以 cURL 注释形式附加在命令末尾，保留完整可复现性）
        appendAssertionsAsComments(sb, caseVO);

        return Result.success(sb.toString());
    }

    /**
     * 将断言规则以 cURL 注释（#）形式追加到命令末尾
     * 注释形式保证：
     * 1) cURL 仍可被复制后直接执行
     * 2) 断言信息以易读方式保留：# 断言类型 | 描述 | 表达式 | 操作符 | 期望值
     */
    private void appendAssertionsAsComments(StringBuilder sb, ApiCaseVO caseVO) {
        if (caseVO.getResponseAssertions() == null || caseVO.getResponseAssertions().isEmpty()) {
            return;
        }
        sb.append(" \\\n  \\\n  # ===== 断言（").append(caseVO.getResponseAssertions().size()).append("条） =====");
        int idx = 1;
        for (com.example.testplatform.apicase.domain.vo.AssertionRuleVO rule : caseVO.getResponseAssertions()) {
            String type = nullSafe(rule.getType());
            String description = nullSafe(rule.getDescription());
            String expression = nullSafe(rule.getExpression());
            String operator = rule.getOperator() == null || rule.getOperator().isEmpty()
                    ? "EQUALS" : rule.getOperator();
            String expected = nullSafe(rule.getExpectedValue());

            sb.append(" \\\n  # 断言").append(idx++)
                    .append(" [").append(type).append(']')
                    .append(" 描述: ").append(description)
                    .append(" 表达式: ").append(expression)
                    .append(" 操作符: ").append(operator)
                    .append(" 期望值: ").append(expected);
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s.replace("'", "'\\''");
    }

    /**
     * 清洗用例地址：去除反引号、`{{...}}`、`${...}`、多余花括号等模板占位符
     */
    private String cleanCurlAddress(String raw) {
        if (raw == null) {
            return "";
        }
        String url = raw.trim();
        // 去除最外层花括号
        while (url.length() >= 2 && url.startsWith("{") && url.endsWith("}")) {
            url = url.substring(1, url.length() - 1).trim();
        }
        // 去除反引号
        url = url.replace("`", "");
        // {{xxx}} -> xxx
        url = url.replaceAll("\\{\\{([\\s\\S]+?)\\}\\}", "$1");
        // ${xxx} -> xxx
        url = url.replaceAll("\\$\\{([\\s\\S]+?)\\}", "$1");
        // 去除剩余的 { 或 }
        url = url.replaceAll("[{}]", "");
        // 去除空白
        url = url.replaceAll("\\s+", "");
        return url;
    }

    // ========== 首页统计接口 ==========

    @Operation(summary = "首页用例统计概览", description = "统计总用例数、HTTP/Dubbo 用例数及最近一次执行成功率")
    @GetMapping("/statistics/overview")
    public Result<ApiCaseStatisticsVO> getStatistics() {
        return Result.success(apiCaseService.getStatistics());
    }

}
