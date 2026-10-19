package com.example.testplatform.apicase.service.impl;

import com.example.testplatform.apicase.domain.po.ApiCaseHistory;
import com.example.testplatform.common.util.AesEncryptUtil;
import com.example.testplatform.common.util.SnapshotUtil;
import com.example.testplatform.enums.ErrorTypeEnum;
import com.example.testplatform.enums.HistoryTypeEnum;
import com.example.testplatform.apicase.mapper.ApiCaseHistoryMapper;
import com.example.testplatform.apicase.service.IApiCaseHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * <p>
 * 用例测试历史表 服务实现类
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Slf4j
@Service
public class ApiCaseHistoryServiceImpl extends ServiceImpl<ApiCaseHistoryMapper, ApiCaseHistory> implements IApiCaseHistoryService {

    @Resource
    private AesEncryptUtil aesEncryptUtil;

    @Override
    public void saveHistory(String caseId, String taskId, String requestSnapshot, String responseSnapshot, boolean result, Long executorId, String env, ErrorTypeEnum errorType) {
        if (!StringUtils.hasText(caseId)) {
            throw new IllegalArgumentException("用例ID不能为空");
        }

        ApiCaseHistory history = new ApiCaseHistory();
        history.setCaseId(caseId);
        history.setTaskId(taskId);
        history.setRequestSnapshot(requestSnapshot);
        history.setResponseSnapshot(responseSnapshot);
        history.setResult(result ? 1 : 0);
        history.setExecutorId(executorId);
        history.setEnv(env);
        
        if (errorType != null) {
            history.setErrorType(errorType.getCode());
            history.setErrorTypeEnum(errorType);
        }
        
        // 设置历史记录类型为单接口测试
        history.setHistoryType(HistoryTypeEnum.SINGLE_API.getCode());
        history.setHistoryTypeEnum(HistoryTypeEnum.SINGLE_API);
        
        history.setCreatedAt(LocalDateTime.now());
        history.setUpdatedAt(LocalDateTime.now());
        history.setDeleted(0);
        
        this.save(history);
    }

    @Override
    public Page<ApiCaseHistory> listHistory(Integer page, Integer size,
                                            String caseId, String env,
                                            Boolean result, Integer historyType) {
        // @TableLogic 启用后，MP 自动过滤 deleted=1，无需手动加
        Page<ApiCaseHistory> pageParam = new Page<>(page, size);
        QueryWrapper<ApiCaseHistory> queryWrapper = new QueryWrapper<>();

        if (StringUtils.hasText(caseId)) {
            queryWrapper.eq("case_id", caseId);
        }
        if (StringUtils.hasText(env)) {
            queryWrapper.eq("env", env);
        }
        if (result != null) {
            queryWrapper.eq("result", result ? 1 : 0);
        }
        if (historyType != null) {
            queryWrapper.eq("history_type", historyType);
        }
        queryWrapper.orderByDesc("created_at");

        return this.page(pageParam, queryWrapper);
    }

    @Override
    public ApiCaseHistory getHistoryDetail(String id) {
        ApiCaseHistory history = this.getById(id);
        if (history != null) {
            decryptSnapshots(history);
        }
        return history;
    }

    @Override
    public Page<ApiCaseHistory> listByTaskId(String taskId, Integer page, Integer size) {
        // @TableLogic 启用后，MP 自动过滤 deleted=1，无需手动加
        Page<ApiCaseHistory> pageParam = new Page<>(page, size);
        QueryWrapper<ApiCaseHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("task_id", taskId)
                .orderByDesc("created_at");

        Page<ApiCaseHistory> resultPage = this.page(pageParam, queryWrapper);
        resultPage.getRecords().forEach(this::decryptSnapshots);
        return resultPage;
    }

    /**
     * 解密请求/响应快照（内部工具方法）
     * 解密后解析 body/result 字段，将转义的 JSON 字符串转换为结构化对象
     */
    private void decryptSnapshots(ApiCaseHistory history) {
        try {
            if (history.getRequestSnapshot() != null) {
                String decrypted = aesEncryptUtil.decrypt(history.getRequestSnapshot());
                history.setRequestSnapshot(SnapshotUtil.parseBodyFields(decrypted));
            }
            if (history.getResponseSnapshot() != null) {
                String decrypted = aesEncryptUtil.decrypt(history.getResponseSnapshot());
                history.setResponseSnapshot(SnapshotUtil.parseBodyFields(decrypted));
            }
        } catch (Exception e) {
            log.error("解密历史快照失败: historyId={}", history.getId(), e);
        }
    }
}
