
package com.example.testplatform.auth.service;

import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable工作流服务 - 管理用户激活审批流程
 *
 * 关键点：
 * 1. 启动用户激活流程（用户注册时）
 * 2. 查询待审批任务（管理员查看）
 * 3. 完成审批任务（批准/拒绝）
 * 4. 流程变量传递用户信息
 *
 * BPMN流程说明：
 * 开始 → 管理员审批任务 → 排他网关 → 批准/拒绝 → 结束
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final RuntimeService runtimeService;  // 运行时服务（启动流程）
    private final TaskService taskService;        // 任务服务（操作任务）

    /**
     * 启动用户激活流程
     *
     * 调用时机：用户注册成功后自动调用
     *
     * 关键点：
     * 1. 使用流程key启动流程实例
     * 2. 传递流程变量（userId、username）
     * 3. 流程变量在整个流程中可用
     * 4. 返回流程实例ID用于追踪
     *
     * 流程变量说明：
     * - userId: 用于ServiceTask中更新用户状态
     * - username: 用于任务描述，方便管理员识别
     *
     * @param userId 用户ID
     * @param username 用户名
     * @return 流程实例ID
     */
    @Transactional
    public String startUserActivationProcess(Long userId, String username) {
        // 准备流程变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", userId);        // 用户ID（用于激活/拒绝时更新状态）
        variables.put("username", username);    // 用户名（用于任务描述）

        // 启动流程实例
        // 关键点：process key必须与BPMN文件中的process id一致
        String processInstanceId = runtimeService
                .startProcessInstanceByKey("userActivationProcess", variables)
                .getId();

        return processInstanceId;
    }

    /**
     * 获取待审批任务列表
     *
     * 调用时机：管理员打开审批页面时
     *
     * 关键点：
     * 1. 按候选组查询（ROLE_ADMIN）
     * 2. 按创建时间倒序排列
     * 3. 返回任务列表供管理员选择
     *
     * BPMN配置：
     * 在BPMN文件中，userTask设置了flowable:candidateGroups="ROLE_ADMIN"
     * 表示ROLE_ADMIN角色组的成员都可以看到这个任务
     *
     * @return 待审批任务列表
     */
    public List<Task> getPendingApprovalTasks() {
        return taskService.createTaskQuery()
                .taskCandidateGroup("ROLE_ADMIN")  // 查询ROLE_ADMIN角色组的任务
                .orderByTaskCreateTime()            // 按创建时间排序
                .desc()                             // 倒序（最新的在前）
                .list();
    }

    /**
     * 完成审批任务（批准或拒绝）
     *
     * 调用时机：管理员点击"批准"或"拒绝"按钮时
     *
     * 关键点：
     * 1. 先查询任务是否存在
     * 2. 设置流程变量approvalResult
     * 3. 完成任务，流程继续流转
     * 4. 根据approvalResult走不同分支
     *
     * 流程分支：
     * - approvalResult='approved' → 激活用户ServiceTask
     * - approvalResult='rejected' → 拒绝用户ServiceTask
     *
     * @param taskId 任务ID
     * @param approvalResult 审批结果（approved/rejected）
     */
    @Transactional
    public void approveUser(String taskId, String approvalResult) {
        // 查询任务
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        // 设置流程变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("approvalResult", approvalResult);  // 审批结果

        // 完成任务
        // 关键点：完成后流程会根据排他网关的条件流向不同分支
        taskService.complete(taskId, variables);
    }

    /**
     * 拒绝用户（便捷方法）
     *
     * 关键点：内部调用approveUser方法，传入rejected参数
     *
     * @param taskId 任务ID
     */
    @Transactional
    public void rejectUser(String taskId) {
        approveUser(taskId, "rejected");
    }
}