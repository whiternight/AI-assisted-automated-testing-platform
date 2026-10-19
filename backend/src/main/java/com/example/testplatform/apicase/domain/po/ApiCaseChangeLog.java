package com.example.testplatform.apicase.domain.po;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;

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
 * 用例变更记录表
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("api_case_change_log")
@Schema(name="ApiCaseChangeLog对象", description="用例变更记录表")
public class ApiCaseChangeLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，UUID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Schema(description = "FK → api_case(id)")
    private String caseId;

    @Schema(description = "变更字段名")
    private String fieldName;

    @Schema(description = "变更前值")
    private String oldValue;

    @Schema(description = "变更后值")
    private String newValue;

    @Schema(description = "操作人")
    private Long operatorId;

    @Schema(description = "创建时间，毫秒精度")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间，毫秒精度")
    private LocalDateTime updatedAt;

    @TableLogic
    @Schema(description = "逻辑删除：0=正常，1=已删除")
    private Integer deleted;


}
