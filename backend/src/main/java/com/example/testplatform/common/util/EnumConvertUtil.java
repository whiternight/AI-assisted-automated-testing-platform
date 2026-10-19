package com.example.testplatform.common.util;

import com.example.testplatform.enums.*;

/**
 * 枚举转换工具类
 * 用于在数据库 int 字段和业务层枚举之间进行转换
 *
 * @author TestPlatform
 */
public class EnumConvertUtil {

    /**
     * 将 protocolType int 值转换为 ProtocolTypeEnum
     *
     * @param code 协议类型编码
     * @return 协议类型枚举
     */
    public static ProtocolTypeEnum toProtocolTypeEnum(Integer code) {
        if (code == null) {
            return null;
        }
        return ProtocolTypeEnum.getByCode(code);
    }

    /**
     * 将 ProtocolTypeEnum 转换为 int 值
     *
     * @param enumValue 协议类型枚举
     * @return 协议类型编码
     */
    public static Integer fromProtocolTypeEnum(ProtocolTypeEnum enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.getCode();
    }

    /**
     * 将 executeMode int 值转换为 ExecuteModeEnum
     *
     * @param code 执行模式编码
     * @return 执行模式枚举
     */
    public static ExecuteModeEnum toExecuteModeEnum(Integer code) {
        if (code == null) {
            return null;
        }
        return ExecuteModeEnum.getByCode(code);
    }

    /**
     * 将 ExecuteModeEnum 转换为 int 值
     *
     * @param enumValue 执行模式枚举
     * @return 执行模式编码
     */
    public static Integer fromExecuteModeEnum(ExecuteModeEnum enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.getCode();
    }

    /**
     * 将 task status int 值转换为 TaskStatusEnum
     *
     * @param code 任务状态编码
     * @return 任务状态枚举
     */
    public static TaskStatusEnum toTaskStatusEnum(Integer code) {
        if (code == null) {
            return null;
        }
        return TaskStatusEnum.getByCode(code);
    }

    /**
     * 将 TaskStatusEnum 转换为 int 值
     *
     * @param enumValue 任务状态枚举
     * @return 任务状态编码
     */
    public static Integer fromTaskStatusEnum(TaskStatusEnum enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.getCode();
    }

    /**
     * 将 node instance status int 值转换为 NodeInstanceStatusEnum
     *
     * @param code 节点实例状态编码
     * @return 节点实例状态枚举
     */
    public static NodeInstanceStatusEnum toNodeInstanceStatusEnum(Integer code) {
        if (code == null) {
            return null;
        }
        return NodeInstanceStatusEnum.getByCode(code);
    }

    /**
     * 将 NodeInstanceStatusEnum 转换为 int 值
     *
     * @param enumValue 节点实例状态枚举
     * @return 节点实例状态编码
     */
    public static Integer fromNodeInstanceStatusEnum(NodeInstanceStatusEnum enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.getCode();
    }

    /**
     * 将 errorType int 值转换为 ErrorTypeEnum
     *
     * @param code 错误类型编码
     * @return 错误类型枚举
     */
    public static ErrorTypeEnum toErrorTypeEnum(Integer code) {
        if (code == null) {
            return null;
        }
        return ErrorTypeEnum.getByCode(code);
    }

    /**
     * 将 ErrorTypeEnum 转换为 int 值
     *
     * @param enumValue 错误类型枚举
     * @return 错误类型编码
     */
    public static Integer fromErrorTypeEnum(ErrorTypeEnum enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.getCode();
    }

    /**
     * 将 historyType int 值转换为 HistoryTypeEnum
     *
     * @param code 历史类型编码
     * @return 历史类型枚举
     */
    public static HistoryTypeEnum toHistoryTypeEnum(Integer code) {
        if (code == null) {
            return null;
        }
        return HistoryTypeEnum.getByCode(code);
    }

    /**
     * 将 HistoryTypeEnum 转换为 int 值
     *
     * @param enumValue 历史类型枚举
     * @return 历史类型编码
     */
    public static Integer fromHistoryTypeEnum(HistoryTypeEnum enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.getCode();
    }

    /**
     * 将 result int 值转换为 Boolean
     *
     * @param result 结果值（1=成功，0=失败）
     * @return Boolean 值
     */
    public static Boolean toResultBoolean(Integer result) {
        if (result == null) {
            return null;
        }
        return result == 1;
    }

    /**
     * 将 Boolean 转换为 result int 值
     *
     * @param resultBoolean Boolean 值
     * @return 结果值（1=成功，0=失败）
     */
    public static Integer fromResultBoolean(Boolean resultBoolean) {
        if (resultBoolean == null) {
            return null;
        }
        return resultBoolean ? 1 : 0;
    }

    /**
     * 将 logType int 值转换为 LogTypeEnum
     *
     * @param code 日志类型编码
     * @return 日志类型枚举
     */
    public static LogTypeEnum toLogTypeEnum(Integer code) {
        if (code == null) {
            return null;
        }
        return LogTypeEnum.getByCode(code);
    }

    /**
     * 将 LogTypeEnum 转换为 int 值
     *
     * @param enumValue 日志类型枚举
     * @return 日志类型编码
     */
    public static Integer fromLogTypeEnum(LogTypeEnum enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.getCode();
    }
}
