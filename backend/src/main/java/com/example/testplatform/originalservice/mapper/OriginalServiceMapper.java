package com.example.testplatform.originalservice.mapper;

import com.example.testplatform.originalservice.domain.po.OriginalService;
import com.example.testplatform.originalservice.domain.vo.AvailableCaseVO;
import com.example.testplatform.originalservice.domain.vo.OriginalServiceVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 原服务表 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-06-17
 */
public interface OriginalServiceMapper extends BaseMapper<OriginalService> {

    /**
     * 分页查询原服务列表（关联用例和应用信息）
     *
     * @param page          分页对象
     * @param keyword       关键词
     * @param applicationId 应用ID
     * @param isAvailable   可用状态
     * @return 分页原服务VO列表
     */
    Page<OriginalServiceVO> selectOriginalServicePage(
            Page<OriginalServiceVO> page,
            @Param("keyword") String keyword,
            @Param("applicationId") String applicationId,
            @Param("isAvailable") Integer isAvailable);

    /**
     * 查询可选用例列表（未在原服务中的用例）
     *
     * @param page          分页对象
     * @param keyword       关键词
     * @param applicationId 应用ID
     * @param protocolType  协议类型
     * @param excludeCaseIds 已存在的用例ID列表
     * @return 分页可选用例VO列表
     */
    Page<AvailableCaseVO> selectAvailableCasePage(
            Page<AvailableCaseVO> page,
            @Param("keyword") String keyword,
            @Param("applicationId") String applicationId,
            @Param("protocolType") Integer protocolType,
            @Param("excludeCaseIds") List<String> excludeCaseIds);

    /**
     * 根据原服务ID查询详情（关联用例和应用信息）
     *
     * @param id 原服务ID
     * @return 原服务VO
     */
    OriginalServiceVO selectOriginalServiceById(@Param("id") String id);
}
