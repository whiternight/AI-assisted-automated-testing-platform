package com.example.testplatform.execution.engine;

import com.example.testplatform.apicase.domain.vo.AssertionResultVO;
import com.example.testplatform.enums.ErrorTypeEnum;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ExecuteContext {

    /** 任务ID */
    private String taskId;

    /** 节点实例ID */
    private String nodeInstanceId;

    /** 用例ID */
    private String caseId;

    /** 执行人ID */
    private Long executorId;

    /** 执行环境 */
    private String env;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 实际请求快照（已组装） */
    private String requestSnapshot;

    /** 合并后的请求头（用户输入覆盖用例默认） */
    private Map<String, String> mergedHeaders;

    /** 合并后的请求参数（用户输入覆盖用例默认） */
    private Map<String, Object> mergedParams;

    /** 合并后的请求体（用户输入完全覆盖用例默认） */
    private Object mergedBody;

    /** 实际响应快照 */
    private String responseSnapshot;

    /** 断言结果 */
    private List<AssertionResultVO> assertionResults;

    /** 是否成功（有响应即为 true，用于历史展示） */
    private Boolean success;

    /** 是否通过（HTTP 2xx 为 true，用于通过率计算） */
    private Boolean passed;

    /** 失败类型 */
    private ErrorTypeEnum errorType;

    /** 错误信息 */
    private String errorMsg;

    /** 执行耗时 */
    private Long duration;
}
