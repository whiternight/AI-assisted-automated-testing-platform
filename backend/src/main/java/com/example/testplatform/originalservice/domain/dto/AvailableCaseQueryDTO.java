package com.example.testplatform.originalservice.domain.dto;

import lombok.Data;

/**
 * 可选用例查询DTO（未在原服务中的用例）
 *
 * @author author
 * @since 2026-06-17
 */
@Data
public class AvailableCaseQueryDTO {

    /**
     * 关键词（用例名称模糊搜索）
     */
    private String keyword;

    /**
     * 按应用ID筛选
     */
    private String applicationId;

    /**
     * 按协议类型筛选
     */
    private Integer protocolType;

    /**
     * 当前页码
     */
    private Long pageNum = 1L;

    /**
     * 每页条数
     */
    private Long pageSize = 20L;
}
