package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 协议类型枚举
 *
 * @author TestPlatform
 */
@Getter
public enum ProtocolTypeEnum {
    /**
     * HTTP协议
     */
    HTTP(1, "HTTP"),
    
    /**
     * Dubbo协议
     */
    DUBBO(2, "Dubbo");

    /**
     * 协议类型编码
     */
    private final int code;
    
    /**
     * 协议类型描述
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param code 协议类型编码
     * @param desc 协议类型描述
     */
    ProtocolTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据编码获取协议类型枚举
     *
     * @param code 协议类型编码
     * @return 对应的协议类型枚举，未找到返回null
     */
    public static ProtocolTypeEnum getByCode(int code) {
        for (ProtocolTypeEnum type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return null;
    }
}
