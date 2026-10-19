package com.example.testplatform.common.config;

import com.example.testplatform.workflow.engine.gateway.SequenceFlowParseHandler;
import com.example.testplatform.workflow.engine.listener.FlowCompletionEventListener;
import com.example.testplatform.workflow.engine.listener.FlowNodeParseHandler;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.common.engine.impl.persistence.StrongUuidGenerator;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;

@Configuration
public class FlowableConfig {

    @Resource
    private SequenceFlowParseHandler sequenceFlowParseHandler;

    @Resource
    private FlowNodeParseHandler flowNodeParseHandler;

    @Resource
    private FlowCompletionEventListener flowCompletionEventListener;

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration>
    processEngineConfigurationConfigurer() {
        return engineConfiguration -> {
            engineConfiguration.setHistoryLevel(HistoryLevel.FULL);
            engineConfiguration.setAsyncExecutorActivate(true);
            // ID生成策略：UUID
            engineConfiguration.setIdGenerator(new StrongUuidGenerator());
            // 注册 BPMN 解析后处理器：自动为节点注入start/end监听器，为网关出线注入take监听器
            engineConfiguration.setPostBpmnParseHandlers(List.of(flowNodeParseHandler, sequenceFlowParseHandler));
            // 注册流程完成/取消事件监听器：流程结束时主动同步 flow_execution 状态，
            // 避免"执行已完成但记录仍为执行中"的脏数据问题
            engineConfiguration.setEventListeners(Arrays.asList(
                    flowCompletionEventListener
            ));
        };
    }
}
