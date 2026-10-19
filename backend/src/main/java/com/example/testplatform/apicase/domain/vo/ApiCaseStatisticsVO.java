package com.example.testplatform.apicase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 首页用例统计 VO
 *
 * @author TestPlatform
 */
@Data
@Builder
@Schema(description = "首页用例统计概览")
public class ApiCaseStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "总用例数")
    private Long totalCaseCount;

    @Schema(description = "HTTP 用例数")
    private Long httpCaseCount;

    @Schema(description = "Dubbo 用例数")
    private Long dubboCaseCount;

    @Schema(description = "测试成功率（%），保留一位小数")
    private Double successRate;
}
