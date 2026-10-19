package com.example.testplatform.workflow.engine.delegate;

import com.example.testplatform.workflow.engine.trace.ExecutionTraceCollector;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 节点进入监听器
 * <p>在节点开始执行时触发，记录节点进入时间及相关信息</p>
 *
 * @author TestPlatform
 */
@Slf4j
@Component("nodeEnterListener")
public class NodeEnterListener implements ExecutionListener {

    @Autowired
    private ExecutionTraceCollector traceCollector;

    @Override
    public void notify(DelegateExecution execution) {
        String nodeId = execution.getCurrentActivityId();
        String nodeName = execution.getCurrentActivityName();
        String eventType = execution.getEventName();

        log.debug("节点进入 - procInstId={}, nodeId={}, nodeName={}, event={}",
                execution.getProcessInstanceId(), nodeId, nodeName, eventType);

        traceCollector.recordNodeStart(execution, nodeId, nodeName);
    }
}