package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 日志类型枚举
 *
 * @author TestPlatform
 */
@Getter
public enum LogTypeEnum {
    /**
     * 请求日志
     */
    REQUEST(1, "请求日志"),
    
    /**
     * 响应日志
     */
    RESPONSE(2, "响应日志"),
    
    /**
     * 错误日志
     */
    ERROR(3, "错误日志"),
    
    /**
     * 重试日志
     */
    RETRY(4, "重试日志"),
    
    /**
     * 参数替换日志
     */
    PARAM_REPLACE(5, "参数替换日志");

    /**
     * 日志类型编码
     */
    private final int code;
    
    /**
     * 日志类型描述
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param code 日志类型编码
     * @param desc 日志类型描述
     */
    LogTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据编码获取日志类型枚举
     *
     * @param code 日志类型编码
     * @return 对应的日志类型枚举，未找到返回null
     */
    public static LogTypeEnum getByCode(int code) {
        for (LogTypeEnum type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return null;
    }
}
