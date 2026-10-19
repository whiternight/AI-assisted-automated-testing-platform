package com.example.testplatform.execution.controller;

import com.example.testplatform.common.result.Result;
import com.example.testplatform.execution.domain.dto.AutomationRunRequestDTO;
import com.example.testplatform.execution.domain.po.Task;
import com.example.testplatform.execution.domain.vo.AutomationTaskVO;
import com.example.testplatform.execution.service.ITaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 任务表 前端控制器
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Tag(name = "自动化测试任务", description = "自动化测试启动、查询、重试接口")
@RestController
@RequestMapping("/task")
public class TaskController {

    @Resource
    private ITaskService taskService;

    /**
     * 启动自动化测试
     * POST /task/automation/run
     */
    @Operation(summary = "启动自动化测试", description = "异步执行所有用例，立即返回任务ID")
    @PostMapping("/automation/run")
    public Result<Task> startAutomationRun(@RequestBody @Valid AutomationRunRequestDTO requestDTO) {
        Task task = taskService.startAutomationRun(
                requestDTO.getCaseIds(),
                requestDTO.getExecutorId(),
                requestDTO.getTestSuiteName(),
                requestDTO.getEnv(),
                requestDTO.getTimeout()
        );
        return Result.success(task);
    }

    /**
     * 重试失败用例
     * POST /task/automation/{taskId}/retry
     */
    @Operation(summary = "重试失败用例", description = "仅重跑未通过的用例，重新计算通过率")
    @PostMapping("/automation/{taskId}/retry")
    public Result<Task> retryFailedCases(@PathVariable String taskId) {
        Task task = taskService.retryFailedCases(taskId);
        return Result.success(task);
    }

    /**
     * 重试所有用例（不管成功失败，全部重新执行）
     * POST /task/automation/{taskId}/retry-all
     */
    @Operation(summary = "重试所有用例", description = "重跑该任务下所有参与自动化的用例，执行了就算成功，重新计算通过率")
    @PostMapping("/automation/{taskId}/retry-all")
    public Result<Task> retryAllCases(@PathVariable String taskId) {
        Task task = taskService.retryAllCases(taskId);
        return Result.success(task);
    }

    /**
     * 查询自动化测试任务详情
     * GET /task/automation/{taskId}
     */
    @Operation(summary = "查询自动化测试任务详情", description = "包含执行人、通过率、每个用例的执行结果")
    @GetMapping("/automation/{taskId}")
    public Result<AutomationTaskVO> getAutomationDetail(@PathVariable String taskId) {
        return Result.success(taskService.getAutomationDetail(taskId));
    }

    /**
     * 分页查询自动化测试任务列表
     * GET /task/automation/list?page=1&size=10
     */
    @Operation(summary = "分页查询自动化测试任务列表")
    @GetMapping("/automation/list")
    public Result<Page<Task>> listAutomationTasks(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(taskService.listAutomationTasks(page, size));
    }

    /**
     * 删除自动化测试任务
     * DELETE /task/automation/{taskId}
     */
    @Operation(summary = "删除自动化测试任务", description = "逻辑删除任务及其关联节点实例")
    @DeleteMapping("/automation/{taskId}")
    public Result<Void> deleteAutomationTask(@PathVariable String taskId) {
        taskService.deleteAutomationTask(taskId);
        return Result.success(null);
    }
}
