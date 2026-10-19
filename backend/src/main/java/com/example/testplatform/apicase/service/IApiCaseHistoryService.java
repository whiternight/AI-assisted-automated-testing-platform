package com.example.testplatform.apicase.service;

import com.example.testplatform.apicase.domain.po.ApiCaseHistory;
import com.example.testplatform.enums.ErrorTypeEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用例测试历史表 服务类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
public interface IApiCaseHistoryService extends IService<ApiCaseHistory> {

    /**
     * 保存单用例执行历史
     */
    void saveHistory(String caseId, String taskId, String requestSnapshot,
                     String responseSnapshot, boolean result, Long executorId,
                     String env, ErrorTypeEnum errorType);

    /**
     * 分页查询执行历史（支持多条件筛选）
     */
    Page<ApiCaseHistory> listHistory(Integer page, Integer size,
                                     String caseId, String env,
                                     Boolean result, Integer historyType);

    /**
     * 根据ID查询历史记录详情（含快照解密）
     */
    ApiCaseHistory getHistoryDetail(String id);

    /**
     * 根据任务ID分页查询所有用例历史（含快照解密）
     */
    Page<ApiCaseHistory> listByTaskId(String taskId, Integer page, Integer size);
}
