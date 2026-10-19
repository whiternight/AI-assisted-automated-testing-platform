package com.example.testplatform.execution.service.impl;

import com.example.testplatform.execution.domain.po.TaskNodeInstance;
import com.example.testplatform.enums.ErrorTypeEnum;
import com.example.testplatform.enums.NodeInstanceStatusEnum;
import com.example.testplatform.execution.mapper.TaskNodeInstanceMapper;
import com.example.testplatform.execution.service.ITaskNodeInstanceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * <p>
 * 任务节点实例表 服务实现类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Slf4j
@Service
public class TaskNodeInstanceServiceImpl extends ServiceImpl<TaskNodeInstanceMapper, TaskNodeInstance> implements ITaskNodeInstanceService {

    @Override
    public TaskNodeInstance createInstance(String taskId, String caseId, String inputSnapshot) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        if (!StringUtils.hasText(caseId)) {
            throw new IllegalArgumentException("用例ID不能为空");
        }

        TaskNodeInstance instance = new TaskNodeInstance();
        instance.setTaskId(taskId);
        instance.setCaseId(caseId);
        instance.setInputSnapshot(inputSnapshot);
        
        // 设置初始状态为待执行
        instance.setStatus(NodeInstanceStatusEnum.PENDING.getCode());
        instance.setStatusEnum(NodeInstanceStatusEnum.PENDING);
        
        instance.setRetryCount(0);
        instance.setStartTime(LocalDateTime.now());
        instance.setCreatedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        instance.setDeleted(0);
        
        this.save(instance);
        log.info("创建节点实例成功: taskId={}, caseId={}, instanceId={}", taskId, caseId, instance.getId());
        
        return instance;
    }

    @Override
    public void completeInstance(String instanceId, String outputSnapshot, NodeInstanceStatusEnum status, ErrorTypeEnum errorType, String errorMsg) {
        if (!StringUtils.hasText(instanceId)) {
            throw new IllegalArgumentException("节点实例ID不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("节点状态不能为空");
        }

        TaskNodeInstance instance = this.getById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("节点实例不存在: " + instanceId);
        }

        instance.setOutputSnapshot(outputSnapshot);
        instance.setStatus(status.getCode());
        instance.setStatusEnum(status);
        
        if (errorType != null) {
            instance.setErrorType(errorType.getCode());
            instance.setErrorTypeEnum(errorType);
        }
        
        instance.setErrorMsg(errorMsg);
        instance.setEndTime(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        
        this.updateById(instance);
        log.info("更新节点实例状态成功: instanceId={}, status={}", instanceId, status.getDesc());
    }
}
