package com.example.testplatform.workflow.engine.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.impl.bpmn.parser.handler.AbstractBpmnParseHandler;
import org.flowable.bpmn.model.FlowableListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FlowNodeParseHandler extends AbstractBpmnParseHandler<FlowNode> {

    private static final String NODE_ENTER_LISTENER = "${nodeEnterListener}";
    private static final String NODE_END_LISTENER = "${nodeEndListener}";

    @Override
    protected Class<? extends BaseElement> getHandledType() {
        return FlowNode.class;
    }

    @Override
    protected void executeParse(BpmnParse bpmnParse, FlowNode flowNode) {
        FlowableListener startListener = new FlowableListener();
        startListener.setEvent("start");
        startListener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        startListener.setImplementation(NODE_ENTER_LISTENER);
        flowNode.getExecutionListeners().add(startListener);

        FlowableListener endListener = new FlowableListener();
        endListener.setEvent("end");
        endListener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        endListener.setImplementation(NODE_END_LISTENER);
        flowNode.getExecutionListeners().add(endListener);

        log.debug("FlowNode 自动注入 start/end 监听器 - nodeId={}, nodeName={}",
                flowNode.getId(), flowNode.getName());
    }
}
