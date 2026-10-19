package com.example.testplatform.apicase.domain.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CaseDebugRequestDTO {

    /** 执行环境 */
    private String env = "default";

    /** 用户覆盖的请求头（合并策略：覆盖同名Key） */
    private Map<String, String> headers;

    /** 用户覆盖的请求参数（合并策略：覆盖同名Key） */
    private Map<String, Object> params;

    /** 用户覆盖的请求体（完全覆盖） */
    private Object body;

    /** 用户指定的超时时间（毫秒），不传则使用用例默认值 */
    private Integer timeout;
}
