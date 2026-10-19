package com.example.testplatform.originalservice.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 原服务表
 * </p>
 *
 * @author author
 * @since 2026-06-17
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("original_service")
public class OriginalService implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键，UUID
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * FK → api_case(id)，手动创建时可为空
     */
    @TableField("case_id")
    private String caseId;

    /**
     * 原服务名称
     */
    @TableField("name")
    private String name;

    /**
     * 协议类型：1=HTTP，2=DUBBO（手动创建时使用）
     */
    @TableField("protocol_type")
    private Integer protocolType;

    /**
     * 请求方法：GET/POST/PUT/DELETE（手动创建时使用）
     */
    @TableField("request_type")
    private String requestType;

    /**
     * 请求地址（手动创建时使用）
     */
    @TableField("address")
    private String address;

    /**
     * 请求头配置（JSON）
     */
    @TableField("request_headers")
    private String requestHeaders;

    /**
     * 请求体（JSON）
     */
    @TableField("request_body")
    private String requestBody;

    /**
     * 0=不可用，1=可用
     */
    @TableField("is_available")
    private Integer isAvailable;

    /**
     * 入参映射规则（JSON Schema）
     */
    @TableField("input_params")
    private String inputParams;

    /**
     * 出参映射规则（JSON Schema）
     */
    @TableField("output_params")
    private String outputParams;

    /**
     * 断言配置
     */
    @TableField("assertions")
    private String assertions;

    /**
     * 原服务类型（预留字段）
     */
    @TableField("service_type")
    private Integer serviceType;

    /**
     * FK → application(id)
     */
    @TableField("application_id")
    private String applicationId;

    /**
     * 创建人
     */
    @TableField("creator_id")
    private Long creatorId;

    /**
     * 负责人
     */
    @TableField("owner_id")
    private Long ownerId;

    /**
     * 修改人
     */
    @TableField("updater_id")
    private Long updaterId;

    /**
     * 创建时间，毫秒精度
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间，毫秒精度
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=正常，1=已删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;


}
