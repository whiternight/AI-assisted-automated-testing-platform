package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 节点实例状态枚举
 *
 * @author TestPlatform
 */
@Getter
public enum NodeInstanceStatusEnum {
    /**
     * 待执行
     */
    PENDING(0, "待执行"),
    
    /**
     * 执行中
     */
    RUNNING(1, "执行中"),
    
    /**
     * 已完成
     */
    COMPLETED(2, "已完成"),
    
    /**
     * 失败
     */
    FAILED(3, "失败"),
    
    /**
     * 超时
     */
    TIMEOUT(4, "超时"),
    
    /**
     * 跳过
     */
    SKIPPED(5, "跳过"),
    
    /**
     * 阻塞
     */
    BLOCKED(6, "阻塞");

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
    NodeInstanceStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据编码获取节点实例状态枚举
     *
     * @param code 状态编码
     * @return 对应的节点实例状态枚举，未找到返回null
     */
    public static NodeInstanceStatusEnum getByCode(int code) {
        for (NodeInstanceStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
