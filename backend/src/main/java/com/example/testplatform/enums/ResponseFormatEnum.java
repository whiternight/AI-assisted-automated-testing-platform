package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 响应格式枚举
 *
 * @author TestPlatform
 */
@Getter
public enum ResponseFormatEnum {
    /**
     * JSON格式
     */
    JSON(1, "JSON"),

    /**
     * Body格式
     */
    BODY(2, "Body"),

    /**
     * HTML格式
     */
    HTML(3, "HTML"),

    /**
     * 文本格式
     */
    TEXT(4, "TEXT");

    /**
     * 响应格式编码
     */
    private final int code;

    /**
     * 响应格式描述
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param code 响应格式编码
     * @param desc 响应格式描述
     */
    ResponseFormatEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据编码获取响应格式枚举
     *
     * @param code 响应格式编码
     * @return 对应的响应格式枚举，未找到返回null
     */
    public static ResponseFormatEnum getByCode(int code) {
        for (ResponseFormatEnum format : values()) {
            if (format.getCode() == code) {
                return format;
            }
        }
        return null;
    }
}
