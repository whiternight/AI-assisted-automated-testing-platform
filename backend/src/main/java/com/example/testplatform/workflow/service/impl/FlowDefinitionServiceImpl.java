package com.example.testplatform.workflow.service.impl;

import com.example.testplatform.app.domain.po.Application;
import com.example.testplatform.app.mapper.ApplicationMapper;
import com.example.testplatform.auth.domain.po.User;
import com.example.testplatform.auth.mapper.UserMapper;
import com.example.testplatform.common.exception.BusinessException;
import com.example.testplatform.common.util.SecurityUtil;
import com.example.testplatform.enums.BusinessExceptionEnum;
import com.example.testplatform.workflow.domain.dto.FlowDeployRequestDTO;
import com.example.testplatform.workflow.domain.dto.FlowNodeDeployDTO;
import com.example.testplatform.workflow.domain.dto.FlowSaveDraftDTO;
import com.example.testplatform.workflow.domain.dto.FlowSaveRequestDTO;
import com.example.testplatform.workflow.domain.dto.FlowUpdateRequestDTO;
import com.example.testplatform.workflow.domain.po.FlowDefinition;
import com.example.testplatform.workflow.domain.po.FlowExecution;
import com.example.testplatform.workflow.domain.vo.*;
import com.example.testplatform.workflow.mapper.FlowDefinitionMapper;
import com.example.testplatform.workflow.mapper.FlowExecutionMapper;
import com.example.testplatform.workflow.mapper.FlowNodeMapper;
import com.example.testplatform.workflow.service.BpmnDeploymentService;
import com.example.testplatform.workflow.service.BpmnModelParser;
import com.example.testplatform.workflow.service.IFlowDefinitionService;
import com.example.testplatform.workflow.service.IFlowExecutionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 流程定义业务扩展表 服务实现类
 *
 * @author TestPlatform
 */
@Slf4j
@Service
public class FlowDefinitionServiceImpl extends ServiceImpl<FlowDefinitionMapper, FlowDefinition> implements IFlowDefinitionService {

    @Resource
    private RepositoryService repositoryService;

    @Resource
    private RuntimeService runtimeService;

    @Resource
    private BpmnModelParser bpmnModelParser;

    @Resource
    private FlowExecutionMapper flowExecutionMapper;

    @Resource
    @Lazy
    private IFlowExecutionService flowExecutionService;

    @Resource
    private FlowNodeMapper flowNodeMapper;

    @Resource
    private BpmnDeploymentService bpmnDeploymentService;

    @Resource
    private ApplicationMapper applicationMapper;

    @Resource
    private UserMapper userMapper;

