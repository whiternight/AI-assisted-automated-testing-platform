package com.example.testplatform.apicase.service.impl;

import com.example.testplatform.apicase.domain.po.CaseActivity;
import com.example.testplatform.apicase.domain.vo.CaseActivityVO;
import com.example.testplatform.apicase.mapper.CaseActivityMapper;
import com.example.testplatform.apicase.service.ICaseActivityService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用例活跃度表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-06-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseActivityServiceImpl extends ServiceImpl<CaseActivityMapper, CaseActivity> implements ICaseActivityService {

    private final CaseActivityMapper caseActivityMapper;

    /**
     * 不活跃阈值：7天
     */
    private static final int INACTIVE_DAYS_THRESHOLD = 7;

    // ==================== 活跃度记录 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordDebugActivity(String caseId) {
        LocalDateTime now = LocalDateTime.now();
        CaseActivity activity = caseActivityMapper.selectByCaseId(caseId);
        if (activity == null) {
            // 新建活跃度记录
            activity = buildNewActivity(caseId, now);
            activity.setLastDebugTime(now);
            save(activity);
            log.info("新建活跃度记录并记录调试时间: caseId={}", caseId);
        } else {
            activity.setLastDebugTime(now);
            activity.setUpdatedAt(now);
            updateById(activity);
            log.info("更新调试活跃时间: caseId={}", caseId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordFlowExecuteActivity(String caseId) {
        updateActivityTime(caseId, "flow");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordSuiteExecuteActivity(String caseId) {
        updateActivityTime(caseId, "suite");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordViewActivity(String caseId) {
        updateActivityTime(caseId, "view");
    }

    // ==================== 查询 ====================

    @Override
    public CaseActivityVO getActivityByCaseId(String caseId) {
        CaseActivityVO vo = caseActivityMapper.selectActivityDetail(caseId);
        if (vo == null) {
            return null;
        }
        enrichActivityVO(vo);
        return vo;
    }

    @Override
    public List<CaseActivityVO> listActivities(String applicationId, Boolean active) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(INACTIVE_DAYS_THRESHOLD);
        List<CaseActivityVO> list = caseActivityMapper.selectActivityList(applicationId, active, threshold);
        return list.stream()
                .map(this::enrichActivityVO)
                .collect(Collectors.toList());
    }

    @Override
    public CaseActivityVO getActivityStats(String applicationId) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(INACTIVE_DAYS_THRESHOLD);
        CaseActivityVO stats = caseActivityMapper.selectActivityStats(applicationId, threshold);
        if (stats == null) {
            stats = CaseActivityVO.builder()
                    .totalCount(0)
                    .activeCount(0)
                    .inactiveCount(0)
                    .build();
        }
        // 统计已逻辑删除数量（全局，不区分应用）
        long deletedCount = lambdaQuery()
                .eq(CaseActivity::getDeleted, 1)
                .count();
        stats.setDeletedCount((int) deletedCount);
        return stats;
    }

    // ==================== 逻辑删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int logicalDeleteInactiveCases() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(INACTIVE_DAYS_THRESHOLD);
        List<CaseActivity> inactiveList = caseActivityMapper.selectInactiveBefore(threshold);
        if (inactiveList.isEmpty()) {
            log.info("无需逻辑删除：没有超过{}天未活跃的用例", INACTIVE_DAYS_THRESHOLD);
            return 0;
        }
        List<String> ids = inactiveList.stream()
                .map(CaseActivity::getId)
                .collect(Collectors.toList());
        int count = caseActivityMapper.batchLogicalDelete(ids, LocalDateTime.now());
        log.info("逻辑删除完成：共删除{}条超过{}天未活跃的用例活跃度记录", count, INACTIVE_DAYS_THRESHOLD);
        return count;
    }

    // ==================== 初始化 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initActivityIfAbsent(String caseId) {
        CaseActivity existing = caseActivityMapper.selectByCaseId(caseId);
        if (existing == null) {
            LocalDateTime now = LocalDateTime.now();
            CaseActivity activity = buildNewActivity(caseId, now);
            save(activity);
            log.info("初始化活跃度记录: caseId={}", caseId);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 通用活跃度时间更新
     */
    private void updateActivityTime(String caseId, String type) {
        LocalDateTime now = LocalDateTime.now();
        CaseActivity activity = caseActivityMapper.selectByCaseId(caseId);
        if (activity == null) {
            activity = buildNewActivity(caseId, now);
            setTimeByType(activity, type, now);
            save(activity);
            log.info("新建活跃度记录并记录[{}]时间: caseId={}", type, caseId);
        } else {
            setTimeByType(activity, type, now);
            activity.setUpdatedAt(now);
            updateById(activity);
            log.info("更新[{}]活跃时间: caseId={}", type, caseId);
        }
    }

    private void setTimeByType(CaseActivity activity, String type, LocalDateTime time) {
        switch (type) {
            case "debug":
                activity.setLastDebugTime(time);
                break;
            case "flow":
                activity.setLastFlowExecuteTime(time);
                break;
            case "suite":
                activity.setLastSuiteExecuteTime(time);
                break;
            case "view":
                activity.setLastViewTime(time);
                break;
            default:
                throw new IllegalArgumentException("未知活跃度类型: " + type);
        }
    }

    /**
     * 构建新的活跃度记录
     */
    private CaseActivity buildNewActivity(String caseId, LocalDateTime now) {
        return CaseActivity.builder()
                .caseId(caseId)
                .calculatedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
    }

    /**
     * 为 VO 填充 active / inactiveDays 计算字段
     */
    private CaseActivityVO enrichActivityVO(CaseActivityVO vo) {
        if (vo == null) {
            return null;
        }
        LocalDateTime lastActive = vo.getLastActiveTime();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusDays(INACTIVE_DAYS_THRESHOLD);

        if (lastActive == null || lastActive.isBefore(LocalDateTime.of(1971, 1, 1, 0, 0))) {
            // 从未活跃
            vo.setActive(false);
            vo.setInactiveDays(null);
        } else {
            boolean active = !lastActive.isBefore(threshold);
            vo.setActive(active);
            if (!active) {
                long days = Duration.between(lastActive, now).toDays();
                vo.setInactiveDays(days);
            } else {
                vo.setInactiveDays(0L);
            }
        }
        return vo;
    }
}
