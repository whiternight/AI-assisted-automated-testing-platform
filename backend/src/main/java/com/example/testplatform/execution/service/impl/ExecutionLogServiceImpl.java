package com.example.testplatform.execution.service.impl;

import com.example.testplatform.execution.domain.po.ExecutionLog;
import com.example.testplatform.enums.LogTypeEnum;
import com.example.testplatform.execution.mapper.ExecutionLogMapper;
import com.example.testplatform.execution.service.IExecutionLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * <p>
 * 执行日志表 服务实现类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Slf4j
@Service
public class ExecutionLogServiceImpl extends ServiceImpl<ExecutionLogMapper, ExecutionLog> implements IExecutionLogService {

    @Override
    public void saveLogAsync(String taskId, String nodeInstanceId, LogTypeEnum logType, String content, String detail) {
        try {
            if (!StringUtils.hasText(taskId)) {
                log.warn("执行日志保存失败：任务ID不能为空");
                return;
            }

            ExecutionLog executionLog = new ExecutionLog();
            executionLog.setTaskId(taskId);
            executionLog.setNodeInstanceId(nodeInstanceId);
            
            if (logType != null) {
                executionLog.setLogType(logType.getCode());
                executionLog.setLogTypeEnum(logType);
            }
            
            executionLog.setContent(content);
            executionLog.setDetail(detail);
            executionLog.setCreatedAt(LocalDateTime.now());
            executionLog.setUpdatedAt(LocalDateTime.now());
            executionLog.setDeleted(0);
            
            this.save(executionLog);
            log.debug("执行日志保存成功: taskId={}, logType={}", taskId, logType != null ? logType.getDesc() : "unknown");
        } catch (Exception e) {
            log.error("执行日志保存失败: taskId={}, logType={}", taskId, logType != null ? logType.getDesc() : "unknown", e);
        }
    }
}
