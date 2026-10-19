package com.example.testplatform.apicase.domain.vo;

import com.example.testplatform.execution.domain.vo.ResponseInfoVO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CaseDebugResponseVO {

    /** 任务ID */
    private String taskId;

    /** 节点实例ID */
    private String nodeInstanceId;

    /** 执行状态 */
    private String status;

    /** 是否成功 */
    private Boolean result;

    /** 执行耗时（毫秒） */
    private Long duration;

    /** 响应信息 */
    private ResponseInfoVO response;

    /** 断言结果列表 */
    private List<AssertionResultVO> assertions;

    /** 失败类型 */
    private String errorType;

    /** 错误信息 */
    private String errorMsg;
}
