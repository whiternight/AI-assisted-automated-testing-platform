package com.example.testplatform.enums;

import lombok.Getter;

/**
 * 任务状态枚举
 *
 * @author TestPlatform
 */
@Getter
public enum TaskStatusEnum {
    /**
     * 待执行
     */
    PENDING(0, "待执行"),
    
    /**
     * 执行中
     */
    RUNNING(1, "执行中"),
    
    /**
     * 成功
     */
    SUCCESS(2, "成功"),
    
    /**
     * 部分成功
     */
    PARTIAL_SUCCESS(3, "部分成功"),
    
    /**
     * 失败
     */
    FAILED(4, "失败"),
    
    /**
     * 阻塞
     */
    BLOCKED(5, "阻塞");

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
    TaskStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据编码获取任务状态枚举
     *
     * @param code 状态编码
     * @return 对应的任务状态枚举，未找到返回null
     */
    public static TaskStatusEnum getByCode(int code) {
        for (TaskStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
