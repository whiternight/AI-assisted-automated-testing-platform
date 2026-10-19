package com.example.testplatform.apicase.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * cURL 导入请求 DTO
 */
@Data
public class CurlImportDTO {

    @NotBlank(message = "cURL命令不能为空")
    private String curlCommand;

    @NotBlank(message = "所属应用不能为空")
    private String applicationId;

    /**
     * 用例名称（选填，留空则自动从 URL 生成）
     */
    private String name;
}
