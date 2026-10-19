package com.example.testplatform.workflow.domain.vo;

import com.example.testplatform.enums.FlowNodeTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点执行详情 VO
 * <p>包含节点轨迹信息和解密后的输入/输出快照</p>
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "节点执行详情")
public class NodeDetailVO {

    @Schema(description = "活动 ID（BPMN 元素 ID）")
    private String activityId;

    @Schema(description = "活动名称")
    private String activityName;

    @Schema(description = "活动类型（Flowable 原生值，如 serviceTask / exclusiveGateway）")
    private String activityType;

    @Schema(description = "业务节点类型枚举")
    private FlowNodeTypeEnum nodeType;

    @Schema(description = "节点执行状态：COMPLETED / ACTIVE / FAILED / SKIPPED")
    private String status;

    @Schema(description = "关联原服务 ID（仅 serviceTask 有值）")
    private String originalServiceId;

    @Schema(description = "节点执行耗时（毫秒）")
    private Long duration;

    @Schema(description = "错误类型：ASSERTION_FAIL / ENV_ERROR / PLATFORM_ERROR")
    private String errorType;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "节点开始时间")
    private LocalDateTime startTime;

    @Schema(description = "节点结束时间")
    private LocalDateTime endTime;

    @Schema(description = "进入快照（AES 解密后的 JSON）")
    private String snapshotEnter;

    @Schema(description = "退出快照（AES 解密后的 JSON）")
    private String snapshotExit;
}
