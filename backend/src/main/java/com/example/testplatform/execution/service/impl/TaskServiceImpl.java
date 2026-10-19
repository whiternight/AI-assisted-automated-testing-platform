package com.example.testplatform.execution.service.impl;

import com.example.testplatform.apicase.domain.dto.CaseDebugRequestDTO;
import com.example.testplatform.app.domain.po.Application;
import com.example.testplatform.app.mapper.ApplicationMapper;
import com.example.testplatform.apicase.domain.po.ApiCase;
import com.example.testplatform.apicase.domain.vo.CaseDebugResponseVO;
import com.example.testplatform.apicase.mapper.ApiCaseMapper;
import com.example.testplatform.common.util.TaskNoGenerator;
import com.example.testplatform.enums.ExecuteModeEnum;
import com.example.testplatform.enums.NodeInstanceStatusEnum;
import com.example.testplatform.enums.TaskStatusEnum;
import com.example.testplatform.execution.domain.po.Task;
import com.example.testplatform.execution.domain.po.TaskNodeInstance;
import com.example.testplatform.execution.domain.vo.AutomationTaskVO;
import com.example.testplatform.execution.domain.vo.NodeResultVO;
import com.example.testplatform.execution.engine.CaseExecuteEngine;
import com.example.testplatform.execution.mapper.TaskMapper;
import com.example.testplatform.execution.service.ITaskNodeInstanceService;
import com.example.testplatform.execution.service.ITaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * <p>
 * 任务表 服务实现类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements ITaskService {

    private final TaskNoGenerator taskNoGenerator;
    @Lazy
    @Resource
    private CaseExecuteEngine caseExecuteEngine;
    private final ITaskNodeInstanceService taskNodeInstanceService;
    private final ApiCaseMapper apiCaseMapper;
    private final ApplicationMapper applicationMapper;
    private final Executor asyncTaskExecutor;

    /**
     * 根据 task.applicationId 查询 Application 表，填充 applicationName
     */
    private void fillApplicationName(Task task) {
        if (task == null || !StringUtils.hasText(task.getApplicationId())) {
            return;
        }
        Application application = applicationMapper.selectById(task.getApplicationId());
        if (application != null) {
            task.setApplicationName(application.getName());
        }
    }

    @Override
    public Task createSingleCaseTask(String caseId, Long executorId, String env) {
        if (!StringUtils.hasText(caseId)) {
            throw new IllegalArgumentException("用例ID不能为空");
        }
        if (executorId == null) {
            throw new IllegalArgumentException("执行人ID不能为空");
        }

        Task task = new Task();
        task.setTaskNo(taskNoGenerator.generate());
        task.setExecutorId(executorId);
        task.setEnv(env);
        
        // 单用例执行时测试集ID为空
        task.setTestSuiteId(null);
        
        // 设置执行模式为同步
        task.setExecuteMode(ExecuteModeEnum.SYNC.getCode());
        task.setExecuteModeEnum(ExecuteModeEnum.SYNC);
        
        // 设置初始状态为待执行
        task.setStatus(TaskStatusEnum.PENDING.getCode());
        task.setStatusEnum(TaskStatusEnum.PENDING);
        
        // 初始化计数
        task.setTotalCases(1);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setBlockedCount(0);
        task.setSkipCount(0);
        
        task.setStartTime(LocalDateTime.now());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setDeleted(0);
        
        // 从用例获取 applicationId 并填充 applicationName
        ApiCase apiCase = apiCaseMapper.selectById(caseId);
        if (apiCase != null) {
            task.setApplicationId(apiCase.getApplicationId());
        }
        fillApplicationName(task);

        this.save(task);
        log.info("创建单用例任务成功: caseId={}, taskId={}, taskNo={}", caseId, task.getId(), task.getTaskNo());
        
        return task;
    }

    @Override
    public void updateTaskStatus(String taskId, TaskStatusEnum status, int successCount, int failCount) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("任务状态不能为空");
        }

        Task task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        task.setStatus(status.getCode());
        task.setStatusEnum(status);
        task.setSuccessCount(successCount);
        task.setFailCount(failCount);
        
        // 如果任务完成，设置结束时间
        if (status == TaskStatusEnum.SUCCESS || status == TaskStatusEnum.PARTIAL_SUCCESS || 
            status == TaskStatusEnum.FAILED || status == TaskStatusEnum.BLOCKED) {
            task.setEndTime(LocalDateTime.now());
        }
        
        task.setUpdatedAt(LocalDateTime.now());
        
        this.updateById(task);
        log.info("更新任务状态成功: taskId={}, status={}, successCount={}, failCount={}", 
                taskId, status.getDesc(), successCount, failCount);
    }

    @Override
    public Task startAutomationRun(List<String> caseIds, Long executorId, String testSuiteName, String env, Integer timeout) {
        if (caseIds == null || caseIds.isEmpty()) {
            throw new IllegalArgumentException("用例ID列表不能为空");
        }
        if (executorId == null) {
            throw new IllegalArgumentException("执行人ID不能为空");
        }

        // 从第一个用例获取 applicationId
        ApiCase firstCase = apiCaseMapper.selectById(caseIds.get(0));
        String applicationId = firstCase != null ? firstCase.getApplicationId() : null;

        // 创建自动化测试任务
        Task task = new Task();
        task.setTaskNo(taskNoGenerator.generate());
        task.setExecutorId(executorId);
        task.setTestSuiteName(testSuiteName);
        task.setApplicationId(applicationId);
        task.setEnv(env != null ? env : "default");
        task.setTestSuiteId(null);
        task.setExecuteMode(ExecuteModeEnum.ASYNC.getCode());
        task.setExecuteModeEnum(ExecuteModeEnum.ASYNC);
        task.setStatus(TaskStatusEnum.RUNNING.getCode());
        task.setStatusEnum(TaskStatusEnum.RUNNING);
        task.setTotalCases(caseIds.size());
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setBlockedCount(0);
        task.setSkipCount(0);
        task.setPassRate(0.0);
        task.setRetryRound(0);
        task.setStartTime(LocalDateTime.now());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setDeleted(0);

        // 填充应用名称
        fillApplicationName(task);

        this.save(task);
        log.info("创建自动化测试任务: taskId={}, taskNo={}, totalCases={}", task.getId(), task.getTaskNo(), caseIds.size());

        // 异步执行所有用例
        executeCasesAsync(task.getId(), caseIds, executorId, env, timeout);

        return task;
    }

    /**
     * 异步并行执行所有用例
     */
    private void executeCasesAsync(String taskId, List<String> caseIds, Long executorId, String env, Integer timeout) {
        CompletableFuture<Void> outerFuture = CompletableFuture.runAsync(() -> {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            try {
                CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();
                if (env != null) requestDTO.setEnv(env);
                if (timeout != null) requestDTO.setTimeout(timeout);

                List<CompletableFuture<Void>> futures = caseIds.stream()
                        .map(caseId -> CompletableFuture.runAsync(() -> {
                            try {
                                CaseDebugResponseVO response = caseExecuteEngine.executeInBatch(
                                        caseId, requestDTO, executorId, taskId);
                                if (response.getResult() != null && response.getResult()) {
                                    successCount.incrementAndGet();
                                } else {
                                    failCount.incrementAndGet();
                                }
                            } catch (Exception e) {
                                log.error("自动化测试用例执行失败: taskId={}, caseId={}", taskId, caseId, e);
                                failCount.incrementAndGet();
                            }
                        }, asyncTaskExecutor))
                        .collect(Collectors.toList());

                // 等待所有用例执行完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // 汇总更新任务结果
                updateAutomationTaskResult(taskId, successCount.get(), failCount.get());
                log.info("自动化测试任务完成: taskId={}, success={}, fail={}", taskId, successCount.get(), failCount.get());

            } catch (Exception e) {
                log.error("自动化测试任务执行异常: taskId={}", taskId, e);
                // 确保任务不会永远停留在 RUNNING 状态
                markTaskAsFailed(taskId, e);
            }
        }, asyncTaskExecutor);

        // 外层 Future 异常兜底：防止异步线程意外终止导致任务永远 RUNNING
        outerFuture.exceptionally(ex -> {
            log.error("自动化测试异步线程意外终止: taskId={}", taskId, ex);
            markTaskAsFailed(taskId, ex);
            return null;
        });
    }

    /**
     * 将任务标记为失败（异常兜底）
     */
    private void markTaskAsFailed(String taskId, Throwable ex) {
        try {
            Task task = this.getById(taskId);
            if (task != null && task.getStatusEnum() == TaskStatusEnum.RUNNING) {
                task.setStatus(TaskStatusEnum.FAILED.getCode());
                task.setStatusEnum(TaskStatusEnum.FAILED);
                task.setEndTime(LocalDateTime.now());
                task.setUpdatedAt(LocalDateTime.now());
                this.updateById(task);
                log.info("任务已标记为失败: taskId={}", taskId);
            }
        } catch (Exception e) {
            log.error("标记任务失败时异常: taskId={}", taskId, e);
        }
    }

    /**
     * 汇总更新自动化测试任务结果
     */
    private void updateAutomationTaskResult(String taskId, int successCount, int failCount) {
        Task task = this.getById(taskId);
        if (task == null) return;

        int total = task.getTotalCases();
        task.setSuccessCount(successCount);
        task.setFailCount(failCount);
        task.setEndTime(LocalDateTime.now());

        // 计算通过率
        double passRate = total > 0
                ? BigDecimal.valueOf((double) successCount / total * 100)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        task.setPassRate(passRate);

        // 设置任务状态
        if (successCount == total) {
            task.setStatus(TaskStatusEnum.SUCCESS.getCode());
            task.setStatusEnum(TaskStatusEnum.SUCCESS);
        } else if (successCount > 0) {
            task.setStatus(TaskStatusEnum.PARTIAL_SUCCESS.getCode());
            task.setStatusEnum(TaskStatusEnum.PARTIAL_SUCCESS);
        } else {
            task.setStatus(TaskStatusEnum.FAILED.getCode());
            task.setStatusEnum(TaskStatusEnum.FAILED);
        }

        task.setUpdatedAt(LocalDateTime.now());
        this.updateById(task);
    }

    @Override
    public Task retryFailedCases(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("任务ID不能为空");
        }

        Task task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 查询该任务下所有节点，取每个 caseId 的最新一条
        // @TableLogic 启用后，MP 自动过滤 deleted=1
        List<TaskNodeInstance> allNodes = taskNodeInstanceService.list(
                new LambdaQueryWrapper<TaskNodeInstance>()
                        .eq(TaskNodeInstance::getTaskId, taskId)
                        .orderByDesc(TaskNodeInstance::getCreatedAt)
        );

        Map<String, TaskNodeInstance> latestNodeMap = new LinkedHashMap<>();
        for (TaskNodeInstance node : allNodes) {
            latestNodeMap.putIfAbsent(node.getCaseId(), node);
        }

        // 筛选出最新状态为 FAILED 的用例
        List<String> failedCaseIds = latestNodeMap.entrySet().stream()
                .filter(e -> e.getValue().getStatus() != null &&
                        e.getValue().getStatus() == NodeInstanceStatusEnum.FAILED.getCode())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (failedCaseIds.isEmpty()) {
            log.info("没有失败的用例需要重试: taskId={}", taskId);
            return task;
        }

        // 更新重试轮次，状态置为执行中
        task.setRetryRound(task.getRetryRound() != null ? task.getRetryRound() + 1 : 1);
        task.setStatus(TaskStatusEnum.RUNNING.getCode());
        task.setStatusEnum(TaskStatusEnum.RUNNING);
        task.setStartTime(LocalDateTime.now());
        task.setEndTime(null);
        task.setUpdatedAt(LocalDateTime.now());
        this.updateById(task);

        // 填充应用名称
        fillApplicationName(task);

        log.info("开始重试失败用例: taskId={}, retryRound={}, failedCount={}",
                taskId, task.getRetryRound(), failedCaseIds.size());

        // 异步执行重试
        retryCasesAsync(task.getId(), failedCaseIds, task.getExecutorId(), task.getEnv());

        return task;
    }

    /**
     * 异步重试失败用例
     */
    private void retryCasesAsync(String taskId, List<String> failedCaseIds, Long executorId, String env) {
        CompletableFuture.runAsync(() -> {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();
            if (env != null) requestDTO.setEnv(env);

            List<CompletableFuture<Void>> futures = failedCaseIds.stream()
                    .map(caseId -> CompletableFuture.runAsync(() -> {
                        try {
                            CaseDebugResponseVO response = caseExecuteEngine.executeInBatch(
                                    caseId, requestDTO, executorId, taskId);
                            if (response.getResult() != null && response.getResult()) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            log.error("重试用例执行失败: taskId={}, caseId={}", taskId, caseId, e);
                            failCount.incrementAndGet();
                        }
                    }, asyncTaskExecutor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 重试完成后，基于每个用例的最新节点状态重新计算通过率
            updateRetryTaskResult(taskId);
            log.info("重试完成: taskId={}, retrySuccess={}, retryFail={}", taskId, successCount.get(), failCount.get());

        }, asyncTaskExecutor);
    }

    @Override
    public Task retryAllCases(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("任务ID不能为空");
        }

        Task task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 查询该任务下所有节点，取每个 caseId 的最新一条，收集所有参与自动化的用例ID
        // @TableLogic 启用后，MP 自动过滤 deleted=1
        List<TaskNodeInstance> allNodes = taskNodeInstanceService.list(
                new LambdaQueryWrapper<TaskNodeInstance>()
                        .eq(TaskNodeInstance::getTaskId, taskId)
                        .orderByDesc(TaskNodeInstance::getCreatedAt)
        );

        Map<String, TaskNodeInstance> latestNodeMap = new LinkedHashMap<>();
        for (TaskNodeInstance node : allNodes) {
            latestNodeMap.putIfAbsent(node.getCaseId(), node);
        }

        // 获取所有参与该自动化测试的用例ID（不筛选状态，全部重跑）
        List<String> allCaseIds = new java.util.ArrayList<>(latestNodeMap.keySet());

        if (allCaseIds.isEmpty()) {
            log.info("没有用例需要重试: taskId={}", taskId);
            return task;
        }

        // 更新重试轮次，状态置为执行中
        task.setRetryRound(task.getRetryRound() != null ? task.getRetryRound() + 1 : 1);
        task.setStatus(TaskStatusEnum.RUNNING.getCode());
        task.setStatusEnum(TaskStatusEnum.RUNNING);
        task.setStartTime(LocalDateTime.now());
        task.setEndTime(null);
        task.setUpdatedAt(LocalDateTime.now());
        this.updateById(task);

        // 填充应用名称
        fillApplicationName(task);

        log.info("开始重试所有用例: taskId={}, retryRound={}, totalCount={}",
                taskId, task.getRetryRound(), allCaseIds.size());

        // 异步执行所有用例重试
        retryCasesAsync(task.getId(), allCaseIds, task.getExecutorId(), task.getEnv());

        return task;
    }

    /**
     * 重试完成后重新计算通过率
     */
    private void updateRetryTaskResult(String taskId) {
        Task task = this.getById(taskId);
        if (task == null) return;

        // 查询该任务下所有节点
        // @TableLogic 启用后，MP 自动过滤 deleted=1
        List<TaskNodeInstance> allNodes = taskNodeInstanceService.list(
                new LambdaQueryWrapper<TaskNodeInstance>()
                        .eq(TaskNodeInstance::getTaskId, taskId)
                        .orderByDesc(TaskNodeInstance::getCreatedAt)
        );

        // 每个 caseId 取最新一条节点来判定最终状态
        Map<String, TaskNodeInstance> latestNodeMap = new LinkedHashMap<>();
        for (TaskNodeInstance node : allNodes) {
            latestNodeMap.putIfAbsent(node.getCaseId(), node);
        }

        int total = task.getTotalCases();
        // 通过率计算：passed=1（HTTP 2xx）才算通过，而不是仅看 status==COMPLETED
        long passedCount = latestNodeMap.values().stream()
                .filter(n -> n.getPassed() != null && n.getPassed() == 1)
                .count();

        int currentSuccess = (int) passedCount;
        int currentFail = total - currentSuccess;

        task.setSuccessCount(currentSuccess);
        task.setFailCount(currentFail);
        task.setEndTime(LocalDateTime.now());

        double passRate = total > 0
                ? BigDecimal.valueOf((double) currentSuccess / total * 100)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        task.setPassRate(passRate);

        if (currentSuccess == total) {
            task.setStatus(TaskStatusEnum.SUCCESS.getCode());
            task.setStatusEnum(TaskStatusEnum.SUCCESS);
        } else if (currentSuccess > 0) {
            task.setStatus(TaskStatusEnum.PARTIAL_SUCCESS.getCode());
            task.setStatusEnum(TaskStatusEnum.PARTIAL_SUCCESS);
        } else {
            task.setStatus(TaskStatusEnum.FAILED.getCode());
            task.setStatusEnum(TaskStatusEnum.FAILED);
        }

        task.setUpdatedAt(LocalDateTime.now());
        this.updateById(task);
    }

    @Override
    public AutomationTaskVO getAutomationDetail(String taskId) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 查询该任务下所有节点
        // @TableLogic 启用后，MP 自动过滤 deleted=1
        List<TaskNodeInstance> nodes = taskNodeInstanceService.list(
                new LambdaQueryWrapper<TaskNodeInstance>()
                        .eq(TaskNodeInstance::getTaskId, taskId)
                        .orderByAsc(TaskNodeInstance::getCreatedAt)
        );

        // 构建每个用例的执行结果
        List<NodeResultVO> nodeResults = nodes.stream().map(node -> {
            String caseName = null;
            try {
                ApiCase apiCase = apiCaseMapper.selectById(node.getCaseId());
                if (apiCase != null) caseName = apiCase.getName();
            } catch (Exception ignored) {}

            NodeInstanceStatusEnum statusEnum = NodeInstanceStatusEnum.getByCode(
                    node.getStatus() != null ? node.getStatus() : 0);

            return NodeResultVO.builder()
                    .nodeInstanceId(node.getId())
                    .caseId(node.getCaseId())
                    .caseName(caseName)
                    .status(node.getStatus())
                    .statusDesc(statusEnum != null ? statusEnum.getDesc() : "未知")
                    .success(node.getStatus() != null &&
                            node.getStatus() == NodeInstanceStatusEnum.COMPLETED.getCode())
                    .passed(node.getPassed())
                    .retryCount(node.getRetryCount())
                    .errorType(node.getErrorTypeEnum() != null ?
                            node.getErrorTypeEnum().name() : null)
                    .errorMsg(node.getErrorMsg())
                    .startTime(node.getStartTime())
                    .endTime(node.getEndTime())
                    .build();
        }).collect(Collectors.toList());

        TaskStatusEnum taskStatusEnum = TaskStatusEnum.getByCode(
                task.getStatus() != null ? task.getStatus() : 0);

        // 填充应用名称
        fillApplicationName(task);

        return AutomationTaskVO.builder()
                .taskId(task.getId())
                .taskNo(task.getTaskNo())
                .applicationName(task.getApplicationName())
                .testSuiteName(task.getTestSuiteName())
                .executorId(task.getExecutorId())
                .status(task.getStatus())
                .statusDesc(taskStatusEnum != null ? taskStatusEnum.getDesc() : "未知")
                .totalCases(task.getTotalCases())
                .successCount(task.getSuccessCount())
                .failCount(task.getFailCount())
                .passRate(task.getPassRate())
                .retryRound(task.getRetryRound())
                .env(task.getEnv())
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .nodeResults(nodeResults)
                .build();
    }

    @Override
    public Page<Task> listAutomationTasks(Integer page, Integer size) {
        // @TableLogic 启用后，MP 自动过滤 deleted=1，无需手动加
        Page<Task> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getExecuteMode, ExecuteModeEnum.ASYNC.getCode())
                .orderByDesc(Task::getCreatedAt);
        Page<Task> result = this.page(pageParam, wrapper);
        result.getRecords().forEach(this::fillApplicationName);
        return result;
    }
    @Override
    public void deleteAutomationTask(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("任务ID不能为空");
        }

        Task task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 逻辑删除关联的节点实例（TaskNodeInstance 已启用 @TableLogic）
        LambdaUpdateWrapper<TaskNodeInstance> nodeWrapper = new LambdaUpdateWrapper<TaskNodeInstance>()
                .eq(TaskNodeInstance::getTaskId, taskId)
                .eq(TaskNodeInstance::getDeleted, 0)
                .set(TaskNodeInstance::getDeleted, 1)
                .set(TaskNodeInstance::getUpdatedAt, LocalDateTime.now());
        taskNodeInstanceService.update(nodeWrapper);

        // 逻辑删除任务（Task 已启用 @TableLogic）
        boolean removed = this.removeById(taskId);
        if (!removed) {
            throw new IllegalArgumentException("任务已被删除: " + taskId);
        }

        log.info("删除自动化测试任务成功: taskId={}", taskId);
    }
}
