package com.example.testplatform.execution.service;

import com.example.testplatform.execution.domain.po.TaskNodeInstance;
import com.example.testplatform.enums.ErrorTypeEnum;
import com.example.testplatform.enums.NodeInstanceStatusEnum;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 任务节点实例表 服务类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
public interface ITaskNodeInstanceService extends IService<TaskNodeInstance> {

    /**
     * 创建单用例节点实例
     */
    TaskNodeInstance createInstance(String taskId, String caseId, String inputSnapshot);

    /**
     * 更新节点实例完成状态
     */
    void completeInstance(String instanceId, String outputSnapshot,
                          NodeInstanceStatusEnum status, ErrorTypeEnum errorType,
                          String errorMsg);
}
