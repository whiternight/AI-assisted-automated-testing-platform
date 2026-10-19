package com.example.testplatform.apicase.domain.dto;

import com.example.testplatform.enums.RequestTypeEnum;
import com.example.testplatform.enums.ResponseFormatEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class HttpCaseCreateDTO {

    @NotBlank(message = "用例名称不能为空")
    @Size(max = 256, message = "用例名称长度不能超过256")
    private String name;

    @NotBlank(message = "请求地址不能为空")
    @Size(max = 512, message = "请求地址长度不能超过512")
    private String address;

    @NotNull(message = "请求类型不能为空")
    private RequestTypeEnum requestType;

    private Map<String, String> requestHeaders;

    private Map<String, Object> requestParams;

    private String requestBody;

    @NotBlank(message = "响应结果不能为空")
    private String responseBody;

    private List<AssertionRuleDTO> responseAssertions;

    @NotNull(message = "响应格式不能为空")
    private ResponseFormatEnum responseFormat;

    @NotBlank(message = "所属应用不能为空")
    private String applicationId;

    private Long businessDomainId;

    @NotNull(message = "超时时间不能为空")
    @Min(value = 1000, message = "超时时间最小1000ms")
    @Max(value = 120000, message = "超时时间最大120000ms")
    private Integer timeout = 30000;

    private String env = "default";
}