    /**
     * 部署流程定义到 Flowable 引擎
     * <p>支持两种部署方式：
     * <ol>
     *   <li>传入 id：部署已保存的未部署/草稿流程，BPMN XML 优先取入参，其次取已存记录</li>
     *   <li>不传 id（新建）：必须同时提供 name、applicationId 和 bpmnXml</li>
     * </ol>
     * 部署成功后状态置为 2（已发布），并自动为 serviceTask 注入 delegateExpression。</p>
     *
     * @param dto 部署请求（id 可空，新建场景 name/applicationId/bpmnXml 必填）
     * @return 部署响应（含 flowDefinitionId、deploymentId、procDefId、version）
     * @throws BusinessException 流程不存在、无权限、BPMN XML 解析失败或部署失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowDeployResponseVO deploy(FlowDeployRequestDTO dto) {
        log.info("开始部署流程: id={}, name={}, applicationId={}", dto.getId(), dto.getName(), dto.getApplicationId());

        // 0. 确定 BPMN XML：传入优先，否则尝试从已有记录读取
        String bpmnXml = dto.getBpmnXml();
        FlowDefinition flowDef = null;
        if (dto.getId() != null && !dto.getId().isBlank()) {
            flowDef = this.getById(dto.getId());
            if (flowDef == null) {
                throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "流程定义不存在: " + dto.getId());
            }
            requireFlowPermission(flowDef);
            if ((bpmnXml == null || bpmnXml.isBlank()) && flowDef.getDefinitionXml() != null) {
                bpmnXml = flowDef.getDefinitionXml();
            }
        } else {
            // 新建时必填字段校验
            if (dto.getName() == null || dto.getName().isBlank()) {
                throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR, "流程名称不能为空");
            }
            if (dto.getApplicationId() == null || dto.getApplicationId().isBlank()) {
                throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR, "所属应用不能为空");
            }
        }
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR, "BPMN XML 不能为空");
        }

        // 1. 仅校验 XML 可解析性（业务校验由前端保证）
        validateBpmnXml(bpmnXml);

        // 2. 部署 BPMN XML 到 Flowable 引擎
        String flowName = (dto.getName() != null && !dto.getName().isBlank())
                ? dto.getName()
                : (flowDef != null ? flowDef.getName() : "flow");
        Deployment deployment = bpmnDeploymentService.deploy(flowName, bpmnXml);
        log.info("Flowable 部署成功: deploymentId={}", deployment.getId());

        // 3. 获取流程定义 ID
        ProcessDefinition procDef = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        if (procDef == null) {
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR, "部署后未找到流程定义");
        }
        String procDefId = procDef.getId();
        log.info("流程定义已注册: procDefId={}, flowableVersion={}", procDefId, procDef.getVersion());

        // 4. 插入或更新 flow_definition 表
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (flowDef != null) {
            // 升级已有记录（草稿/未部署状态）：版本号递增（语义化版本 patch +1）
            String newVersion = incrementSemanticVersion(flowDef.getVersion());
            if (dto.getName() != null && !dto.getName().isBlank()) {
                flowDef.setName(dto.getName());
            }
            if (dto.getApplicationId() != null && !dto.getApplicationId().isBlank()) {
                flowDef.setApplicationId(dto.getApplicationId());
            }
            if (dto.getBusinessDomainId() != null) {
                flowDef.setBusinessDomainId(dto.getBusinessDomainId());
            }
            flowDef.setProcDefId(procDefId);
            flowDef.setDeploymentId(deployment.getId());
            flowDef.setDefinitionXml(bpmnXml);
            flowDef.setVersion(newVersion);
            flowDef.setStatus(2); // 2=已发布
            flowDef.setUpdaterId(currentUserId);
            flowDef.setUpdatedAt(LocalDateTime.now());
            this.updateById(flowDef);
            log.info("flow_definition 已升级: id={}, version={}", flowDef.getId(), newVersion);
        } else {
            // 新建记录：初始版本号 0.0.1
            flowDef = new FlowDefinition();
            flowDef.setName(dto.getName());
            flowDef.setApplicationId(dto.getApplicationId());
            flowDef.setBusinessDomainId(dto.getBusinessDomainId());
            flowDef.setProcDefId(procDefId);
            flowDef.setCreatorId(dto.getCreatorId() != null ? dto.getCreatorId() : currentUserId);
            flowDef.setOwnerId(dto.getOwnerId() != null ? dto.getOwnerId() : currentUserId);
            flowDef.setDeploymentId(deployment.getId());
            flowDef.setDefinitionXml(bpmnXml);
            flowDef.setVersion(INITIAL_VERSION);
            flowDef.setStatus(2); // 2=已发布
            flowDef.setCreatedAt(LocalDateTime.now());
            flowDef.setUpdatedAt(LocalDateTime.now());
            flowDef.setDeleted(0);
            this.save(flowDef);
            log.info("flow_definition 已插入: id={}, version={}", flowDef.getId(), INITIAL_VERSION);
        }

        // 5. Save serviceTask node config for OriginalServiceDelegate input/output resolution.
        if (dto.getNodes() != null) {
            saveFlowNodes(flowDef.getId(), dto.getNodes());
        }

        return FlowDeployResponseVO.builder()
                .flowDefinitionId(flowDef.getId())
                .deploymentId(deployment.getId())
                .procDefId(procDefId)
                .name(flowDef.getName())
                .version(flowDef.getVersion())
                .status(2)
                .deployedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 分页查询流程定义列表
     * <p>仅返回当前用户创建或拥有的流程，支持按应用 ID 和名称模糊过滤，
     * 结果按创建时间倒序排列，并补充 Flowable 部署信息、应用名称、创建人姓名及节点数量。</p>
     *
     * @param pageNum        页码（从 1 开始）
     * @param pageSize       每页大小
     * @param name           流程名称（模糊匹配，可空）
     * @param applicationId  应用 ID（精确匹配，可空）
     * @return 流程定义 VO 分页结果
     */
    @Override
    public Page<FlowDefinitionVO> listFlows(Integer pageNum, Integer pageSize, String name, String applicationId) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        log.info("分页查询流程列表: pageNum={}, pageSize={}, name={}, applicationId={}, userId={}",
                pageNum, pageSize, name, applicationId, currentUserId);

        // 1. 从 flow_definition 表分页查询（仅当前用户创建或拥有的流程，@TableLogic 自动过滤 deleted=1）
        Page<FlowDefinition> pageParam = new Page<>(pageNum, pageSize);
        Page<FlowDefinition> entityPage = this.lambdaQuery()
                .eq(applicationId != null && !applicationId.isBlank(), FlowDefinition::getApplicationId, applicationId)
                .like(name != null && !name.isBlank(), FlowDefinition::getName, name)
                .and(currentUserId != null, q -> q
                        .eq(FlowDefinition::getCreatorId, currentUserId)
                        .or()
                        .eq(FlowDefinition::getOwnerId, currentUserId)
                )
                .orderByDesc(FlowDefinition::getCreatedAt)
                .page(pageParam);

        // 2. 转换为 VO，补充 Flowable 部署信息
        List<FlowDefinitionVO> voList = new ArrayList<>();
        for (FlowDefinition def : entityPage.getRecords()) {
            FlowDefinitionVO.FlowDefinitionVOBuilder voBuilder = FlowDefinitionVO.builder()
                    .id(def.getId())
                    .name(def.getName())
                    .applicationId(def.getApplicationId())
                    .businessDomainId(def.getBusinessDomainId())
                    .procDefId(def.getProcDefId())
                    .status(def.getStatus())
                    .version(def.getVersion())
                    .creatorId(def.getCreatorId())
                    .ownerId(def.getOwnerId())
                    .createdAt(def.getCreatedAt())
                    .updatedAt(def.getUpdatedAt());

            // 从 Flowable 获取部署信息（版本号、部署 ID）
            enrichWithFlowableInfo(def, voBuilder);

            // 查询应用名称
            if (def.getApplicationId() != null && !def.getApplicationId().isBlank()) {
                Application app = applicationMapper.selectById(def.getApplicationId());
                if (app != null) {
                    voBuilder.applicationName(app.getName());
                }
            }

            // 查询创建人/负责人姓名
            voBuilder.creatorName(getUserName(def.getCreatorId()));
            voBuilder.ownerName(getUserName(def.getOwnerId()));

            // 统计节点数量
            voBuilder.nodeCount(countNodes(def.getProcDefId()));

            voList.add(voBuilder.build());
        }

