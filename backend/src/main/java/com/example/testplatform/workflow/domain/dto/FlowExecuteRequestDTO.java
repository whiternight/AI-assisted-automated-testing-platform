package com.example.testplatform.workflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 流程执行请求 DTO
 * <p>接收执行启动参数，启动指定流程定义</p>
 *
 * @author TestPlatform
 */
@Data
@Schema(description = "流程执行请求")
public class FlowExecuteRequestDTO {

    @NotBlank(message = "流程定义ID不能为空")
    @Schema(description = "流程定义 ID")
    private String flowDefinitionId;

    @Schema(description = "流程入参（全局变量）")
    private Map<String, Object> inputParams;

    @Schema(description = "执行环境，默认 default")
    private String env = "default";

    @Schema(description = "执行模式：1=同步, 2=异步，默认同步")
    private Integer executeMode = 1;

    @Schema(description = "超时时间（毫秒），默认30000")
    private Long timeout = 30000L;
}
