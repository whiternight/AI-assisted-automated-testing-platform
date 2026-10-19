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
 * 流程定义业务扩展表
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("flow_definition")
@Schema(name = "FlowDefinition", description = "流程定义业务扩展表")
public class FlowDefinition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键，UUID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @Schema(description = "流程名称")
    private String name;

    @Schema(description = "FK → application(id)")
    private String applicationId;

    @Schema(description = "FK → business_domain(id)")
    private String businessDomainId;

    @Schema(description = "创建人")
    private Long creatorId;

    @Schema(description = "负责人")
    private Long ownerId;

    @Schema(description = "修改人")
    private Long updaterId;

    @Schema(description = "关联 Flowable ACT_RE_PROCDEF.ID_")
    private String procDefId;

    @Schema(description = "0=草稿 1=未部署 2=已发布 3=已停用")
    private Integer status;

    @Schema(description = "画布定义 JSON（节点、连线、坐标）")
    private String definitionJson;

    @Schema(description = "创建时间，毫秒精度")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间，毫秒精度")
    private LocalDateTime updatedAt;

    @TableLogic
    @Schema(description = "逻辑删除：0=正常，1=已删除")
    private Integer deleted;

    @Schema(description = "Flowable ACT_RE_DEPLOYMENT.ID_")
    private String deploymentId;

    @Schema(description = "BPMN XML 内容（备份）")
    private String definitionXml;

    @Schema(description = "版本号（语义化版本，如 0.0.1）")
    private String version;


}
