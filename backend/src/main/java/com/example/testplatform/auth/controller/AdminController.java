package com.example.testplatform.auth.controller;

import com.example.testplatform.common.result.Result;
import com.example.testplatform.auth.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "管理员审批", description = "用户激活审批接口")
public class AdminController {

    private final WorkflowService workflowService;

    @GetMapping("/pending-tasks")
    @Operation(summary = "获取待审批任务列表")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Map<String, Object>>> getPendingTasks() {
        List<Task> tasks = workflowService.getPendingApprovalTasks();

        List<Map<String, Object>> taskList = tasks.stream().map(task -> {
            Map<String, Object> map = new HashMap<>();
            map.put("taskId", task.getId());
            map.put("taskName", task.getName());
            map.put("createTime", task.getCreateTime());
            map.put("processInstanceId", task.getProcessInstanceId());
            return map;
        }).collect(Collectors.toList());

        return Result.success(taskList);
    }

    @PostMapping("/approve")
    @Operation(summary = "审批通过")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> approve(@RequestBody ApprovalRequest request) {
        try {
            workflowService.approveUser(request.getTaskId(), "approved");
            return Result.success("审批通过，用户已激活");
        } catch (Exception e) {
            return Result.error("审批失败: " + e.getMessage());
        }
    }

    @PostMapping("/reject")
    @Operation(summary = "审批拒绝")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> reject(@RequestBody ApprovalRequest request) {
        try {
            workflowService.rejectUser(request.getTaskId());
            return Result.success("审批拒绝，用户已被锁定");
        } catch (Exception e) {
            return Result.error("拒绝失败: " + e.getMessage());
        }
    }

    @Data
    static class ApprovalRequest {
        private String taskId;
    }
}