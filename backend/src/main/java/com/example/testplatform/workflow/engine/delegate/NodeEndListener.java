package com.example.testplatform.workflow.engine.delegate;

import com.example.testplatform.workflow.engine.trace.ExecutionTraceCollector;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 节点结束监听器
 * <p>在节点执行结束时触发，记录节点离开时间及执行结果</p>
 *
 * @author TestPlatform
 */
@Slf4j
@Component("nodeEndListener")
public class NodeEndListener implements ExecutionListener {

    @Autowired
    private ExecutionTraceCollector traceCollector;

    @Override
    public void notify(DelegateExecution execution) {
        String nodeId = execution.getCurrentActivityId();
        String nodeName = execution.getCurrentActivityName();
        String eventType = execution.getEventName();

        log.debug("节点结束 - procInstId={}, nodeId={}, nodeName={}, event={}",
                execution.getProcessInstanceId(), nodeId, nodeName, eventType);

        Object result = execution.getVariable("node_" + nodeId + "_result");

        traceCollector.recordNodeEnd(execution, nodeId, result);
    }
}