package com.example.testplatform.workflow.service;

import com.example.testplatform.workflow.domain.dto.FlowDeployRequestDTO;
import com.example.testplatform.workflow.domain.dto.FlowSaveDraftDTO;
import com.example.testplatform.workflow.domain.dto.FlowSaveRequestDTO;
import com.example.testplatform.workflow.domain.dto.FlowUpdateRequestDTO;
import com.example.testplatform.workflow.domain.po.FlowDefinition;
import com.example.testplatform.workflow.domain.vo.FlowDefinitionDetailVO;
import com.example.testplatform.workflow.domain.vo.FlowDefinitionVO;
import com.example.testplatform.workflow.domain.vo.FlowDeployResponseVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 流程定义业务扩展表 服务类
 *
 * @author TestPlatform
 */
public interface IFlowDefinitionService extends IService<FlowDefinition> {

    /**
     * 部署流程定义
     * <p>将 BPMN XML 部署到 Flowable 引擎，同时插入 flow_definition 和 flow_node 记录</p>
     *
     * @param dto 部署请求（含 BPMN XML 和节点配置）
     * @return 部署响应
     */
    FlowDeployResponseVO deploy(FlowDeployRequestDTO dto);

    /**
     * 分页查询流程列表
     * <p>仅返回当前用户创建或拥有的流程，可按 applicationId 过滤</p>
     *
     * @param pageNum       页码
     * @param pageSize      每页条数
     * @param name          流程名称（模糊查询，可为空）
     * @param applicationId 应用 ID（可为空，按应用过滤）
     * @return 分页结果
     */
    Page<FlowDefinitionVO> listFlows(Integer pageNum, Integer pageSize, String name, String applicationId);

    /**
     * 查询流程定义详情
     *
     * @param id 流程定义 ID（flow_definition 表主键）
     * @return 详情（含节点列表、连线列表、最近执行记录）
     */
    FlowDefinitionDetailVO getDetail(String id);

    /**
     * 逻辑删除流程定义
     *
     * @param id 流程定义 ID
     */
    void deleteFlow(String id);

    /**
     * 更新流程状态
     *
     * @param id     流程定义 ID
     * @param status 状态：0=草稿 1=未部署 2=已发布 3=已停用
     */
    void updateStatus(String id, Integer status);

    /**
     * 更新已部署的流程定义
     * <p>支持更新 BPMN XML（重新部署到 Flowable）、节点配置及基础信息</p>
     *
     * @param dto 更新请求（含流程定义 ID、可选的 BPMN XML 和节点配置）
     * @return 部署响应
     */
    FlowDeployResponseVO update(FlowUpdateRequestDTO dto);

    /**
     * 保存流程草稿
     * <p>仅保存基础信息（name、applicationId、businessDomainId），不保存 BPMN，状态 0=草稿</p>
     *
     * @param dto 草稿保存请求（id 可空，不传则新建，传则更新）
     * @return 流程定义 VO
     */
    FlowDefinitionVO saveDraft(FlowSaveDraftDTO dto);

    /**
     * 保存流程设计
     * <p>保存 BPMN XML 和 serviceTask 节点配置，状态 1=未部署，不部署到 Flowable</p>
     *
     * @param dto 流程设计保存请求（id 可空，不传则新建，传则更新）
     * @return 流程定义 VO
     */
    FlowDefinitionVO saveFlowDesign(FlowSaveRequestDTO dto);
}
