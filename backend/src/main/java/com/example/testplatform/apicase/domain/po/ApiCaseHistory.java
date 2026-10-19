package com.example.testplatform.apicase.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.example.testplatform.enums.ErrorTypeEnum;
import com.example.testplatform.enums.HistoryTypeEnum;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用例测试历史表
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("api_case_history")
@Schema(name = "ApiCaseHistory对象", description = "用例测试历史表")
public class ApiCaseHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，UUID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Schema(description = "FK → api_case(id)")
    private String caseId;

    @Schema(description = "1=单接口，2=自动化，3=流程，4=测试集")
    private Integer historyType;

    /**
     * 历史记录类型枚举（业务层使用，不映射到数据库）
     */
    @TableField(exist = false)
    @Schema(description = "历史记录类型枚举")
    private HistoryTypeEnum historyTypeEnum;

    @Schema(description = "入参快照（AES-256 加密）")
    private String requestSnapshot;

    @Schema(description = "出参快照及原始响应体（AES-256 加密）")
    private String responseSnapshot;

    @Schema(description = "1=成功，0=失败")
    private Integer result;

    /**
     * 执行结果（业务层使用，不映射到数据库）
     */
    @TableField(exist = false)
    @Schema(description = "执行结果")
    private Boolean resultBoolean;

    @Schema(description = "执行人")
    private Long executorId;

    @Schema(description = "执行环境")
    private String env;

    @Schema(description = "1=ASSERTION_FAIL，2=ENV_ERROR，3=PLATFORM_ERROR")
    private Integer errorType;

    /**
     * 错误类型枚举（业务层使用，不映射到数据库）
     */
    @TableField(exist = false)
    @Schema(description = "错误类型枚举")
    private ErrorTypeEnum errorTypeEnum;

    @Schema(description = "FK → task(id)，异步执行时关联")
    private String taskId;

    @Schema(description = "创建时间，毫秒精度")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间，毫秒精度")
    private LocalDateTime updatedAt;

    @TableLogic
    @Schema(description = "逻辑删除：0=正常，1=已删除")
    private Integer deleted;


}
