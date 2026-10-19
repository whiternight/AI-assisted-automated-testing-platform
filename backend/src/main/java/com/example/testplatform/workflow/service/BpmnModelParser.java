package com.example.testplatform.workflow.service;

import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.RepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BPMN 模型解析工具
 * <p>
 * 基于 RepositoryService.getBpmnModel() 获取已部署的 BpmnModel，
 * 提供节点/连线查询的便捷方法，直接使用 Flowable 原生对象。
 * </p>
 *
 * @author TestPlatform
 */
@Slf4j
@Component
public class BpmnModelParser {

    @Autowired
    private RepositoryService repositoryService;

    /**
     * 根据流程定义 ID 获取已部署的 BpmnModel
     *
     * @param processDefinitionId Flowable 流程定义 ID
     * @return BpmnModel
     */
    public BpmnModel getBpmnModel(String processDefinitionId) {
        return repositoryService.getBpmnModel(processDefinitionId);
    }

    /**
     * 获取流程中所有流程元素
     *
     * @param bpmnModel BPMN 模型
     * @return 流程元素列表
     */
    public List<FlowElement> getAllFlowElements(BpmnModel bpmnModel) {
        List<FlowElement> elements = new ArrayList<>();
        if (bpmnModel == null) return elements;
        for (Process process : bpmnModel.getProcesses()) {
            elements.addAll(process.getFlowElements());
        }
        return elements;
    }

    /**
     * 根据节点 ID 获取 FlowNode
     *
     * @param bpmnModel BPMN 模型
     * @param nodeId    节点 ID（BPMN 元素 ID）
     * @return FlowNode，未找到返回 null
     */
    public FlowNode getNodeById(BpmnModel bpmnModel, String nodeId) {
        if (bpmnModel == null || nodeId == null) return null;
        for (Process process : bpmnModel.getProcesses()) {
            FlowElement element = process.getFlowElement(nodeId);
            if (element instanceof FlowNode flowNode) {
                return flowNode;
            }
        }
        return null;
    }

    /**
     * 获取指定节点的入线
     *
     * @param bpmnModel BPMN 模型
     * @param nodeId    节点 ID
     * @return 入线列表
     */
    public List<SequenceFlow> getIncomingFlows(BpmnModel bpmnModel, String nodeId) {
        List<SequenceFlow> incoming = new ArrayList<>();
        FlowNode node = getNodeById(bpmnModel, nodeId);
        if (node != null) {
            incoming.addAll(node.getIncomingFlows());
        }
        return incoming;
    }

    /**
     * 获取指定节点的出线
     *
     * @param bpmnModel BPMN 模型
     * @param nodeId    节点 ID
     * @return 出线列表
     */
    public List<SequenceFlow> getOutgoingFlows(BpmnModel bpmnModel, String nodeId) {
        List<SequenceFlow> outgoing = new ArrayList<>();
        FlowNode node = getNodeById(bpmnModel, nodeId);
        if (node != null) {
            outgoing.addAll(node.getOutgoingFlows());
        }
        return outgoing;
    }

    /**
     * 获取流程名称
     *
     * @param bpmnModel BPMN 模型
     * @return 流程名称
     */
    public String getProcessName(BpmnModel bpmnModel) {
        if (bpmnModel != null && bpmnModel.getMainProcess() != null) {
            return bpmnModel.getMainProcess().getName();
        }
        return null;
    }

    /**
     * 获取流程定义 ID
     *
     * @param bpmnModel BPMN 模型
     * @return 流程定义 ID
     */
    public String getProcessId(BpmnModel bpmnModel) {
        if (bpmnModel != null && bpmnModel.getMainProcess() != null) {
            return bpmnModel.getMainProcess().getId();
        }
        return null;
    }
}
