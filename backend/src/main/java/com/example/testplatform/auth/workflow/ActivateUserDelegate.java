
package com.example.testplatform.auth.workflow;

import com.example.testplatform.auth.mapper.UserMapper;
import com.example.testplatform.auth.domain.po.User;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 激活用户代理类 - Flowable ServiceTask的执行逻辑
 *
 * 关键点：
 * 1. 实现JavaDelegate接口
 * 2. 在BPMN中配置为flowable:class
 * 3. 从流程变量获取userId
 * 4. 更新用户状态为1（已激活）
 *
 * BPMN配置示例：
 * <serviceTask id="activateUser" name="激活用户"
 *     flowable:class="com.example.testplatform.auth.workflow.ActivateUserDelegate"/>
 */
@Component
@RequiredArgsConstructor
public class ActivateUserDelegate implements JavaDelegate {

    private final UserMapper userMapper;

    /**
     * 执行激活逻辑
     *
     * 调用时机：当流程流转到"激活用户"ServiceTask时自动调用
     *
     * 关键点：
     * 1. 从流程上下文获取userId变量
     * 2. 查询用户记录
     * 3. 更新status=1（已激活）
     * 4. 更新updatedAt时间
     *
     * 流程上下文：
     * - execution可以获取所有流程变量
     * - 这些变量在启动流程或完成任务时设置
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
            // 更新用户状态为1（已激活）
            user.setStatus(1);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }
}