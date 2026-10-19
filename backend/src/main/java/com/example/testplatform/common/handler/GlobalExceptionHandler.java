package com.example.testplatform.common.handler;

import com.example.testplatform.common.result.Result;
import com.example.testplatform.enums.BusinessExceptionEnum;
import com.example.testplatform.common.exception.BusinessException;
import com.example.testplatform.common.exception.CaseExecuteException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.TimeoutException;

/**
 * 全局异常处理器
 *
 * @author TestPlatform
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理用例执行异常
     */
    @ExceptionHandler(CaseExecuteException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleCaseExecuteException(CaseExecuteException e) {
        log.warn("用例执行异常: code={}, message={}, errorType={}", 
                e.getCode(), e.getMessage(), e.getErrorType());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        
        log.warn("参数校验异常: {}", message);
        return Result.badRequest(message);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数绑定失败";
        
        log.warn("参数绑定异常: {}", message);
        return Result.badRequest(message);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数异常: {}", e.getMessage());
        return Result.badRequest(e.getMessage());
    }

    /**
     * 处理非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleIllegalStateException(IllegalStateException e) {
        log.warn("非法状态异常: {}", e.getMessage());
        return Result.forbidden(e.getMessage());
    }

    /**
     * 处理超时异常
     */
    @ExceptionHandler(TimeoutException.class)
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    public Result<Void> handleTimeoutException(TimeoutException e) {
        log.warn("请求超时: {}", e.getMessage());
        return Result.error(BusinessExceptionEnum.TIMEOUT_ERROR.getCode(), 
                BusinessExceptionEnum.TIMEOUT_ERROR.getMessage());
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常", e);
        return Result.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                "系统内部错误，请联系管理员");
    }

    /**
     * 处理其他所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(), 
                "系统内部错误，请联系管理员");
    }
}
