package com.example.testplatform.originalservice.domain.dto;

import lombok.Data;

/**
 * 原服务查询DTO
 *
 * @author author
 * @since 2026-06-17
 */
@Data
public class OriginalServiceQueryDTO {

    /**
     * 关键词（名称模糊搜索）
     */
    private String keyword;

    /**
     * 按应用ID筛选
     */
    private String applicationId;

    /**
     * 按可用状态筛选
     */
    private Integer isAvailable;

    /**
     * 当前页码
     */
    private Long pageNum = 1L;

    /**
     * 每页条数
     */
    private Long pageSize = 20L;
}
