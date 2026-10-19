package com.example.testplatform.app.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

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
 * 业务域表
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("business_domain")
@Schema(name = "BusinessDomain对象", description = "业务域表")
public class BusinessDomain implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，UUID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Schema(description = "业务域名称")
    private String name;

    @Schema(description = "创建时间，毫秒精度")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间，毫秒精度")
    private LocalDateTime updatedAt;

    @TableLogic
    @Schema(description = "逻辑删除：0=正常，1=已删除")
    private Integer deleted;


}
