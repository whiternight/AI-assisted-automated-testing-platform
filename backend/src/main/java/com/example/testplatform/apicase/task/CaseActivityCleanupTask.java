package com.example.testplatform.apicase.task;

import com.example.testplatform.apicase.service.ICaseActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 用例活跃度定时清理任务
 * <p>
 * 每天凌晨 02:00 执行，将7天内未被任何维度调用的用例进行逻辑删除。
 * </p>
 *
 * @author TestPlatform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseActivityCleanupTask {

    private final ICaseActivityService caseActivityService;

    /**
     * 每天凌晨 02:00 执行逻辑删除
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupInactiveCases() {
        log.info("【定时任务】开始执行用例活跃度清理...");
        try {
            int count = caseActivityService.logicalDeleteInactiveCases();
            log.info("【定时任务】用例活跃度清理完成，共逻辑删除 {} 条记录", count);
        } catch (Exception e) {
            log.error("【定时任务】用例活跃度清理失败", e);
        }
    }
}
