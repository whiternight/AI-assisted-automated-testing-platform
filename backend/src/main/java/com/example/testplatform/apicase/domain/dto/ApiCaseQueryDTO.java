package com.example.testplatform.apicase.domain.dto;

import com.example.testplatform.enums.ProtocolTypeEnum;
import lombok.Data;

@Data
public class ApiCaseQueryDTO {

    private String keyword;  // 用例名称模糊搜索

    private String applicationId;  // 按应用筛选（UUID字符串）

    private Long businessDomainId;  // 按业务域筛选

    private ProtocolTypeEnum protocolType;  // 按协议类型筛选

    private Boolean isAutomationEnabled;  // 按自动化开关筛选

    private Long ownerId;  // 按负责人筛选

    private String env;  // 按环境筛选

    private Boolean lowActiveFlag;  // 低活跃度筛选

    // 分页参数
    private Long pageNum = 1L;
    private Long pageSize = 20L;

    // 排序
    private String orderBy = "updated_at";
    private Boolean isAsc = false;
}
