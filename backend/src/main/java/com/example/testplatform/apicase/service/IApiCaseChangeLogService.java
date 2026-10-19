package com.example.testplatform.apicase.service;

import com.example.testplatform.apicase.domain.po.ApiCaseChangeLog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用例变更记录表 服务类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-11
 */
public interface IApiCaseChangeLogService extends IService<ApiCaseChangeLog> {

    /**
     * 保存变更日志
     */
    void saveChangeLog(String caseId, String action, String oldValue, String newValue);
}
