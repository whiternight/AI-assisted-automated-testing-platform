package com.example.testplatform.execution.engine;

import com.example.testplatform.apicase.domain.dto.CaseDebugRequestDTO;
import com.example.testplatform.apicase.domain.vo.CaseDebugResponseVO;

public interface CaseExecuteEngine {

    /**
     * 执行单用例
     * @param caseId 用例ID
     * @param requestDTO 用户请求参数
     * @param executorId 执行人ID
     * @return 执行结果
     */
    CaseDebugResponseVO execute(String caseId, CaseDebugRequestDTO requestDTO, Long executorId);

    /**
     * 在批量任务上下文中执行单用例（不创建独立 Task，共享父 Task）
     * @param caseId 用例ID
     * @param requestDTO 请求参数
     * @param executorId 执行人ID
     * @param taskId 共享的父任务ID
     * @return 执行结果
     */
    CaseDebugResponseVO executeInBatch(String caseId, CaseDebugRequestDTO requestDTO, Long executorId, String taskId);
}
