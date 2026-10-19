package com.example.testplatform.apicase.domain.vo;

import lombok.Data;

@Data
public class AssertionRuleVO {
    private String type;
    private String description;
    private String expression;
    private String expectedValue;
    private String operator;
}
