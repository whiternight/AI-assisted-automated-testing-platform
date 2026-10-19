package com.example.testplatform.execution.controller;

import com.example.testplatform.common.result.Result;
import com.example.testplatform.execution.domain.po.ExecutionLog;
import com.example.testplatform.execution.service.IExecutionLogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 执行日志表 前端控制器
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/execution-log")
public class ExecutionLogController {

    @Resource
    private IExecutionLogService executionLogService;

    /**
     * 分页查询执行日志
     * GET /execution-log?page=1&size=10&taskId=xxx&nodeInstanceId=xxx&logType=1
     */
    @GetMapping
    public Result<Page<ExecutionLog>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String nodeInstanceId,
            @RequestParam(required = false) Integer logType) {

        Page<ExecutionLog> pageParam = new Page<>(page, size);
        QueryWrapper<ExecutionLog> queryWrapper = new QueryWrapper<>();

        if (taskId != null && !taskId.isEmpty()) {
            queryWrapper.eq("task_id", taskId);
        }

        if (nodeInstanceId != null && !nodeInstanceId.isEmpty()) {
            queryWrapper.eq("node_instance_id", nodeInstanceId);
        }

        if (logType != null) {
            queryWrapper.eq("log_type", logType);
        }

        queryWrapper.orderByDesc("created_at");

        Page<ExecutionLog> resultPage = executionLogService.page(pageParam, queryWrapper);
        return Result.success(resultPage);
    }

    /**
     * 根据ID查询日志详情
     * GET /execution-log/{id}
     */
    @GetMapping("/{id}")
    public Result<ExecutionLog> getById(@PathVariable String id) {
        ExecutionLog log = executionLogService.getById(id);
        return Result.success(log);
    }
}
