package com.example.testplatform.app.domain.dto;

import com.example.testplatform.apicase.domain.dto.AssertionRuleDTO;
import com.example.testplatform.enums.ProtocolTypeEnum;
import com.example.testplatform.enums.ResponseFormatEnum;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DirectTestRequestDTO {

    @NotNull(message = "协议类型不能为空")
    private ProtocolTypeEnum protocolType;  // HTTP / DUBBO

    // ===== HTTP 专用 =====
    private String requestType;  // GET / POST / PUT / DELETE
    private String address;      // 完整 URL，如 https://api.example.com/user
    private Map<String, String> requestHeaders;
    private Map<String, Object> requestParams;
    private String requestBody;

    // ===== Dubbo 专用 =====
    private String serviceName;   // 服务全名，如 com.111.wts.selYp
    private String methodName;    // 方法名
    private String version;     // 版本号，默认 1.0.0
    private String group;       // 服务分组（可选）
    private String dubboUrl;    // 直连地址，如 dubbo://192.168.1.100:20880，不填则走注册中心
    private List<String> paramTypes;   // 参数类型全名
    private List<Object> paramValues;  // 参数值

    // ===== 通用 =====
    @NotNull(message = "响应格式不能为空")
    private ResponseFormatEnum responseFormat;

    private List<AssertionRuleDTO> responseAssertions;

    @Min(1000)
    @Max(120000)
    private Integer timeout = 30000;
}
