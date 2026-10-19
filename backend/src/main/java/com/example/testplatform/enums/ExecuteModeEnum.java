package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 执行模式枚举
 *
 * @author TestPlatform
 */
@Getter
public enum ExecuteModeEnum {
    /**
     * 同步执行
     */
    SYNC(1, "同步"),
    
    /**
     * 异步执行
     */
    ASYNC(2, "异步");

    /**
     * 执行模式编码
     */
    private final int code;
    
    /**
     * 执行模式描述
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param code 执行模式编码
     * @param desc 执行模式描述
     */
    ExecuteModeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据编码获取执行模式枚举
     *
     * @param code 执行模式编码
     * @return 对应的执行模式枚举，未找到返回null
     */
    public static ExecuteModeEnum getByCode(int code) {
        for (ExecuteModeEnum mode : values()) {
            if (mode.getCode() == code) {
                return mode;
            }
        }
        return null;
    }
}