        // 3. 构建分页结果
        Page<FlowDefinitionVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 查询流程定义详情
     * <p>聚合返回：基础信息、BPMN XML、节点列表、连线列表、最近 5 条执行记录及最近一次失败执行详情。
     * 节点/连线信息从 Flowable BpmnModel 解析，非自维护表。</p>
     *
     * @param id 流程定义 ID
     * @return 流程定义详情 VO
     * @throws BusinessException 流程不存在或无权限
     */
    @Override
    public FlowDefinitionDetailVO getDetail(String id) {
        log.info("查询流程定义详情: id={}", id);

        // 1. 查询 flow_definition
        FlowDefinition def = this.getById(id);
        if (def == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "流程定义不存在: " + id);
        }
        requireFlowPermission(def);

        // 2. 从 Flowable 获取部署信息和 BPMN 模型
        String deploymentId = null;
        String definitionXml = null;
        List<BpmnNodeInfoVO> nodes = Collections.emptyList();
        List<BpmnSequenceFlowInfoVO> sequences = Collections.emptyList();

        if (def.getProcDefId() != null && !def.getProcDefId().isBlank()) {
            // 获取流程定义信息
            ProcessDefinition procDef = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(def.getProcDefId())
                    .singleResult();
            if (procDef != null) {
                deploymentId = procDef.getDeploymentId();
            }

            // 获取 BPMN 模型，解析节点和连线
            BpmnModel bpmnModel = bpmnModelParser.getBpmnModel(def.getProcDefId());
            if (bpmnModel != null) {
                // 获取 BPMN XML
                try (java.io.InputStream is = repositoryService.getProcessModel(def.getProcDefId())) {
                    if (is != null) {
                        definitionXml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                } catch (Exception e) {
                    log.warn("获取 BPMN XML 失败: procDefId={}", def.getProcDefId(), e);
                }

                // 解析节点和连线
                for (Process process : bpmnModel.getProcesses()) {
                    java.util.Collection<FlowElement> elements = process.getFlowElements();
                    nodes = new ArrayList<>();
                    sequences = new ArrayList<>();
                    for (FlowElement element : elements) {
                        if (element instanceof FlowNode flowNode) {
                            nodes.add(BpmnNodeInfoVO.builder()
                                    .id(flowNode.getId())
                                    .name(flowNode.getName())
                                    .type(flowNode.getClass().getSimpleName())
                                    .build());
                        } else if (element instanceof SequenceFlow sf) {
                            sequences.add(BpmnSequenceFlowInfoVO.builder()
                                    .source(sf.getSourceRef())
                                    .target(sf.getTargetRef())
                                    .condition(sf.getConditionExpression())
                                    .build());
                        }
                    }
                }
            }
        }

        // 3. 查询最近执行记录（前5条）
        List<FlowExecutionBriefVO> recentExecutions = queryRecentExecutions(id);

        // 4. 查询最近一次失败的执行详情
        FlowExecuteResultVO lastFailedExecution = queryLastFailedExecution(id);

        // 4.1 草稿/未部署流程：从数据库取 XML，并加载节点绑定
        if (definitionXml == null || definitionXml.isBlank()) {
            definitionXml = def.getDefinitionXml();
        }
        // 加载 flow_node 表中的节点绑定（serviceTask 配置）
        List<BpmnNodeInfoVO> nodeBindings = loadNodeBindings(id);
        if (!nodeBindings.isEmpty()) {
            nodes = nodeBindings;
        }

        // 5. 构建详情 VO
        String applicationName = null;
        if (def.getApplicationId() != null && !def.getApplicationId().isBlank()) {
            Application app = applicationMapper.selectById(def.getApplicationId());
            if (app != null) {
                applicationName = app.getName();
            }
        }

        return FlowDefinitionDetailVO.builder()
                .id(def.getId())
                .name(def.getName())
                .applicationId(def.getApplicationId())
                .applicationName(applicationName)
                .businessDomainId(def.getBusinessDomainId())
                .procDefId(def.getProcDefId())
                .deploymentId(deploymentId)
                .version(def.getVersion())
                .status(def.getStatus())
                .creatorId(def.getCreatorId())
                .creatorName(getUserName(def.getCreatorId()))
                .ownerId(def.getOwnerId())
                .ownerName(getUserName(def.getOwnerId()))
                .definitionXml(definitionXml)
                .createdAt(def.getCreatedAt())
                .updatedAt(def.getUpdatedAt())
                .nodes(nodes)
                .sequences(sequences)
                .recentExecutions(recentExecutions)
                .lastFailedExecution(lastFailedExecution)
                .build();
    }

