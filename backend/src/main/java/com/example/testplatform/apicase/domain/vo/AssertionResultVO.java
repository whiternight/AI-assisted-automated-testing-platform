package com.example.testplatform.apicase.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssertionResultVO {

    /** 断言类型 */
    private String type;

    /** 断言描述 */
    private String description;

    /** 期望值 */
    private String expected;

    /** 实际值 */
    private String actual;

    /** 是否通过 */
    private Boolean passed;

    /** 失败原因 */
    private String failReason;
}
