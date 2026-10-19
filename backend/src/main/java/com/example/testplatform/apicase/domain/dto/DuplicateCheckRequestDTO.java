package com.example.testplatform.apicase.domain.dto;

import com.example.testplatform.enums.ProtocolTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DuplicateCheckRequestDTO {
    @NotBlank(message = "应用ID不能为空")
    private String applicationId;

    @NotBlank(message = "请求地址不能为空")
    private String address;

    @NotNull(message = "协议类型不能为空")
    private ProtocolTypeEnum protocolType;

    private String excludeId;  // 更新时传入，排除自身
}