    /**
     * 删除流程定义
     * <p>执行逻辑：
     * <ol>
     *   <li>校验流程存在及当前用户权限</li>
     *   <li>检查是否存在活跃流程实例，存在则禁止删除</li>
     *   <li>删除 Flowable 部署（级联删除历史）</li>
     *   <li>逻辑删除 flow_definition 记录</li>
     * </ol>
     * </p>
     *
     * @param id 流程定义 ID
     * @throws BusinessException 流程不存在、无权限或存在活跃实例
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFlow(String id) {
        log.info("删除流程定义: id={}", id);

        // 1. 查询 flow_definition
        FlowDefinition def = this.getById(id);
        if (def == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "流程定义不存在: " + id);
        }
        requireFlowPermission(def);

        // 2. 检查是否有活跃的流程实例
        if (def.getProcDefId() != null && !def.getProcDefId().isBlank()) {
            long activeCount = runtimeService.createProcessInstanceQuery()
                    .processDefinitionId(def.getProcDefId())
                    .count();
            if (activeCount > 0) {
                throw new BusinessException(BusinessExceptionEnum.VALIDATE_ERROR,
                        "存在 " + activeCount + " 个活跃流程实例，无法删除");
            }

            // 3. 删除 Flowable 部署
            try {
                ProcessDefinition procDef = repositoryService.createProcessDefinitionQuery()
                        .processDefinitionId(def.getProcDefId())
                        .singleResult();
                if (procDef != null && procDef.getDeploymentId() != null) {
                    repositoryService.deleteDeployment(procDef.getDeploymentId(), true);
                    log.info("Flowable 部署已删除: deploymentId={}", procDef.getDeploymentId());
                }
            } catch (Exception e) {
                log.warn("删除 Flowable 部署失败: procDefId={}", def.getProcDefId(), e);
            }
        }

        // 4. 逻辑删除 flow_definition（使用 MyBatis-Plus 内置逻辑删除）
        this.removeById(id);

        log.info("流程定义已逻辑删除: id={}", id);
    }

    /**
     * 更新流程状态
     * <p>状态取值：0=草稿 1=未部署 2=已发布 3=已停用。仅创建者或负责人可操作。</p>
     *
     * @param id     流程定义 ID
     * @param status 目标状态（0~3）
     * @throws BusinessException 状态值无效、流程不存在或无权限
     */
    @Override
    public void updateStatus(String id, Integer status) {
        log.info("更新流程状态: id={}, status={}", id, status);

        // 校验状态值
        if (status == null || status < 0 || status > 3) {
            throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR, "状态值无效: " + status + "（允许 0=草稿 1=未部署 2=已发布 3=已停用）");
        }

