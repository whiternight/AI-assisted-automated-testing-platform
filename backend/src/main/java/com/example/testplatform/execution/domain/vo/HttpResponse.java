package com.example.testplatform.execution.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * HTTP 响应 VO
 *
 * @author TestPlatform
 */
@Data
@Builder
public class HttpResponse {

    /** HTTP状态码 */
    private Integer statusCode;

    /** 响应头 */
    private Map<String, String> headers;

    /** 响应体 */
    private Object body;

    /** 响应格式 */
    private String format;

    /** 响应大小（字节） */
    private Long size;

    /** 执行耗时（毫秒） */
    private Long duration;
}
