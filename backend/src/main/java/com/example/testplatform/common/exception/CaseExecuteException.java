package com.example.testplatform.common.exception;

import com.example.testplatform.enums.BusinessExceptionEnum;
import com.example.testplatform.enums.ErrorTypeEnum;
import lombok.Getter;

/**
 * 用例执行异常
 *
 * @author TestPlatform
 */
@Getter
public class CaseExecuteException extends RuntimeException {

    /**
     * 异常码
     */
    private final int code;

    /**
     * 错误类型
     */
    private final ErrorTypeEnum errorType;

    /**
     * 构造函数
     *
     * @param code 异常码
     * @param message 异常消息
     * @param errorType 错误类型
     */
    public CaseExecuteException(int code, String message, ErrorTypeEnum errorType) {
        super(message);
        this.code = code;
        this.errorType = errorType;
    }

    /**
     * 构造函数（带原因）
     *
     * @param code 异常码
     * @param message 异常消息
     * @param errorType 错误类型
     * @param cause 异常原因
     */
    public CaseExecuteException(int code, String message, ErrorTypeEnum errorType, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.errorType = errorType;
    }

    /**
     * 构造函数（使用枚举）
     *
     * @param exceptionEnum 业务异常枚举
     * @param errorType 错误类型
     */
    public CaseExecuteException(BusinessExceptionEnum exceptionEnum, ErrorTypeEnum errorType) {
        super(exceptionEnum.getMessage());
        this.code = exceptionEnum.getCode();
        this.errorType = errorType;
    }

    /**
     * 构造函数（使用枚举，带原因）
     *
     * @param exceptionEnum 业务异常枚举
     * @param errorType 错误类型
     * @param cause 异常原因
     */
    public CaseExecuteException(BusinessExceptionEnum exceptionEnum, ErrorTypeEnum errorType, Throwable cause) {
        super(exceptionEnum.getMessage(), cause);
        this.code = exceptionEnum.getCode();
        this.errorType = errorType;
    }
}
