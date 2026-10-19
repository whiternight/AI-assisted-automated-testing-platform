package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 连线类型枚举
 *
 * @author TestPlatform
 */
@Getter
public enum SequenceTypeEnum {
    /**
     * 顺序流
     */
    SEQUENCE(1, "顺序流"),

    /**
     * 条件流
     */
    CONDITIONAL(2, "条件流"),

    /**
     * 默认流
     */
    DEFAULT(3, "默认流");

    /**
     * 连线类型编码
     */
    private final int code;

    /**
     * 连线类型描述
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param code 连线类型编码
     * @param desc 连线类型描述
     */
    SequenceTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据编码获取连线类型枚举
     *
     * @param code 连线类型编码
     * @return 对应的连线类型枚举，未找到返回null
     */
    public static SequenceTypeEnum getByCode(int code) {
        for (SequenceTypeEnum type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return null;
    }
}
