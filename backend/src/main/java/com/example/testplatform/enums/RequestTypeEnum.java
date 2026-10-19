package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 请求类型枚举
 *
 * @author TestPlatform
 */
@Getter
public enum RequestTypeEnum {
    /**
     * GET请求
     */
    GET("GET"),

    /**
     * POST请求
     */
    POST("POST"),

    /**
     * PUT请求
     */
    PUT("PUT"),

    /**
     * DELETE请求
     */
    DELETE("DELETE"),

    /**
     * PATCH请求
     */
    PATCH("PATCH");

    /**
     * HTTP方法
     */
    private final String method;

    /**
     * 构造函数
     *
     * @param method HTTP方法
     */
    RequestTypeEnum(String method) {
        this.method = method;
    }

    /**
     * 根据方法名获取请求类型枚举
     *
     * @param method HTTP方法
     * @return 对应的请求类型枚举，未找到返回null
     */
    public static RequestTypeEnum getByMethod(String method) {
        for (RequestTypeEnum type : values()) {
            if (type.getMethod().equalsIgnoreCase(method)) {
                return type;
            }
        }
        return null;
    }
}
