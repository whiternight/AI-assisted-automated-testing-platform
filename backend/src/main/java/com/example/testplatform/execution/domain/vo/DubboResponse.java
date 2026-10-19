package com.example.testplatform.execution.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 * Dubbo 响应 VO
 *
 * @author TestPlatform
 */
@Data
@Builder
public class DubboResponse {

    /** 调用结果 */
    private Object result;

    /** 是否成功 */
    private Boolean success;

    /** 错误信息 */
    private String errorMsg;

    /** 执行耗时（毫秒） */
    private Long duration;
}
