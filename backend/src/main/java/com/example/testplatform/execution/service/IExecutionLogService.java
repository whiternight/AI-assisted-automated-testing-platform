package com.example.testplatform.execution.service;

import com.example.testplatform.execution.domain.po.ExecutionLog;
import com.example.testplatform.enums.LogTypeEnum;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.scheduling.annotation.Async;

/**
 * <p>
 * 执行日志表 服务类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
public interface IExecutionLogService extends IService<ExecutionLog> {

    /**
     * 异步保存执行日志
     */
    @Async("asyncTaskExecutor")
    void saveLogAsync(String taskId, String nodeInstanceId, LogTypeEnum logType,
                      String content, String detail);

}
