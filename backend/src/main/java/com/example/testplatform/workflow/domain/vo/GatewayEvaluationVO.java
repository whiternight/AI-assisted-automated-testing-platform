package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 网关评估记录 VO
 * <p>记录网关节点的条件评估过程与结果（事后采集，不干预执行）</p>
 *
 * @author TestPlatform
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "网关评估记录")
public class GatewayEvaluationVO {

    @Schema(description = "网关 ID（BPMN 元素 ID）")
    private String gatewayId;

    @Schema(description = "网关名称")
    private String gatewayName;

    @Schema(description = "网关类型：exclusiveGateway / inclusiveGateway / parallelGateway")
    private String gatewayType;

    // ---- 兼容旧字段（保留供已有代码使用） ----

    @Schema(description = "网关节点标识（与 gatewayId 相同，兼容旧字段）")
    private String gatewayNodeKey;

    @Schema(description = "网关节点名称")
    private String gatewayNodeName;

    @Schema(description = "网关类型编码：4=排他网关 5=并行网关 6=包容网关")
    private Integer gatewayTypeCode;

    @Schema(description = "条件表达式")
    private String conditionExpression;

    @Schema(description = "评估结果：true / false")
    private Boolean evaluationResult;

    @Schema(description = "选中的分支节点标识")
    private String selectedBranchKey;

    @Schema(description = "未选中的分支节点标识（排他网关时）")
    private String skippedBranchKey;

    @Schema(description = "评估时变量快照（JSON）")
    private String variablesSnapshot;

    @Schema(description = "评估时间")
    private LocalDateTime evaluatedAt;

    // ---- 新增字段（事后采集） ----

    @Schema(description = "实际走过的连线列表（从 HistoryService 采集）")
    private List<TakenFlowVO> takenFlows;

    @Schema(description = "排他网关命中的连线 ID（仅排他网关有值）")
    private String matchedFlowId;
}

