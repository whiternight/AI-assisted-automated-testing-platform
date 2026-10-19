package com.example.testplatform.execution.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 自动化测试启动请求 DTO
 *
 * @author TestPlatform
 */
@Data
@Schema(name = "AutomationRunRequestDTO", description = "自动化测试启动请求")
public class AutomationRunRequestDTO {

    @NotEmpty(message = "用例ID列表不能为空")
    @Schema(description = "参与自动化测试的用例ID列表")
    private List<String> caseIds;

    @NotNull(message = "执行人不能为空")
    @Schema(description = "执行人ID（从弹窗中选择）")
    private Long executorId;

    @NotBlank(message = "用例集名称不能为空")
    @Schema(description = "用例集名称")
    private String testSuiteName;

    @Schema(description = "执行环境，默认 default")
    private String env = "default";

    @Schema(description = "单用例超时时间（毫秒）")
    private Integer timeout;
}
