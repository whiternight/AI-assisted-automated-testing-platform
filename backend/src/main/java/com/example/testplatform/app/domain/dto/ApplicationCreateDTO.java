package com.example.testplatform.app.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <p>
 * 应用创建 DTO
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-15
 */
@Data
@Schema(name = "ApplicationCreateDTO", description = "新增应用请求参数")
public class ApplicationCreateDTO {

    /**
     * 应用名称
     */
    @NotBlank(message = "应用名称不能为空")
    @Schema(description = "应用名称", example = "订单系统")
    private String name;

    /**
     * 所属部门ID（FK → department.id）
     */
    @NotBlank(message = "部门ID不能为空")
    @Schema(description = "所属部门ID", example = "dept-001")
    private String departmentId;

    /**
     * 业务域ID（FK → business_domain.id），可选
     */
    @Schema(description = "业务域ID，可选", example = "1")
    private String businessDomainId;

    /**
     * 应用负责人ID（FK → user.id），不传则默认为当前登录用户
     */
    @Schema(description = "应用负责人ID，不传则默认为当前登录用户", example = "1001")
    private Long ownerId;

    /**
     * 应用描述
     */
    @Schema(description = "应用描述", example = "负责订单全生命周期管理")
    private String description;
}
