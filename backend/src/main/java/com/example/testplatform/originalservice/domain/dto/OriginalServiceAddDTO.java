package com.example.testplatform.originalservice.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 原服务批量添加用例DTO
 *
 * @author author
 * @since 2026-06-17
 */
@Data
public class OriginalServiceAddDTO {

    /**
     * 批量添加的用例ID列表
     */
    @NotEmpty(message = "用例ID列表不能为空")
    private List<String> caseIds;

    /**
     * 所属应用ID（必填）
     */
    @NotBlank(message = "应用ID不能为空")
    private String applicationId;

}
