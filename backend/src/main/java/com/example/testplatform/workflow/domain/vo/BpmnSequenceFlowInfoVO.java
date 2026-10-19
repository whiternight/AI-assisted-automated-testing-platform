package com.example.testplatform.workflow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * BPMN 连线简要信息 VO
 * <p>从 Flowable SequenceFlow 映射，用于详情展示</p>
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "BPMN 连线简要信息")
public class BpmnSequenceFlowInfoVO {

    @Schema(description = "源节点 ID")
    private String source;

    @Schema(description = "目标节点 ID")
    private String target;

    @Schema(description = "条件表达式（可为空）")
    private String condition;
}
