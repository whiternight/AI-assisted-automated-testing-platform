package com.example.testplatform.apicase.domain.dto;

import com.example.testplatform.enums.ResponseFormatEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DubboCaseCreateDTO {

    @NotBlank(message = "用例名称不能为空")
    @Size(max = 256, message = "用例名称长度不能超过256")
    private String name;

    @NotBlank(message = "服务地址不能为空")
    @Size(max = 512, message = "服务地址长度不能超过512")
    private String address;  // 如: com.111.wts.selYp

    @NotBlank(message = "方法名不能为空")
    private String methodName;

    @NotBlank(message = "版本号不能为空")
    private String version = "1.0.0";

    private List<String> paramTypes;  // 参数类型全名列表

    private List<Object> paramValues;  // 参数值列表

    private List<AssertionRuleDTO> responseAssertions;

    @NotNull(message = "响应格式不能为空")
    private ResponseFormatEnum responseFormat;

    @NotBlank(message = "所属应用不能为空")
    private String applicationId;

    private Long businessDomainId;

    @NotNull(message = "超时时间不能为空")
    private Integer timeout = 30000;

    private String env = "default";
}
