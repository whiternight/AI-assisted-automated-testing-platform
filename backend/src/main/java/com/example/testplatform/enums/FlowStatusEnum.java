package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 流程状态枚举
 *
 * @author TestPlatform
 */
@Getter
public enum FlowStatusEnum {
    /**
     * 草稿
     */
    DRAFT(0, "草稿"),

    /**
     * 未部署
     */
    UNDEPLOYED(1, "未部署"),

    /**
     * 已发布
     */
    PUBLISHED(2, "已发布"),

    /**
     * 已停用
     */
    DISABLED(3, "已停用");

    /**
     * 状态编码
     */
    private final int code;

    /**
     * 状态描述
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param code 状态编码
     * @param desc 状态描述
     */
    FlowStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据编码获取流程状态枚举
     *
     * @param code 状态编码
     * @return 对应的流程状态枚举，未找到返回null
     */
    public static FlowStatusEnum getByCode(int code) {
        for (FlowStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
