package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 业务异常枚举
 *
 * @author TestPlatform
 */
@Getter
public enum BusinessExceptionEnum {

    // ==================== 通用异常 (1xxx) ====================
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(1001, "参数错误"),
    VALIDATE_ERROR(1002, "校验失败"),
    
    // ==================== 认证授权异常 (2xxx) ====================
    UNAUTHORIZED(2001, "未登录或登录已过期"),
    FORBIDDEN(2002, "没有权限执行此操作"),
    TOKEN_INVALID(2003, "Token无效"),
    TOKEN_EXPIRED(2004, "Token已过期"),
    
    // ==================== 资源异常 (3xxx) ====================
    NOT_FOUND(3001, "资源不存在"),
    ALREADY_EXISTS(3002, "资源已存在"),
    
    // ==================== 用例相关异常 (4xxx) ====================
    CASE_NOT_FOUND(4001, "用例不存在"),
    CASE_DELETED(4002, "用例已删除"),
    CASE_EXECUTE_FAILED(4003, "用例执行失败"),
    CASE_ASSERTION_FAILED(4004, "用例断言失败"),
    CASE_DUPLICATE(4005, "已存在相同配置的用例"),
    
    // ==================== 应用相关异常 (5xxx) ====================
    APP_NOT_FOUND(5001, "应用不存在"),
    APP_NO_PERMISSION(5002, "没有该应用的执行权限"),
    
    // ==================== 系统异常 (9xxx) ====================
    SYSTEM_ERROR(9001, "系统内部错误"),
    DATABASE_ERROR(9002, "数据库操作失败"),
    TIMEOUT_ERROR(9003, "请求超时"),
    NETWORK_ERROR(9004, "网络连接失败");

    /**
     * 异常码
     */
    private final int code;
    
    /**
     * 异常消息
     */
    private final String message;

    /**
     * 构造函数
     *
     * @param code 异常码
     * @param message 异常消息
     */
    BusinessExceptionEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
