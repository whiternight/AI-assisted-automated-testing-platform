package com.example.testplatform.originalservice.domain.vo;

import lombok.Data;

/**
 * 可选用例VO（未添加到原服务中的用例）
 *
 * @author author
 * @since 2026-06-17
 */
@Data
public class AvailableCaseVO {

    /**
     * 用例ID
     */
    private String id;

    /**
     * 用例名称
     */
    private String name;

    /**
     * 请求地址
     */
    private String address;

    /**
     * 协议类型：HTTP / DUBBO
     */
    private String protocolType;

    /**
     * 请求方法：GET / POST / PUT / DELETE
     */
    private String requestType;

    /**
     * 所属应用名称
     */
    private String applicationName;

    /**
     * 所属应用ID
     */
    private String applicationId;
}
