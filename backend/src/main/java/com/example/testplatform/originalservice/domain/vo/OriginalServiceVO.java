package com.example.testplatform.originalservice.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 原服务列表VO
 *
 * @author author
 * @since 2026-06-17
 */
@Data
public class OriginalServiceVO {

    private String id;

    /**
     * 关联用例ID
     */
    private String caseId;

    /**
     * 原服务名称
     */
    private String name;

    /**
     * 关联用例名称
     */
    private String caseName;

    /**
     * 协议类型（来自用例或手动配置）
     */
    private String protocolType;

    /**
     * 协议类型数值
     */
    private Integer protocolTypeNum;

    /**
     * 请求方法：GET/POST/PUT/DELETE
     */
    private String requestType;

    /**
     * 请求地址（来自用例或手动配置）
     */
    private String address;

    /**
     * 请求头配置（JSON）
     */
    private String requestHeaders;

    /**
     * 请求体（JSON）
     */
    private String requestBody;

    /**
     * 是否可用：0=不可用，1=可用
     */
    private Integer isAvailable;

    /**
     * 入参映射规则（JSON Schema）
     */
    private String inputParams;

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

    /**
     * 所属应用ID
     */
    private String applicationId;

    /**
     * 所属应用名称
     */
    private String applicationName;

    /**
     * 所属部门名称（通过应用关联获取）
     */
    private String departmentName;

    /**
     * 创建人
     */
    private Long creatorId;

    /**
     * 负责人
     */
    private Long ownerId;

    /**
     * 修改人
     */
    private Long updaterId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
