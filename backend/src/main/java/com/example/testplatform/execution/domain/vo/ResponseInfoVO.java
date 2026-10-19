package com.example.testplatform.execution.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseInfoVO {

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

    /** 响应耗时（毫秒） */
    private Long duration;
}
