package com.example.testplatform.apicase.mapper;

import com.example.testplatform.apicase.domain.po.ApiCase;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * <p>
 * 用例主表 Mapper 接口
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
public interface ApiCaseMapper extends BaseMapper<ApiCase> {
    @Update("UPDATE api_case SET updated_at = #{updateTime} WHERE id = #{caseId}")
    void updateLastDebugTime(@Param("caseId") String caseId, @Param("updateTime") LocalDateTime updateTime);

}
