package com.example.testplatform.apicase.controller;


import com.example.testplatform.common.result.Result;
import com.example.testplatform.apicase.domain.po.ApiCaseChangeLog;
import com.example.testplatform.apicase.service.IApiCaseChangeLogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用例变更记录表 前端控制器
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-11
 */
@Tag(name = "变更日志", description = "用例变更记录查询接口")
@RestController
@RequestMapping("/api-case-change-log")
public class ApiCaseChangeLogController {

    @Resource
    private IApiCaseChangeLogService apiCaseChangeLogService;

    @Operation(summary = "根据用例ID查询变更日志")
    @GetMapping("/case/{caseId}")
    public Result<List<ApiCaseChangeLog>> getByCaseId(@PathVariable String caseId) {
        QueryWrapper<ApiCaseChangeLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("case_id", caseId)
                .orderByDesc("created_at");
        return Result.success(apiCaseChangeLogService.list(queryWrapper));
    }

}
