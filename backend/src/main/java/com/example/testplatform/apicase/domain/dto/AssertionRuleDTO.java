package com.example.testplatform.apicase.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssertionRuleDTO {

    @NotBlank(message = "断言类型不能为空")
    private String type;  // STATUS_CODE, HEADER, JSON_PATH, REGEX, RESPONSE_TIME

    private String description;

    @NotBlank(message = "断言表达式不能为空")
    private String expression;  // JSONPath、正则、状态码等

    @NotBlank(message = "期望值不能为空")
    private String expectedValue;

    private String operator = "EQUALS";  // EQUALS, CONTAINS, MATCHES, GT, LT, GTE, LTE
}
