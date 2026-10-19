package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 历史记录类型枚举
 *
 * @author TestPlatform
 */
@Getter
public enum HistoryTypeEnum {
    /**
     * 单接口测试
     */
    SINGLE_API(1, "单接口"),
    
    /**
     * 自动化测试
     */
    AUTOMATION(2, "自动化"),
    
    /**
     * 流程测试
     */
    FLOW(3, "流程"),
    
    /**
     * 测试集
     */
    TEST_SUITE(4, "测试集");

    /**
     * 历史类型编码
     */
    private final int code;
    
    /**
     * 历史类型描述
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param code 历史类型编码
     * @param desc 历史类型描述
     */
    HistoryTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据编码获取历史类型枚举
     *
     * @param code 历史类型编码
     * @return 对应的历史类型枚举，未找到返回null
     */
    public static HistoryTypeEnum getByCode(int code) {
        for (HistoryTypeEnum type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return null;
    }
}
