package com.example.testplatform.execution.service;

import com.example.testplatform.execution.domain.po.Task;
import com.example.testplatform.execution.domain.vo.AutomationTaskVO;
import com.example.testplatform.enums.TaskStatusEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 任务表 服务类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
public interface ITaskService extends IService<Task> {

    /**
     * 创建单用例执行任务
     */
    Task createSingleCaseTask(String caseId, Long executorId, String env);

    /**
     * 更新任务状态
     */
    void updateTaskStatus(String taskId, TaskStatusEnum status,
                          int successCount, int failCount);

    /**
     * 启动自动化测试（异步执行所有用例）
     *
     * @param caseIds       用例ID列表
     * @param executorId    执行人ID
     * @param testSuiteName 用例集名称
     * @param env           执行环境
     * @param timeout       超时时间（毫秒）
     * @return 创建的任务
     */
    Task startAutomationRun(List<String> caseIds, Long executorId, String testSuiteName, String env, Integer timeout);

    /**
     * 重试失败用例
     *
     * @param taskId 任务ID
     * @return 更新后的任务
     */
    Task retryFailedCases(String taskId);

    /**
     * 重试所有用例（不管成功还是失败，全部重新执行）
     *
     * @param taskId 任务ID
     * @return 更新后的任务
     */
    Task retryAllCases(String taskId);

    /**
     * 查询自动化测试任务详情（含各用例结果）
     *
     * @param taskId 任务ID
     * @return 自动化测试详情VO
     */
    AutomationTaskVO getAutomationDetail(String taskId);

    /**
     * 分页查询自动化测试任务列表
     *
     * @param page 页码
     * @param size 每页条数
     * @return 分页任务列表
     */
    Page<Task> listAutomationTasks(Integer page, Integer size);

    /**
     * 删除自动化测试任务（逻辑删除，同时删除关联节点实例）
     *
     * @param taskId 任务ID
     */
    void deleteAutomationTask(String taskId);
}
