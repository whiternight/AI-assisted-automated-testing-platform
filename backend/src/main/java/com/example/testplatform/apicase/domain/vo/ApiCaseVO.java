package com.example.testplatform.apicase.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ApiCaseVO {
    private String id;
    private String name;
    private String address;
    private String protocolType;  // HTTP / DUBBO
    private String requestType;   // GET / POST / PUT / DELETE
    private Map<String, String> requestHeaders;
    private Map<String, Object> requestParams;
    private String requestBody;
    private List<AssertionRuleVO> responseAssertions;
    private String responseFormat;
    private String applicationId;
    private String applicationName;
    private String businessDomainId;
    private String businessDomainName;
    private Boolean isAutomationEnabled;
    private String env;
    private Integer timeout;
    private Long creatorId;
    private String creatorName;
    private Long ownerId;
    private String ownerName;
    private Long updaterId;
    private String updaterName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 最近一次执行结果（冗余存储在api_case表）
    private String responseBody;
    private Boolean isSuccessful;
}