        FlowDefinition def = this.getById(id);
        if (def == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "流程定义不存在: " + id);
        }
        requireFlowPermission(def);

        def.setStatus(status);
        def.setUpdaterId(SecurityUtil.getCurrentUserId());
        def.setUpdatedAt(LocalDateTime.now());
        this.updateById(def);

        log.info("流程状态已更新: id={}, status={}", id, status);
    }

    /**
     * 更新流程定义
     * <p>支持更新基础信息、BPMN XML（重新部署到 Flowable）及 serviceTask 节点配置。
     * 若提供新 BPMN XML，需先校验无活跃实例，再删除旧部署并重新部署。
     * 版本号策略：edited=true 时 +1（实质性编辑），否则保持不变（仅保存）。</p>
     *
     * @param dto 更新请求
     * @return 部署响应（含最新 procDefId、deploymentId、version）
     * @throws BusinessException 流程不存在、无权限、存在活跃实例或 BPMN 解析失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowDeployResponseVO update(FlowUpdateRequestDTO dto) {
        log.info("开始更新流程定义: id={}", dto.getId());

        // 1. 查询已有流程定义（@TableLogic 自动过滤 deleted=1）
        FlowDefinition existingDef = this.getById(dto.getId());
        if (existingDef == null) {
            throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "流程定义不存在: " + dto.getId());
        }
        requireFlowPermission(existingDef);

        // 2. 如果提供了新的 BPMN XML，重新部署到 Flowable
        String newProcDefId = existingDef.getProcDefId();
        String newDeploymentId = existingDef.getDeploymentId();
        String newDefinitionXml = existingDef.getDefinitionXml();

        if (dto.getBpmnXml() != null && !dto.getBpmnXml().isBlank()) {
            // 校验 BPMN XML 合法性
            validateBpmnXml(dto.getBpmnXml());

            // 检查是否有活跃的流程实例
            if (existingDef.getProcDefId() != null && !existingDef.getProcDefId().isBlank()) {
                long activeCount = runtimeService.createProcessInstanceQuery()
                        .processDefinitionId(existingDef.getProcDefId())
                        .count();
                if (activeCount > 0) {
                    throw new BusinessException(BusinessExceptionEnum.VALIDATE_ERROR,
                            "存在 " + activeCount + " 个活跃流程实例，无法更新 BPMN 定义");
                }

                // 删除旧的 Flowable 部署
                try {
                    ProcessDefinition oldProcDef = repositoryService.createProcessDefinitionQuery()
                            .processDefinitionId(existingDef.getProcDefId())
                            .singleResult();
                    if (oldProcDef != null && oldProcDef.getDeploymentId() != null) {
                        repositoryService.deleteDeployment(oldProcDef.getDeploymentId(), true);
                        log.info("旧 Flowable 部署已删除: deploymentId={}", oldProcDef.getDeploymentId());
                    }
                } catch (Exception e) {
                    log.warn("删除旧 Flowable 部署失败: {}", e.getMessage());
                }
            }

            // 重新部署
            String flowName = (dto.getName() != null && !dto.getName().isBlank()) ? dto.getName() : existingDef.getName();
            Deployment deployment = bpmnDeploymentService.deploy(flowName, dto.getBpmnXml());
            log.info("新 Flowable 部署成功: deploymentId={}", deployment.getId());

            ProcessDefinition procDef = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .singleResult();
            if (procDef == null) {
                throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR, "部署后未找到流程定义");
            }
            newProcDefId = procDef.getId();
            newDeploymentId = deployment.getId();
            newDefinitionXml = dto.getBpmnXml();
            log.info("流程定义已重新部署: procDefId={}, flowableVersion={}", newProcDefId, procDef.getVersion());
        }

        // 版本号计算：实质性编辑时语义化版本 patch +1，仅保存时保持不变
        String currentVersion = existingDef.getVersion() != null && !existingDef.getVersion().isBlank()
                ? existingDef.getVersion() : INITIAL_VERSION;
        String newVersion;
        if (Boolean.TRUE.equals(dto.getEdited())) {
            newVersion = incrementSemanticVersion(currentVersion);
            log.info("检测到实质性编辑，版本号递增: {} -> {}", currentVersion, newVersion);
        } else {
            newVersion = currentVersion;
            log.info("仅保存操作，版本号保持不变: {}", newVersion);
        }

        // 3. 更新 flow_definition 基础字段
        if (dto.getName() != null && !dto.getName().isBlank()) {
            existingDef.setName(dto.getName());
        }
        if (dto.getApplicationId() != null && !dto.getApplicationId().isBlank()) {
            existingDef.setApplicationId(dto.getApplicationId());
        }
        if (dto.getBusinessDomainId() != null) {
            existingDef.setBusinessDomainId(dto.getBusinessDomainId());
        }
        if (dto.getOwnerId() != null) {
            existingDef.setOwnerId(dto.getOwnerId());
        }
        existingDef.setProcDefId(newProcDefId);
        existingDef.setDeploymentId(newDeploymentId);
        // 仅在实际重新部署（提供了新 BPMN XML 且部署成功）时才将状态置为已发布，
        // 避免仅保存操作将已停用（status=3）的流程误改为已发布
        if (dto.getBpmnXml() != null && !dto.getBpmnXml().isBlank()
                && newProcDefId != null && !newProcDefId.isBlank()) {
            existingDef.setStatus(2);
        }
        existingDef.setVersion(newVersion);
        existingDef.setDefinitionXml(newDefinitionXml);
        existingDef.setUpdaterId(SecurityUtil.getCurrentUserId());
        existingDef.setUpdatedAt(LocalDateTime.now());
        this.updateById(existingDef);
        log.info("flow_definition 已更新: id={}", existingDef.getId());

        // 4. 如果提供了节点配置，保存 serviceTask 节点
        if (dto.getNodes() != null) {
            saveFlowNodes(existingDef.getId(), dto.getNodes());
        }

        // 5. 构建响应
        return FlowDeployResponseVO.builder()
                .flowDefinitionId(existingDef.getId())
                .deploymentId(newDeploymentId)
                .procDefId(newProcDefId)
                .name(existingDef.getName())
                .version(newVersion)
                .status(existingDef.getStatus())
                .deployedAt(LocalDateTime.now())
                .build();
    }

    // ==================== 草稿保存 ====================

    /**
     * 保存流程草稿
     * <p>仅保存基础信息（名称、应用、业务域），状态置为 0（草稿），不保存 BPMN XML 和节点配置。
     * id 可空：传入则更新已有草稿，不传则新建。version 初始化为 0。</p>
     *
     * @param dto 草稿保存请求
     * @return 流程定义 VO（含应用名称）
     * @throws BusinessException 流程不存在（更新场景）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowDefinitionVO saveDraft(FlowSaveDraftDTO dto) {
        log.info("保存流程草稿: id={}, name={}", dto.getId(), dto.getName());

        Long currentUserId = SecurityUtil.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        FlowDefinition def;

        if (dto.getId() != null && !dto.getId().isBlank()) {
            // 更新已有记录（@TableLogic 自动过滤 deleted=1）
            def = this.getById(dto.getId());
            if (def == null) {
                throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "流程定义不存在: " + dto.getId());
            }
            def.setName(dto.getName());
            def.setApplicationId(dto.getApplicationId());
            def.setBusinessDomainId(dto.getBusinessDomainId());
            def.setUpdatedAt(now);
            this.updateById(def);
            log.info("草稿已更新: id={}", def.getId());
        } else {
            // 新建记录
            def = new FlowDefinition();
            def.setName(dto.getName());
            def.setApplicationId(dto.getApplicationId());
            def.setBusinessDomainId(dto.getBusinessDomainId());
            def.setProcDefId(""); // 草稿未部署，置空字符串以满足 NOT NULL 约束
            def.setCreatorId(currentUserId);
            def.setOwnerId(currentUserId);
            def.setStatus(0); // 0=草稿
            def.setVersion(INITIAL_VERSION);
            def.setCreatedAt(now);
            def.setUpdatedAt(now);
            def.setDeleted(0);
            this.save(def);
            log.info("草稿已创建: id={}", def.getId());
        }

        // 查询应用名称
        String applicationName = null;
        if (def.getApplicationId() != null && !def.getApplicationId().isBlank()) {
            Application app = applicationMapper.selectById(def.getApplicationId());
            if (app != null) {
                applicationName = app.getName();
            }
        }

        return FlowDefinitionVO.builder()
                .id(def.getId())
                .name(def.getName())
                .applicationId(def.getApplicationId())
                .applicationName(applicationName)
                .businessDomainId(def.getBusinessDomainId())
                .status(def.getStatus())
                .createdAt(def.getCreatedAt())
                .updatedAt(def.getUpdatedAt())
                .build();
    }

    /**
     * 保存流程设计（自动部署）
     * <p>保存 BPMN XML 和 serviceTask 节点配置，**自动部署到 Flowable 引擎**，状态置为 2（已发布）。
     * id 可空：传入则更新已有记录，不传则新建。节点保存采用先删后插策略，仅保留 serviceTask。
     * 保存成功后流程立即可执行。</p>
     *
     * @param dto 流程设计保存请求（含 BPMN XML 和节点配置）
     * @return 流程定义 VO（含应用名称）
     * @throws BusinessException 流程不存在或无权限（更新场景）、BPMN解析失败或部署失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowDefinitionVO saveFlowDesign(FlowSaveRequestDTO dto) {
        log.info("保存流程设计并自动部署: id={}, name={}", dto.getId(), dto.getName());

        Long currentUserId = SecurityUtil.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        FlowDefinition def;

        // 校验BPMN XML合法性
        validateBpmnXml(dto.getBpmnXml());

        if (dto.getId() != null && !dto.getId().isBlank()) {
            // 更新已有记录（@TableLogic 自动过滤 deleted=1）
            def = this.getById(dto.getId());
            if (def == null) {
                throw new BusinessException(BusinessExceptionEnum.NOT_FOUND, "流程定义不存在: " + dto.getId());
            }
            requireFlowPermission(def);
            def.setName(dto.getName());
            def.setApplicationId(dto.getApplicationId());
            def.setBusinessDomainId(dto.getBusinessDomainId());
            def.setDefinitionXml(dto.getBpmnXml());

            // 如果之前已部署，删除旧部署
            if (def.getProcDefId() != null && !def.getProcDefId().isBlank()) {
                long activeCount = runtimeService.createProcessInstanceQuery()
                        .processDefinitionId(def.getProcDefId())
                        .count();
                if (activeCount > 0) {
                    throw new BusinessException(BusinessExceptionEnum.VALIDATE_ERROR,
                            "存在 " + activeCount + " 个活跃流程实例，无法更新流程定义");
                }
                try {
                    ProcessDefinition oldProcDef = repositoryService.createProcessDefinitionQuery()
                            .processDefinitionId(def.getProcDefId())
                            .singleResult();
                    if (oldProcDef != null && oldProcDef.getDeploymentId() != null) {
                        repositoryService.deleteDeployment(oldProcDef.getDeploymentId(), true);
                        log.info("旧 Flowable 部署已删除: deploymentId={}", oldProcDef.getDeploymentId());
                    }
                } catch (Exception e) {
                    log.warn("删除旧 Flowable 部署失败: {}", e.getMessage());
                }
            }
        } else {
            // 新建记录
            def = new FlowDefinition();
            def.setName(dto.getName());
            def.setApplicationId(dto.getApplicationId());
            def.setBusinessDomainId(dto.getBusinessDomainId());
            def.setCreatorId(currentUserId);
            def.setOwnerId(currentUserId);
            def.setVersion(INITIAL_VERSION);
            def.setCreatedAt(now);
            def.setDeleted(0);
            log.info("新建流程: id={}", def.getId());
        }

        // 部署到Flowable引擎
        String flowName = (dto.getName() != null && !dto.getName().isBlank()) ? dto.getName() : def.getName();
        Deployment deployment = bpmnDeploymentService.deploy(flowName, dto.getBpmnXml());
        log.info("Flowable 部署成功: deploymentId={}", deployment.getId());

        ProcessDefinition procDef = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        if (procDef == null) {
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR, "部署后未找到流程定义");
        }
        String procDefId = procDef.getId();

        // 更新流程定义信息，状态设为2已发布
        def.setProcDefId(procDefId);
        def.setDeploymentId(deployment.getId());
        def.setDefinitionXml(dto.getBpmnXml());
        def.setStatus(2); // 2=已发布，保存即部署可直接执行
        def.setUpdaterId(currentUserId);
        def.setUpdatedAt(now);

        if (dto.getId() != null && !dto.getId().isBlank()) {
            this.updateById(def);
            log.info("流程设计已更新并部署: id={}, procDefId={}", def.getId(), procDefId);
        } else {
            this.save(def);
            log.info("流程设计已创建并部署: id={}, procDefId={}", def.getId(), procDefId);
        }

        // 保存 serviceTask 节点配置（先删后插）
        saveFlowNodes(def.getId(), dto.getNodes());

        // 查询应用名称
        String applicationName = null;
        if (def.getApplicationId() != null && !def.getApplicationId().isBlank()) {
            Application app = applicationMapper.selectById(def.getApplicationId());
            if (app != null) {
                applicationName = app.getName();
            }
        }

        return FlowDefinitionVO.builder()
                .id(def.getId())
                .name(def.getName())
                .applicationId(def.getApplicationId())
                .applicationName(applicationName)
                .businessDomainId(def.getBusinessDomainId())
                .procDefId(procDefId)
                .deploymentId(deployment.getId())
                .version(def.getVersion())
                .status(def.getStatus())
                .createdAt(def.getCreatedAt())
                .updatedAt(def.getUpdatedAt())
                .deployedAt(now)
                .build();
    }

    /**
     * 保存 serviceTask 节点配置到 flow_node 表
     * <p>仅保存 serviceTask 节点，nodeType 统一为 3（原服务节点）</p>
     */
    private void saveFlowNodes(String flowDefinitionId, List<FlowNodeDeployDTO> nodes) {
        // 清除旧节点
        flowNodeMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.testplatform.workflow.domain.po.FlowNode>()
                        .eq(com.example.testplatform.workflow.domain.po.FlowNode::getFlowDefinitionId, flowDefinitionId)
        );
        log.info("旧 flow_node 已清除: flowDefinitionId={}", flowDefinitionId);

        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        int inserted = 0;
        for (FlowNodeDeployDTO nodeDTO : nodes) {
            // 仅保存 serviceTask 节点
            if (!isServiceTaskNode(nodeDTO)) {
                continue;
            }
            com.example.testplatform.workflow.domain.po.FlowNode node =
                    new com.example.testplatform.workflow.domain.po.FlowNode();
            node.setFlowDefinitionId(flowDefinitionId);
            node.setNodeKey(nodeDTO.getNodeKey());
            node.setNodeName(nodeDTO.getNodeName());
            node.setNodeType(3); // 统一为原服务节点
            node.setOriginalServiceId(nodeDTO.getOriginalServiceId());
            node.setInputParams(isBlankToNull(nodeDTO.getInputParams()));
            node.setOutputParams(isBlankToNull(nodeDTO.getOutputParams()));
            node.setTimeout(nodeDTO.getTimeout());
            node.setRetryCount(nodeDTO.getRetryCount() != null ? nodeDTO.getRetryCount() : 0);
            node.setRetryInterval(nodeDTO.getRetryInterval() != null ? nodeDTO.getRetryInterval() : 0);
            node.setRetryType(nodeDTO.getRetryType() != null ? nodeDTO.getRetryType() : 1);
            node.setAssertions(isBlankToNull(nodeDTO.getAssertions()));
            node.setIsReferenceValid(1);
            node.setCreatedAt(LocalDateTime.now());
            node.setUpdatedAt(LocalDateTime.now());
            node.setDeleted(0);
            flowNodeMapper.insert(node);
            inserted++;
        }
        log.info("flow_node 保存完成: count={}", inserted);
    }

    /**
     * 判断节点配置是否为 serviceTask（原服务节点）
     */
    private boolean isServiceTaskNode(FlowNodeDeployDTO nodeDTO) {
        if (nodeDTO == null || nodeDTO.getNodeType() == null) {
            return false;
        }
        return nodeDTO.getNodeType() == 3 || nodeDTO.getNodeType() == 4;
    }

    /**
     * 加载 flow_node 表中的节点绑定（serviceTask 配置），用于编辑时回填
     */
    private List<BpmnNodeInfoVO> loadNodeBindings(String flowDefinitionId) {
        List<com.example.testplatform.workflow.domain.po.FlowNode> flowNodes = flowNodeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.testplatform.workflow.domain.po.FlowNode>()
                        .eq(com.example.testplatform.workflow.domain.po.FlowNode::getFlowDefinitionId, flowDefinitionId)
        );
        if (flowNodes == null || flowNodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<BpmnNodeInfoVO> result = new ArrayList<>();
        for (com.example.testplatform.workflow.domain.po.FlowNode fn : flowNodes) {
            result.add(BpmnNodeInfoVO.builder()
                    .id(fn.getNodeKey())
                    .name(fn.getNodeName())
                    .type("serviceTask")
                    .originalServiceId(fn.getOriginalServiceId())
                    .inputParams(fn.getInputParams())
                    .outputParams(fn.getOutputParams())
                    .assertions(fn.getAssertions())
                    .timeout(fn.getTimeout())
                    .retryCount(fn.getRetryCount())
                    .retryInterval(fn.getRetryInterval())
                    .retryType(fn.getRetryType())
                    .build());
        }
        return result;
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据用户 ID 获取显示名称（优先取真实姓名，其次取用户名）
     */
    private String getUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                return null;
            }
            if (user.getRealName() != null && !user.getRealName().isBlank()) {
                return user.getRealName();
            }
            return user.getUsername();
        } catch (Exception e) {
            log.warn("查询用户名称失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 将空字符串转为 null，防止 MySQL JSON 类型列拒绝空字符串
     */
    private String isBlankToNull(String value) {
        return (value != null && !value.isBlank()) ? value : null;
    }

    /**
     * 校验 BPMN XML 可解析性
     * <p>后端不做业务校验，仅确保传入内容是合法 XML，防止 Flowable 部署时崩溃</p>
     *
     * @param bpmnXml BPMN XML 内容
     * @throws BusinessException XML 解析失败时抛出
     */
    private void validateBpmnXml(String bpmnXml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new java.io.ByteArrayInputStream(
                    bpmnXml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR,
                    "BPMN XML 解析失败: " + e.getMessage());
        }
    }

    /**
     * 检查当前用户是否有权限操作该流程定义
     * <p>只有创建者或负责人才有权限查看/删除/修改</p>
     *
     * @param def 流程定义
     * @throws BusinessException 无权限时抛出 FORBIDDEN
     */
    private void requireFlowPermission(FlowDefinition def) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(BusinessExceptionEnum.UNAUTHORIZED);
        }
        boolean isCreator = currentUserId.equals(def.getCreatorId());
        boolean isOwner = currentUserId.equals(def.getOwnerId());
        if (!isCreator && !isOwner) {
            throw new BusinessException(BusinessExceptionEnum.FORBIDDEN, "无权操作该流程，仅创建者或负责人可执行");
        }
    }

    /**
     * 从 Flowable 获取部署信息并补充到 VO
     */
    private void enrichWithFlowableInfo(FlowDefinition def, FlowDefinitionVO.FlowDefinitionVOBuilder voBuilder) {
        if (def.getProcDefId() == null || def.getProcDefId().isBlank()) {
            return;
        }
        try {
            ProcessDefinition procDef = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(def.getProcDefId())
                    .singleResult();
            if (procDef != null) {
                voBuilder.deploymentId(procDef.getDeploymentId());
                // 版本号使用 flow_definition 表中的语义化版本（def.getVersion()），不覆盖为 Flowable 内部版本
            }
        } catch (Exception e) {
            log.warn("查询 Flowable 流程定义失败: procDefId={}", def.getProcDefId(), e);
        }
    }

    /**
     * 统计流程节点数量（不含 sequenceFlow）
     */
    private Integer countNodes(String procDefId) {
        if (procDefId == null || procDefId.isBlank()) {
            return 0;
        }
        try {
            BpmnModel bpmnModel = bpmnModelParser.getBpmnModel(procDefId);
            if (bpmnModel == null) {
                return 0;
            }
            int count = 0;
            for (Process process : bpmnModel.getProcesses()) {
                for (FlowElement element : process.getFlowElements()) {
                    // FlowNode 包含 StartEvent/EndEvent/Task/Gateway 等，排除 SequenceFlow（连线）
                    // SequenceFlow 并不继承 FlowNode，因此 instanceof FlowNode 已隐含排除了 SequenceFlow
                    if (element instanceof FlowNode) {
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            log.warn("统计节点数量失败: procDefId={}", procDefId, e);
            return 0;
        }
    }

    /**
     * 语义化版本号递增（patch 位 +1）。
     * <p>格式：major.minor.patch，如 0.0.1 → 0.0.2 → 0.0.3。
     * 非法或空版本号将归一化为 0.0.1 后递增为 0.0.2。</p>
     *
     * @param currentVersion 当前版本号（如 "0.0.1"）
     * @return 递增后的版本号
     */
    private String incrementSemanticVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return "0.0.1";
        }
        String[] parts = currentVersion.trim().split("\\.");
        if (parts.length != 3) {
            return "0.0.1";
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]);
            return String.format("%d.%d.%d", major, minor, patch + 1);
        } catch (NumberFormatException e) {
            log.warn("版本号格式非法，归一化为 0.0.1: {}", currentVersion);
            return "0.0.1";
        }
    }

    /** 初始语义化版本号 */
    private static final String INITIAL_VERSION = "0.0.1";

    /**
     * 查询最近5条执行记录
     */
    private List<FlowExecutionBriefVO> queryRecentExecutions(String flowDefinitionId) {
        try {
            List<FlowExecution> records = flowExecutionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowExecution>()
                            .eq(FlowExecution::getFlowDefinitionId, flowDefinitionId)
                            .orderByDesc(FlowExecution::getStartTime)
                            .last("LIMIT 5")
            );

            List<FlowExecutionBriefVO> briefs = new ArrayList<>();
            for (FlowExecution exec : records) {
                briefs.add(FlowExecutionBriefVO.builder()
                        .id(exec.getId())
                        .procInstId(exec.getProcInstId())
                        .status(exec.getStatus())
                        .startTime(exec.getStartTime())
                        .endTime(exec.getEndTime())
                        .duration(exec.getDuration())
                        .build());
            }
            return briefs;
        } catch (Exception e) {
            log.warn("查询执行记录失败: flowDefinitionId={}", flowDefinitionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询最近一次失败的执行详情
     * <p>查找 status=2（失败）的最近一条执行记录，调用 getExecutionResult 获取完整结果</p>
     */
    private FlowExecuteResultVO queryLastFailedExecution(String flowDefinitionId) {
        try {
            FlowExecution lastFailed = flowExecutionMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowExecution>()
                            .eq(FlowExecution::getFlowDefinitionId, flowDefinitionId)
                            .eq(FlowExecution::getStatus, 2) // 2=失败
                            .orderByDesc(FlowExecution::getStartTime)
                            .last("LIMIT 1")
            );
            if (lastFailed == null || lastFailed.getProcInstId() == null) {
                return null;
            }
            return flowExecutionService.getExecutionResult(lastFailed.getProcInstId());
        } catch (Exception e) {
            log.warn("查询上次失败执行记录失败: flowDefinitionId={}", flowDefinitionId, e);
            return null;
        }
    }
}
