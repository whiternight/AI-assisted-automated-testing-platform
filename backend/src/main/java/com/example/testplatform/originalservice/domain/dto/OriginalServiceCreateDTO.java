package com.example.testplatform.originalservice.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 手动创建原服务DTO
 *
 * @author author
 * @since 2026-06-22
 */
@Data
public class OriginalServiceCreateDTO {

    /**
     * 原服务名称
     */
    @NotBlank(message = "原服务名称不能为空")
    private String name;

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
    @NotBlank(message = "请求地址不能为空")
    private String address;

    /**
     * 请求头配置（JSON字符串）
     */
    private String requestHeaders;

    /**
     * 请求参数（JSON字符串，对应入参映射）
     */
    private String requestParams;

    /**
     * 请求体（JSON字符串）
     */
    private String requestBody;

    /**
     * 出参映射规则（JSON Schema）
     */
    private String outputParams;

    /**
     * 断言配置（JSON字符串）
     */
    private String assertions;

    /**
     * 原服务类型（预留字段）
     */
    private Integer serviceType;

    /**
     * 所属应用ID
     */
    @NotBlank(message = "应用ID不能为空")
    private String applicationId;

    /**
     * 关联用例ID（可选，从已有用例创建时传入）
     */
    private String caseId;
}
