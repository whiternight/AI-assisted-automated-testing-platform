package com.example.testplatform.apicase.domain.vo;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <p>
 * 批量调试单个用例的执行结果 VO
 * </p>
 * <p>
 * 每个元素代表一个用例的执行结果，包含用例标识、执行状态、完整响应及异常信息。
 * </p>
 *
 * @author TestPlatform
 * @since 2026-06-15
 */
@Data
@Builder
@Schema(name = "BatchDebugResultVO", description = "批量调试中单个用例的执行结果")
public class BatchDebugResultVO {

    /**
     * 用例ID
     */
    @Schema(description = "用例ID")
    private String caseId;

    /**
     * 用例名称
     */
    @Schema(description = "用例名称")
    private String caseName;

    /**
     * 该用例是否执行成功（true=成功，false=失败或异常）
     */
    @Schema(description = "是否执行成功")
    private Boolean success;

    /**
     * 完整的调试响应结果（成功时填充）
     */
    @Schema(description = "调试响应详情")
    private CaseDebugResponseVO response;

    /**
     * 异常信息（失败时填充）
     */
    @Schema(description = "异常信息，失败时返回")
    private String errorMsg;
}
