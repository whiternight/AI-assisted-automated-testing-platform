package com.example.testplatform.apicase.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Dubbo用例更新DTO
 *
 * @author TestPlatform
 */
@Data
@Schema(description = "Dubbo用例更新DTO")
public class DubboCaseUpdateDTO {

    @NotBlank(message = "用例ID不能为空")
    @Schema(description = "用例ID")
    private String id;

    @Schema(description = "用例名称")
    private String name;

    @Schema(description = "接口名称")
    private String interfaceName;

    @Schema(description = "方法名称")
    private String methodName;

    @Schema(description = "参数列表")
    private List<String> parameters;

    @Schema(description = "超时时间(毫秒)")
    private Integer timeout;

    @Schema(description = "断言规则列表")
    private List<AssertionRuleDTO> assertionRules;
}
