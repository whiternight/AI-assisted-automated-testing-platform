package com.example.testplatform.apicase.controller;

import com.example.testplatform.common.result.Result;
import com.example.testplatform.apicase.domain.po.ApiCaseHistory;
import com.example.testplatform.apicase.domain.vo.ApiCaseVO;
import com.example.testplatform.apicase.service.IApiCaseHistoryService;
import com.example.testplatform.apicase.service.IApiCaseService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 用例测试历史表 前端控制器
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/api-case-history")
public class ApiCaseHistoryController {

    @Resource
    private IApiCaseHistoryService apiCaseHistoryService;

    @Resource
    private IApiCaseService apiCaseService;

    /**
     * 分页查询执行历史
     * GET /api-case-history?page=1&size=10&caseId=xxx&env=dev&historyType=1
     */
    @GetMapping
    public Result<Page<ApiCaseHistory>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) String env,
            @RequestParam(required = false) Boolean result,
            @RequestParam(required = false) Integer historyType) {
        return Result.success(apiCaseHistoryService.listHistory(page, size, caseId, env, result, historyType));
    }

    /**
     * 根据ID查询历史记录详情（含解密）
     * GET /api-case-history/{id}
     */
    @GetMapping("/{id}")
    public Result<ApiCaseHistory> getById(@PathVariable String id) {
        return Result.success(apiCaseHistoryService.getHistoryDetail(id));
    }

    @Operation(summary = "根据应用ID分页查询用例列表")
    @GetMapping("/cases")
    public Result<Page<ApiCaseVO>> listCasesByApplicationId(
            @RequestParam String applicationId,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "20") Long pageSize) {
        return Result.success(apiCaseService.listByApplicationId(applicationId, pageNum, pageSize));
    }

    /**
     * 根据用例集（任务）ID 分页查询所有用例的执行历史
     * GET /api-case-history/by-task/{taskId}?page=1&size=10
     */
    @Operation(summary = "根据用例集ID查询所有用例历史", description = "根据任务ID分页查询该次执行中所有用例的历史记录")
    @GetMapping("/by-task/{taskId}")
    public Result<Page<ApiCaseHistory>> listByTaskId(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(apiCaseHistoryService.listByTaskId(taskId, page, size));
    }

}
