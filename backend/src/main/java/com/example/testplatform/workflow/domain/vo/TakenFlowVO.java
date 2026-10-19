package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网关触发的连线记录 VO
 * <p>记录网关评估后实际走过的 SequenceFlow 信息</p>
 *
 * @author TestPlatform
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "网关触发的连线记录")
public class TakenFlowVO {

    @Schema(description = "连线 ID（BPMN SequenceFlow ID）")
    private String flowId;

    @Schema(description = "连线名称")
    private String flowName;

    @Schema(description = "条件表达式（如 ${amount > 100}）")
    private String conditionExpression;

    @Schema(description = "是否为默认分支（default flow）")
    private Boolean isDefault;
}
