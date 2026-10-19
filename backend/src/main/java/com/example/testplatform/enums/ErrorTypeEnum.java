package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 错误类型枚举
 *
 * @author TestPlatform
 */
@Getter
public enum ErrorTypeEnum {
    /**
     * 断言失败
     */
    ASSERTION_FAIL(1, "断言失败"),
    
    /**
     * 环境错误
     */
    ENV_ERROR(2, "环境错误"),
    
    /**
     * 平台错误
     */
    PLATFORM_ERROR(3, "平台错误");

    /**
     * 错误类型编码
     */
    private final int code;
    
    /**
     * 错误类型描述
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param code 错误类型编码
     * @param desc 错误类型描述
     */
    ErrorTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据编码获取错误类型枚举
     *
     * @param code 错误类型编码
     * @return 对应的错误类型枚举，未找到返回null
     */
    public static ErrorTypeEnum getByCode(int code) {
        for (ErrorTypeEnum type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return null;
    }
}
