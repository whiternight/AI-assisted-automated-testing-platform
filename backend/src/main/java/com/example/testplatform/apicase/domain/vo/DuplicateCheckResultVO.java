package com.example.testplatform.apicase.domain.vo;

import lombok.Data;

@Data
public class DuplicateCheckResultVO {
    private Boolean isDuplicate;  // 是否重复
    private String duplicateCaseId;  // 重复用例ID
    private String duplicateCaseName;  // 重复用例名称
    private String message;  // 提示信息
}
