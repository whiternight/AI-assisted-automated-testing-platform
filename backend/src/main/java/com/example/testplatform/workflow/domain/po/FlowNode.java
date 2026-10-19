package com.example.testplatform.workflow.domain.po;

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
 * 流程节点表
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("flow_node")
@Schema(name = "FlowNode", description = "流程节点表")
public class FlowNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，UUID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Schema(description = "FK → flow_definition(id)")
    private String flowDefinitionId;

    @Schema(description = "画布节点唯一标识（如 node_1）")
    private String nodeKey;

    @Schema(description = "1=开始，2=结束，3=原服务节点（HTTP/Dubbo 等 serviceTask），4=条件分支，5=并行网关，6=包容网关，7=事件网关，8=子流程，9=调用活动")
    private Integer nodeType;

    @Schema(description = "FK → original_service(id)")
    private String originalServiceId;

    @Schema(description = "节点显示名称")
    private String nodeName;

    @Schema(description = "入参配置（静态值+动态引用）")
    private String inputParams;

    @Schema(description = "出参配置（JSONPath 提取字段列表）")
    private String outputParams;

    @Schema(description = "节点级超时（毫秒），覆盖用例默认值")
    private Integer timeout;

    @Schema(description = "重试次数（0~5）")
    private Integer retryCount;

    @Schema(description = "重试间隔（毫秒）")
    private Integer retryInterval;

    @Schema(description = "1=固定间隔，2=指数退避")
    private Integer retryType;

    @Schema(description = "断言配置 JSON（覆盖原服务级断言）")
    private String assertions;

    @Schema(description = "0=引用失效（红色警告），1=有效")
    private Integer isReferenceValid;

    @Schema(description = "创建时间，毫秒精度")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间，毫秒精度")
    private LocalDateTime updatedAt;

    @TableLogic
    @Schema(description = "逻辑删除：0=正常，1=已删除")
    private Integer deleted;


}
