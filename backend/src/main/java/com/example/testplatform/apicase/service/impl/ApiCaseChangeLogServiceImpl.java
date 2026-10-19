package com.example.testplatform.apicase.service.impl;

import com.example.testplatform.apicase.domain.po.ApiCaseChangeLog;
import com.example.testplatform.apicase.mapper.ApiCaseChangeLogMapper;
import com.example.testplatform.apicase.service.IApiCaseChangeLogService;
import com.example.testplatform.common.util.SecurityUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * <p>
 * 用例变更记录表 服务实现类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-11
 */
@Service
public class ApiCaseChangeLogServiceImpl extends ServiceImpl<ApiCaseChangeLogMapper, ApiCaseChangeLog> implements IApiCaseChangeLogService {

    @Override
    public void saveChangeLog(String caseId, String action, String oldValue, String newValue) {
        ApiCaseChangeLog log = new ApiCaseChangeLog();
        log.setId(UUID.randomUUID().toString());
        log.setCaseId(caseId);
        log.setFieldName(action);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setOperatorId(SecurityUtil.getCurrentUserId());
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        log.setDeleted(0);
        this.save(log);
    }
}
