package com.example.testplatform.execution.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单个用例节点执行结果 VO
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(name = "NodeResultVO", description = "单个用例节点执行结果")
public class NodeResultVO {

    @Schema(description = "节点实例ID")
    private String nodeInstanceId;

    @Schema(description = "用例ID")
    private String caseId;

    @Schema(description = "用例名称")
    private String caseName;

    @Schema(description = "执行状态（0=待执行，1=执行中，2=完成，3=失败，4=超时，5=跳过，6=阻塞）")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "是否通过（用于通过率计算）：0=未通过（非2xx），1=已通过（2xx）")
    private Integer passed;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @Schema(description = "错误类型")
    private String errorType;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
