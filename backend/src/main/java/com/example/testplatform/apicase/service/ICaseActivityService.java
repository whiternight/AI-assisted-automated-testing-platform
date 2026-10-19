package com.example.testplatform.apicase.service;

import com.example.testplatform.apicase.domain.po.CaseActivity;
import com.example.testplatform.apicase.domain.vo.CaseActivityVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 用例活跃度表 服务类
 * </p>
 *
 * @author author
 * @since 2026-06-18
 */
public interface ICaseActivityService extends IService<CaseActivity> {

    /**
     * 记录单用例调试活跃（更新 last_debug_time，不存在则新建）
     */
    void recordDebugActivity(String caseId);

    /**
     * 记录流程引用执行活跃（更新 last_flow_execute_time）
     */
    void recordFlowExecuteActivity(String caseId);

    /**
     * 记录测试集执行活跃（更新 last_suite_execute_time）
     */
    void recordSuiteExecuteActivity(String caseId);

    /**
     * 记录手动查看活跃（更新 last_view_time）
     */
    void recordViewActivity(String caseId);

    /**
     * 查询单个用例活跃度详情
     */
    CaseActivityVO getActivityByCaseId(String caseId);

    /**
     * 查询活跃度列表（支持按应用、活跃状态筛选）
     */
    List<CaseActivityVO> listActivities(String applicationId, Boolean active);

    /**
     * 查询活跃度统计概览
     */
    CaseActivityVO getActivityStats(String applicationId);

    /**
     * 执行逻辑删除：将7天内未活跃的用例标记为已删除
     *
     * @return 被逻辑删除的记录数
     */
    int logicalDeleteInactiveCases();

    /**
     * 为用例初始化活跃度记录（如已有则跳过）
     */
    void initActivityIfAbsent(String caseId);
}
