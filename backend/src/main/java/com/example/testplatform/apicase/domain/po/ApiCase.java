package com.example.testplatform.apicase.domain.po;

import com.example.testplatform.enums.RequestTypeEnum;
import com.example.testplatform.enums.ResponseFormatEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.example.testplatform.enums.ProtocolTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用例主表
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("api_case")
@Schema(name = "ApiCase对象", description = "用例主表")
public class ApiCase implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，UUID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Schema(description = "用例名称")
    private String name;

    @Schema(description = "请求地址（URL 或 Dubbo 全名）")
    private String address;

    @Schema(description = "1=HTTP，2=DUBBO，预留 3=MQ")
    private Integer protocolType;

    /**
     * 协议类型枚举（业务层使用，不映射到数据库）
     */
    @TableField(exist = false)
    @Schema(description = "协议类型枚举")
    private ProtocolTypeEnum protocolTypeEnum;

    @Schema(description = "HTTP 方法：GET/POST/PUT/DELETE")
    private RequestTypeEnum requestType;

    @Schema(description = "请求头配置")
    private String requestHeaders;

    @Schema(description = "请求参数")
    private String requestParams;

    @Schema(description = "请求体")
    private String requestBody;

    @Schema(description = "响应断言规则")
    private String responseAssertions;

    @Schema(description = "JSON/Body/HTML/TXT")
    private ResponseFormatEnum responseFormat;

    @Schema(description = "FK → application(id)")
    private String applicationId;

    @Schema(description = "FK → business_domain(id)")
    private String businessDomainId;

    @Schema(description = "0=否，1=是（参与自动化测试）")
    private Integer isAutomationEnabled;

    @Schema(description = "环境标识（预留多环境扩展）")
    private String env;

    @Schema(description = "默认超时（毫秒）")
    private Integer timeout;

    @Schema(description = "最近一次执行响应体")
    private String responseBody;

    @Schema(description = "最近一次执行是否成功：0=失败，1=成功")
    private Integer isSuccessful;

    @Schema(description = "创建人")
    private Long creatorId;

    @Schema(description = "负责人")
    private Long ownerId;

    @Schema(description = "最近修改人")
    private Long updaterId;

    @Schema(description = "创建时间，毫秒精度")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间，毫秒精度")
    private LocalDateTime updatedAt;

    @TableLogic
    @Schema(description = "逻辑删除：0=正常，1=已删除")
    private Integer deleted;


}
