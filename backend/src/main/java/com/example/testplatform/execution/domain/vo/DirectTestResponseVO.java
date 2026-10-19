package com.example.testplatform.execution.domain.vo;

import com.example.testplatform.apicase.domain.vo.AssertionResultVO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DirectTestResponseVO {

    /** 是否成功 */
    private Boolean success;

    /** 执行耗时（毫秒） */
    private Long duration;

    /** 响应信息 */
    private ResponseInfoVO response;

    /** 断言结果 */
    private List<AssertionResultVO> assertions;

    /** 失败类型 */
    private String errorType;

    /** 错误信息 */
    private String errorMsg;

    /** 原始出参快照（JSON字符串，方便需要存储时直接使用） */
    private String responseSnapshot;
}
