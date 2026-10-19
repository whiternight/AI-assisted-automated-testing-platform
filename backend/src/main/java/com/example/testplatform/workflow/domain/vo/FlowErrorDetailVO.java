package com.example.testplatform.workflow.domain.vo;

import com.example.testplatform.enums.FlowNodeTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 失败定位详情 VO
 * <p>从流程历史中定位失败节点并提取错误信息</p>
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "失败定位详情")
public class FlowErrorDetailVO {

    @Schema(description = "失败活动 ID")
    private String failedActivityId;

    @Schema(description = "失败活动名称")
    private String failedActivityName;

    @Schema(description = "失败活动类型（Flowable 原生值）")
    private String failedActivityType;

    @Schema(description = "失败节点业务类型")
    private FlowNodeTypeEnum failedNodeType;

    @Schema(description = "错误类型：ASSERTION_FAIL / ENV_ERROR / PLATFORM_ERROR")
    private String errorType;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "失败的连线 ID（排他网关无匹配时填充）")
    private String failedSequenceFlowId;

    @Schema(description = "失败时间")
    private LocalDateTime failedAt;
}
