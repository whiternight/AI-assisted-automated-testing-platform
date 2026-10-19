
package com.example.testplatform.auth.workflow;

import com.example.testplatform.auth.mapper.UserMapper;
import com.example.testplatform.auth.domain.po.User;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 拒绝用户代理类 - Flowable ServiceTask的执行逻辑
 *
 * 关键点：
 * 1. 实现JavaDelegate接口
 * 2. 在BPMN中配置为flowable:class
 * 3. 从流程变量获取userId
 * 4. 更新用户状态为2（已锁定/已拒绝）
 *
 * BPMN配置示例：
 * <serviceTask id="rejectUser" name="拒绝用户"
 *     flowable:class="com.example.testplatform.auth.workflow.RejectUserDelegate"/>
 */
@Component
@RequiredArgsConstructor
public class RejectUserDelegate implements JavaDelegate {

    private final UserMapper userMapper;

    /**
     * 执行拒绝逻辑
     *
     * 调用时机：当流程流转到"拒绝用户"ServiceTask时自动调用
     *
     * 关键点：
     * 1. 从流程上下文获取userId变量
     * 2. 查询用户记录
     * 3. 更新status=2（已锁定）
     * 4. 更新updatedAt时间
     *
     * 用户状态说明：
     * - status=0: 待激活（刚注册）
     * - status=1: 已激活（正常用户）
     * - status=2: 已锁定（被拒绝或多次登录失败）
     *
     * @param execution 流程执行上下文
     */
    @Override
    public void execute(DelegateExecution execution) {
        // 从流程变量中获取userId
        Long userId = execution.getVariable("userId", Long.class);

        // 查询用户
        User user = userMapper.selectById(userId);
        if (user != null) {
            // 更新用户状态为2（已锁定/已拒绝）
            user.setStatus(2);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }
}