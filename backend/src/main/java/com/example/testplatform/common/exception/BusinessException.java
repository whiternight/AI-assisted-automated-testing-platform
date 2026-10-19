package com.example.testplatform.common.exception;

import com.example.testplatform.enums.BusinessExceptionEnum;
import lombok.Getter;

/**
 * 通用业务异常
 *
 * @author TestPlatform
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 异常码
     */
    private final int code;

    /**
     * 构造函数
     *
     * @param exceptionEnum 业务异常枚举
     */
    public BusinessException(BusinessExceptionEnum exceptionEnum) {
        super(exceptionEnum.getMessage());
        this.code = exceptionEnum.getCode();
    }

    /**
     * 构造函数（自定义消息）
     *
     * @param exceptionEnum 业务异常枚举
     * @param message 自定义消息
     */
    public BusinessException(BusinessExceptionEnum exceptionEnum, String message) {
        super(message);
        this.code = exceptionEnum.getCode();
    }

    /**
     * 构造函数（带原因）
     *
     * @param exceptionEnum 业务异常枚举
     * @param cause 异常原因
     */
    public BusinessException(BusinessExceptionEnum exceptionEnum, Throwable cause) {
        super(exceptionEnum.getMessage(), cause);
        this.code = exceptionEnum.getCode();
    }

    /**
     * 构造函数（自定义消息和原因）
     *
     * @param exceptionEnum 业务异常枚举
     * @param message 自定义消息
     * @param cause 异常原因
     */
    public BusinessException(BusinessExceptionEnum exceptionEnum, String message, Throwable cause) {
        super(message, cause);
        this.code = exceptionEnum.getCode();
    }

    /**
     * 构造函数（直接使用code和message）
     *
     * @param code 异常码
     * @param message 异常消息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
