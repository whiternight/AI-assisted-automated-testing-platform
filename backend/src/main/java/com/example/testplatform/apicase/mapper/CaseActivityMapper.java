package com.example.testplatform.apicase.mapper;

import com.example.testplatform.apicase.domain.po.CaseActivity;
import com.example.testplatform.apicase.domain.vo.CaseActivityVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用例活跃度表 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-06-18
 */
public interface CaseActivityMapper extends BaseMapper<CaseActivity> {

    /**
     * 根据 caseId 查询活跃度记录（未删除）
     */
    CaseActivity selectByCaseId(@Param("caseId") String caseId);

    /**
     * 查询所有未删除且最近活跃时间早于指定时间的记录
     */
    List<CaseActivity> selectInactiveBefore(@Param("threshold") LocalDateTime threshold);

    /**
     * 批量逻辑删除（将 deleted 置为 1）
     */
    int batchLogicalDelete(@Param("ids") List<String> ids, @Param("now") LocalDateTime now);

    /**
     * 查询用例活跃度详情（关联 api_case 名称）
     */
    CaseActivityVO selectActivityDetail(@Param("caseId") String caseId);

    /**
     * 分页查询活跃度列表（支持按应用、活跃度状态筛选）
     */
    List<CaseActivityVO> selectActivityList(@Param("applicationId") String applicationId,
                                            @Param("active") Boolean active,
                                            @Param("threshold") LocalDateTime threshold);

    /**
     * 统计活跃度概览
     */
    CaseActivityVO selectActivityStats(@Param("applicationId") String applicationId,
                                       @Param("threshold") LocalDateTime threshold);
}
