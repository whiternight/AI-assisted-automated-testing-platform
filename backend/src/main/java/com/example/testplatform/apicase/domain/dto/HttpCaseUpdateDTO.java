package com.example.testplatform.apicase.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * HTTP用例更新DTO
 *
 * @author TestPlatform
 */
@Data
@Schema(description = "HTTP用例更新DTO")
public class HttpCaseUpdateDTO {

    @NotBlank(message = "用例ID不能为空")
    @Schema(description = "用例ID")
    private String id;

    @Schema(description = "用例名称")
    private String name;

    @Schema(description = "请求URL")
    private String url;

    @Schema(description = "请求方法")
    private String method;

    @Schema(description = "请求头")
    private String headers;

    @Schema(description = "请求体")
    private String body;

    @Schema(description = "超时时间(毫秒)")
    private Integer timeout;

    @Schema(description = "断言规则列表")
    private List<AssertionRuleDTO> assertionRules;
}
