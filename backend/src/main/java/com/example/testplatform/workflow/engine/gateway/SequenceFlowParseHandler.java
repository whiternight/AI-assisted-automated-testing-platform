package com.example.testplatform.workflow.engine.gateway;

import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.impl.bpmn.parser.handler.AbstractBpmnParseHandler;
import org.flowable.engine.parse.BpmnParseHandler;
import org.flowable.bpmn.model.FlowableListener;
import org.springframework.stereotype.Component;

/**
 * SequenceFlow 解析处理器
 * <p>
 * 在 BPMN 部署解析阶段，自动为每条 {@code <sequenceFlow>} 注入一个 {@code take} 事件监听器，
 * 使得 {@link SequenceFlowTakeListener} 在连线被触发时被调用，将走向信息实时写入流程变量。
 * </p>
 * <p>
 * 注册方式（在 FlowableConfig 中）：
 * <pre>{@code
 * engineConfiguration.setPostBpmnParseHandlers(
 *     List.of(sequenceFlowParseHandler)
 * );
 * }</pre>
 * </p>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>无需手动在 BPMN XML 中添加 executionListener</li>
 *   <li>使用 delegateExpression 引用 Spring Bean，监听器可注入依赖</li>
 *   <li>仅对 sequenceFlow 类型生效</li>
 * </ul>
 * </p>
 *
 * @author TestPlatform
 */
@Slf4j
@Component
public class SequenceFlowParseHandler extends AbstractBpmnParseHandler<SequenceFlow> {

    /**
     * SequenceFlowTakeListener 的 Spring Bean 名称（对应 @Component 值）
     */
    private static final String LISTENER_DELEGATE_EXPRESSION = "${sequenceFlowTakeListener}";

    @Override
    protected Class<? extends BaseElement> getHandledType() {
        return SequenceFlow.class;
    }

    @Override
    protected void executeParse(BpmnParse bpmnParse, SequenceFlow sequenceFlow) {
        // 为所有连线注入take监听器，实时记录连线触发，用于前端动态显示执行路径
        FlowableListener listener = new FlowableListener();
        listener.setEvent("take");
        listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        listener.setImplementation(LISTENER_DELEGATE_EXPRESSION);

        sequenceFlow.getExecutionListeners().add(listener);

        log.debug("SequenceFlow 自动注入 take 监听器 - flowId={}, sourceRef={}, targetRef={}",
                sequenceFlow.getId(), sequenceFlow.getSourceRef(), sequenceFlow.getTargetRef());
    }
}
