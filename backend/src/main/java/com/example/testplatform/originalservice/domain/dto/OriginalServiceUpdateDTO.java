package com.example.testplatform.originalservice.domain.dto;

import lombok.Data;

/**
 * 原服务更新DTO（修改请求参数）
 *
 * @author author
 * @since 2026-06-17
 */
@Data
public class OriginalServiceUpdateDTO {

    /**
     * 原服务名称
     */
    private String name;

    /**
     * 是否可用：0=不可用，1=可用
     */
    private Integer isAvailable;

    /**
     * 协议类型：1=HTTP，2=DUBBO
     */
    private Integer protocolType;

    /**
     * 请求方法：GET/POST/PUT/DELETE
     */
    private String requestType;

    /**
     * 请求地址
     */
    private String address;

    /**
     * 请求头配置（JSON字符串）
     */
    private String requestHeaders;

    /**
     * 入参映射规则（JSON Schema）
     */
    private String inputParams;

    /**
     * 请求体（JSON字符串）
     */
    private String requestBody;

    /**
     * 出参映射规则（JSON Schema）
     */
    private String outputParams;

    /**
     * 断言配置
     */
    private String assertions;

    /**
     * 原服务类型（预留字段）
     */
    private Integer serviceType;
}
