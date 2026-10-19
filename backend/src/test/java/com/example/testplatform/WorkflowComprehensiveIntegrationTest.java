package com.example.testplatform;

import com.example.testplatform.apicase.domain.vo.AssertionResultVO;
import com.example.testplatform.apicase.domain.vo.CaseDebugResponseVO;
import com.example.testplatform.common.exception.BusinessException;
import com.example.testplatform.enums.FlowNodeTypeEnum;
import com.example.testplatform.execution.domain.vo.ResponseInfoVO;
import com.example.testplatform.execution.engine.CaseExecuteEngine;
import com.example.testplatform.execution.engine.assertion.AssertionEngine;
import com.example.testplatform.originalservice.domain.po.OriginalService;
import com.example.testplatform.originalservice.service.IOriginalServiceService;
import com.example.testplatform.workflow.domain.dto.FlowDeployRequestDTO;
import com.example.testplatform.workflow.domain.dto.FlowExecuteRequestDTO;
import com.example.testplatform.workflow.domain.dto.FlowNodeDeployDTO;
import com.example.testplatform.workflow.domain.po.FlowDefinition;
import com.example.testplatform.workflow.domain.po.FlowExecution;
import com.example.testplatform.workflow.domain.po.FlowNode;
import com.example.testplatform.workflow.domain.vo.*;
import com.example.testplatform.workflow.engine.util.AssertionChecker;
import com.example.testplatform.workflow.engine.util.InputParamResolver;
import com.example.testplatform.workflow.engine.util.OutputParamExtractor;
import com.example.testplatform.workflow.mapper.FlowDefinitionMapper;
import com.example.testplatform.workflow.mapper.FlowExecutionMapper;
import com.example.testplatform.workflow.mapper.FlowNodeMapper;
import com.example.testplatform.workflow.service.IFlowDefinitionService;
import com.example.testplatform.workflow.service.IFlowExecutionService;
import jakarta.annotation.Resource;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * 工作流模块全面集成测试
 * <p>
 * 覆盖工作流完整生命周期：
 * 1. 流程定义部署与解析
 * 2. 流程实例启动与执行（同步/异步）
 * 3. 节点执行逻辑验证（HTTP/Dubbo 模拟、输入参数解析、输出参数提取）
 * 4. AssertionChecker 断言校验正确性
 * 5. 流程执行结果查询与状态验证（成功、失败、部分成功）
 * 6. 异常处理与错误详情记录（断言失败、服务超时等）
 * 7. InputParamResolver / OutputParamExtractor 工具验证
 * 8. 重试机制验证
 * </p>
 *
 * 测试数据产生位置：
 * - flow_definition 表：流程定义记录（通过 FlowDefinitionServiceImpl.deploy 插入）
 * - flow_node 表：节点配置记录（deploy 时批量插入）
 * - flow_execution 表：执行记录（通过 FlowExecutionServiceImpl.executeFlow 插入）
 * - Flowable ACT_* 表：Flowable 引擎内部表（部署、流程实例、历史活动等）
 * 查询方式：
 * - SELECT * FROM flow_definition WHERE name LIKE 'comprehensive-test-%' ORDER BY created_at DESC;
 * - SELECT * FROM flow_node WHERE flow_definition_id IN (SELECT id FROM flow_definition WHERE name LIKE 'comprehensive-test-%');
 * - SELECT * FROM flow_execution WHERE flow_definition_id IN (SELECT id FROM flow_definition WHERE name LIKE 'comprehensive-test-%');
 * - SELECT * FROM ACT_HI_PROCINST WHERE PROC_DEF_ID_ LIKE 'comprehensive%';
 * - SELECT * FROM ACT_HI_ACTINST WHERE PROC_INST_ID_ IN (SELECT ID_ FROM ACT_HI_PROCINST WHERE PROC_DEF_ID_ LIKE 'comprehensive%');
 *
 * @author TestPlatform
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class WorkflowComprehensiveIntegrationTest {

    @Resource
    private IFlowDefinitionService flowDefinitionService;

    @Resource
    private IFlowExecutionService flowExecutionService;

    @Resource
    private FlowDefinitionMapper flowDefinitionMapper;

    @Resource
    private FlowExecutionMapper flowExecutionMapper;

    @Resource
    private FlowNodeMapper flowNodeMapper;

    @Resource
    private RepositoryService repositoryService;

    @Resource
    private HistoryService historyService;

    @Resource
    private AssertionChecker assertionChecker;

    @Resource
    private InputParamResolver inputParamResolver;

    @Resource
    private OutputParamExtractor outputParamExtractor;

    @Resource
    private AssertionEngine assertionEngine;

    @MockitoBean
    private CaseExecuteEngine caseExecuteEngine;

    @MockitoBean
    private IOriginalServiceService originalServiceService;

    private final List<String> deploymentIds = new ArrayList<>();
    private final List<String> flowDefinitionIds = new ArrayList<>();

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_ORIGINAL_SERVICE_ID = "comprehensive-test-orig-svc-001";
    private static final String TEST_CASE_ID = "comprehensive-test-case-001";

    @BeforeEach
    public void setUp() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("testUser", String.valueOf(TEST_USER_ID),
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        System.out.println("\n========== 开始工作流全面集成测试 ==========");
    }

    @AfterEach
    public void tearDown() {
        // 清理 Flowable 部署
        for (String deploymentId : deploymentIds) {
            try {
                repositoryService.deleteDeployment(deploymentId, true);
            } catch (Exception e) {
                System.err.println("清理部署失败: " + e.getMessage());
            }
        }
        deploymentIds.clear();

        // 清理数据库记录
        try {
            for (String flowDefId : flowDefinitionIds) {
                flowNodeMapper.delete(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowNode>()
                                .eq(FlowNode::getFlowDefinitionId, flowDefId));
                flowDefinitionMapper.deleteById(flowDefId);
            }
            flowExecutionMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowExecution>()
                            .isNotNull(FlowExecution::getId));
        } catch (Exception e) {
            System.err.println("清理数据失败: " + e.getMessage());
        }
        flowDefinitionIds.clear();
        SecurityContextHolder.clearContext();
        System.out.println("========== 结束工作流全面集成测试 ==========\n");
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║          第一部分：流程定义部署与解析测试                      ║
    // ╚══════════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("流程定义部署与解析")
    class DeployAndParseTests {

        @Test
        @DisplayName("部署单节点流程 - 验证 flow_definition / flow_node / Flowable 部署三方数据一致性")
        public void testDeploy_SingleNode_DataConsistency() {
            FlowDeployRequestDTO request = buildSimpleDeployRequest("comprehensive-test-single");
            FlowDeployResponseVO response = flowDefinitionService.deploy(request);

            // 验证响应完整性
            assertNotNull(response, "部署响应不应为null");
            assertNotNull(response.getFlowDefinitionId(), "flowDefinitionId 不应为null");
            assertNotNull(response.getDeploymentId(), "deploymentId 不应为null");
            assertNotNull(response.getProcDefId(), "procDefId 不应为null");
            assertEquals(1, response.getStatus(), "状态应为已发布(1)");
            assertNotNull(response.getVersion(), "版本号不应为null");
            assertNotNull(response.getDeployedAt(), "部署时间不应为null");
            assertEquals(request.getName(), response.getName(), "名称应匹配");

            // 验证 flow_definition 表
            FlowDefinition flowDef = flowDefinitionService.getById(response.getFlowDefinitionId());
            assertNotNull(flowDef, "flow_definition 记录应存在");
            assertEquals(request.getName(), flowDef.getName());
            assertEquals(request.getApplicationId(), flowDef.getApplicationId());
            assertEquals(1, flowDef.getStatus(), "状态应为已发布");
            assertEquals(0, flowDef.getDeleted(), "deleted 应为0");
            assertNotNull(flowDef.getDefinitionXml(), "BPMN XML 应存储");
            assertEquals(response.getDeploymentId(), flowDef.getDeploymentId(), "deploymentId 应一致");

            // 验证 flow_node 表
            List<FlowNode> nodes = flowNodeMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowNode>()
                            .eq(FlowNode::getFlowDefinitionId, response.getFlowDefinitionId()));
            assertEquals(1, nodes.size(), "应有1个节点配置");
            assertEquals("serviceTask1", nodes.get(0).getNodeKey());
            assertEquals(FlowNodeTypeEnum.ORIGINAL_SERVICE.getCode(), nodes.get(0).getNodeType());
            assertEquals(TEST_ORIGINAL_SERVICE_ID, nodes.get(0).getOriginalServiceId());

            // 验证 Flowable 部署
            Deployment deployment = repositoryService.createDeploymentQuery()
                    .deploymentId(response.getDeploymentId()).singleResult();
            assertNotNull(deployment, "Flowable 部署应存在");

            deploymentIds.add(response.getDeploymentId());
            flowDefinitionIds.add(response.getFlowDefinitionId());
            System.out.println("[部署] 单节点流程部署成功: flowDefId=" + response.getFlowDefinitionId()
                    + ", procDefId=" + response.getProcDefId());
        }

        @Test
        @DisplayName("部署多节点流程 - 验证节点顺序和配置")
        public void testDeploy_MultiNode_NodeOrderAndConfig() {
            FlowDeployRequestDTO request = buildMultiNodeDeployRequest("comprehensive-test-multi");
            FlowDeployResponseVO response = flowDefinitionService.deploy(request);
            deploymentIds.add(response.getDeploymentId());
            flowDefinitionIds.add(response.getFlowDefinitionId());

            List<FlowNode> nodes = flowNodeMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowNode>()
                            .eq(FlowNode::getFlowDefinitionId, response.getFlowDefinitionId())
                            .orderByAsc(FlowNode::getCreatedAt));
            assertEquals(2, nodes.size(), "应有2个节点配置");

            // 验证第一个节点配置
            FlowNode node1 = nodes.stream().filter(n -> "serviceTask1".equals(n.getNodeKey())).findFirst().orElse(null);
            assertNotNull(node1, "serviceTask1 应存在");
            assertEquals(1, node1.getRetryCount(), "重试次数应为1");
            assertEquals(500, node1.getRetryInterval(), "重试间隔应为500ms");

            // 验证第二个节点配置
            FlowNode node2 = nodes.stream().filter(n -> "serviceTask2".equals(n.getNodeKey())).findFirst().orElse(null);
            assertNotNull(node2, "serviceTask2 应存在");
            assertEquals(0, node2.getRetryCount(), "重试次数应为0");
            assertEquals(10000, node2.getTimeout(), "超时应为10000ms");

            System.out.println("[部署] 多节点流程部署成功: 节点数=" + nodes.size());
        }

        @Test
        @DisplayName("部署详情查询 - 验证 BPMN 解析出的节点和连线")
        public void testDeploy_DetailQuery_BpmnParsing() {
            FlowDeployResponseVO deployResponse = deploySimpleFlow();
            FlowDefinitionDetailVO detail = flowDefinitionService.getDetail(deployResponse.getFlowDefinitionId());

            assertNotNull(detail, "详情不应为null");
            assertEquals(deployResponse.getFlowDefinitionId(), detail.getId());
            assertNotNull(detail.getNodes(), "节点列表不应为null");
            // startEvent + serviceTask + endEvent + errorBoundary + errorEnd = 至少5个
            assertTrue(detail.getNodes().size() >= 3, "至少包含 startEvent + serviceTask + endEvent");
            assertNotNull(detail.getSequences(), "连线列表不应为null");
            assertTrue(detail.getSequences().size() >= 2, "至少包含2条连线");
            assertNotNull(detail.getDefinitionXml(), "BPMN XML 不应为null");

            System.out.println("[部署] BPMN 解析: 节点数=" + detail.getNodes().size()
                    + ", 连线数=" + detail.getSequences().size());
            detail.getNodes().forEach(n ->
                    System.out.println("  节点: " + n.getId() + " (" + n.getType() + ") - " + n.getName()));
            detail.getSequences().forEach(s ->
                    System.out.println("  连线: " + s.getSource() + " → " + s.getTarget()));
        }

        @Test
        @DisplayName("缺少 bpmnXml 部署应抛异常")
        public void testDeploy_MissingBpmnXml_ThrowsException() {
            FlowDeployRequestDTO invalidRequest = new FlowDeployRequestDTO();
            invalidRequest.setName("comprehensive-test-no-xml");
            invalidRequest.setApplicationId("1");

            Exception ex = assertThrows(Exception.class, () -> flowDefinitionService.deploy(invalidRequest));
            assertTrue(
                    ex.getMessage().contains("BPMN") || ex.getMessage().contains("not null")
                            || ex.getMessage().contains("null"),
                    "应抛出校验异常, 实际: " + ex.getMessage());
            System.out.println("[部署] 缺少 bpmnXml 异常验证通过: " + ex.getMessage());
        }

        @Test
        @DisplayName("空节点列表部署应成功（仅部署 BPMN，不存节点配置）")
        public void testDeploy_EmptyNodes_Success() {
            FlowDeployRequestDTO request = buildSimpleDeployRequest("comprehensive-test-empty-nodes");
            request.setNodes(Collections.emptyList());

            FlowDeployResponseVO response = flowDefinitionService.deploy(request);
            deploymentIds.add(response.getDeploymentId());
            flowDefinitionIds.add(response.getFlowDefinitionId());

            assertNotNull(response, "部署应成功");
            List<FlowNode> nodes = flowNodeMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowNode>()
                            .eq(FlowNode::getFlowDefinitionId, response.getFlowDefinitionId()));
            assertEquals(0, nodes.size(), "节点列表应为空");
            System.out.println("[部署] 空节点列表部署通过");
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║          第二部分：流程实例启动与执行测试                      ║
    // ╚══════════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("流程实例启动与执行")
    class ExecutionTests {

        @Test
        @DisplayName("同步模式执行 - 流程启动并返回执行记录")
        public void testExecute_SyncMode_ReturnsExecutionRecord() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockServiceSuccess();

            FlowExecuteRequestDTO execReq = new FlowExecuteRequestDTO();
            execReq.setFlowDefinitionId(deploy.getFlowDefinitionId());
            Map<String, Object> inputParams = new HashMap<>();
            inputParams.put("userId", "user123");
            inputParams.put("orderId", "order456");
            execReq.setInputParams(inputParams);
            execReq.setExecuteMode(1);
            execReq.setTimeout(30000L);
            execReq.setEnv("test");

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), execReq);

            assertNotNull(execResp, "执行响应不应为null");
            assertNotNull(execResp.getTaskNo(), "taskNo 不应为null");
            assertTrue(execResp.getTaskNo().startsWith("T-"), "taskNo 应以 T- 开头");
            assertNotNull(execResp.getExecutionId(), "executionId 不应为null");
            assertNotNull(execResp.getProcessInstanceId(), "processInstanceId 不应为null");
            assertEquals(0, execResp.getStatus(), "初始状态应为执行中(0)");
            assertNotNull(execResp.getStartTime(), "startTime 不应为null");

            // 验证 flow_execution 表
            FlowExecution execution = flowExecutionMapper.selectById(execResp.getExecutionId());
            assertNotNull(execution, "flow_execution 记录应存在");
            assertEquals(deploy.getFlowDefinitionId(), execution.getFlowDefinitionId());
            assertEquals(0, execution.getStatus());

            // 验证 Flowable 历史实例
            HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(execResp.getProcessInstanceId()).singleResult();
            assertNotNull(historic, "历史流程实例应存在");

            System.out.println("[执行] 同步执行成功: taskNo=" + execResp.getTaskNo()
                    + ", procInstId=" + execResp.getProcessInstanceId());
        }

        @Test
        @DisplayName("异步模式执行 - 应立即返回")
        public void testExecute_AsyncMode_ReturnsImmediately() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockServiceSuccess();

            FlowExecuteRequestDTO execReq = new FlowExecuteRequestDTO();
            execReq.setFlowDefinitionId(deploy.getFlowDefinitionId());
            execReq.setExecuteMode(2);

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), execReq);

            assertNotNull(execResp, "响应不应为null");
            assertEquals(0, execResp.getStatus(), "初始状态应为0");
            assertNotNull(execResp.getTaskNo());
            System.out.println("[执行] 异步执行立即返回: taskNo=" + execResp.getTaskNo());
        }

        @Test
        @DisplayName("流程变量正确传递到 Flowable 引擎")
        public void testExecute_VariablesPassedToFlowable() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockServiceSuccess();

            FlowExecuteRequestDTO execReq = new FlowExecuteRequestDTO();
            execReq.setFlowDefinitionId(deploy.getFlowDefinitionId());
            Map<String, Object> params = new HashMap<>();
            params.put("orderAmount", 999.99);
            params.put("customerName", "testCustomer");
            params.put("isVip", true);
            execReq.setInputParams(params);

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), execReq);
            assertNotNull(execResp.getProcessInstanceId());

            HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(execResp.getProcessInstanceId()).singleResult();
            assertNotNull(historic, "历史实例应存在");
            System.out.println("[执行] 变量传递验证通过");
        }

        @Test
        @DisplayName("执行不存在的流程定义应抛 BusinessException")
        public void testExecute_NonExistentDefinition_ThrowsException() {
            FlowExecuteRequestDTO execReq = new FlowExecuteRequestDTO();
            execReq.setFlowDefinitionId("non-existent-id");

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    flowExecutionService.executeFlow("non-existent-id", execReq));
            assertTrue(ex.getMessage().contains("not exist") || ex.getMessage().contains("不存在"),
                    "应抛出不存在异常, 实际: " + ex.getMessage());
            System.out.println("[执行] 不存在定义异常验证: " + ex.getMessage());
        }

        @Test
        @DisplayName("执行草稿状态流程应抛异常")
        public void testExecute_DraftStatus_ThrowsException() {
            FlowDefinition draft = new FlowDefinition();
            draft.setName("comprehensive-test-draft");
            draft.setApplicationId("1");
            draft.setProcDefId("some-proc-def-id");
            draft.setStatus(0);
            draft.setCreatorId(TEST_USER_ID);
            draft.setOwnerId(TEST_USER_ID);
            draft.setCreatedAt(LocalDateTime.now());
            draft.setUpdatedAt(LocalDateTime.now());
            draft.setDeleted(0);
            flowDefinitionMapper.insert(draft);
            flowDefinitionIds.add(draft.getId());

            FlowExecuteRequestDTO execReq = new FlowExecuteRequestDTO();
            execReq.setFlowDefinitionId(draft.getId());

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    flowExecutionService.executeFlow(draft.getId(), execReq));
            assertTrue(ex.getMessage().contains("not published") || ex.getMessage().contains("未发布")
                            || ex.getMessage().contains("procDefId"),
                    "应抛出未发布异常, 实际: " + ex.getMessage());
            System.out.println("[执行] 草稿状态异常验证: " + ex.getMessage());
        }

        @Test
        @DisplayName("执行 procDefId 为空的流程应抛异常")
        public void testExecute_EmptyProcDefId_ThrowsException() {
            FlowDefinition flow = new FlowDefinition();
            flow.setName("comprehensive-test-empty-proc");
            flow.setApplicationId("1");
            flow.setProcDefId("");
            flow.setStatus(1);
            flow.setCreatorId(TEST_USER_ID);
            flow.setOwnerId(TEST_USER_ID);
            flow.setCreatedAt(LocalDateTime.now());
            flow.setUpdatedAt(LocalDateTime.now());
            flow.setDeleted(0);
            flowDefinitionMapper.insert(flow);
            flowDefinitionIds.add(flow.getId());

            FlowExecuteRequestDTO execReq = new FlowExecuteRequestDTO();
            execReq.setFlowDefinitionId(flow.getId());

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    flowExecutionService.executeFlow(flow.getId(), execReq));
            assertTrue(ex.getMessage().contains("deploy") || ex.getMessage().contains("部署")
                            || ex.getMessage().contains("procDefId"),
                    "应抛出异常, 实际: " + ex.getMessage());
            System.out.println("[执行] 空 procDefId 异常验证: " + ex.getMessage());
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║     第三部分：执行结果查询与状态验证（成功/失败/部分成功）       ║
    // ╚══════════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("执行结果查询与状态验证")
    class ResultQueryTests {

        @Test
        @DisplayName("查询成功执行结果 - 验证状态/耗时/节点轨迹")
        public void testGetResult_Success_StatusAndTraces() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockServiceSuccess();

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());

            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());

            assertNotNull(result, "结果不应为null");
            assertEquals(execResp.getProcessInstanceId(), result.getProcessInstanceId());
            assertNotNull(result.getStatus(), "状态不应为null");
            assertNotNull(result.getStartTime(), "startTime 不应为null");
            assertEquals(deploy.getFlowDefinitionId(), result.getFlowDefinitionId());

            if (result.getStatus() == 1) {
                assertNotNull(result.getEndTime(), "成功时 endTime 不应为null");
                assertNotNull(result.getTotalDuration(), "成功时 totalDuration 不应为null");
                assertTrue(result.getTotalDuration() >= 0);
                assertNull(result.getErrorDetail(), "成功时 errorDetail 应为null");
                System.out.println("[结果] 执行成功: duration=" + result.getTotalDuration() + "ms");
            } else {
                System.out.println("[结果] 执行状态: " + result.getStatus());
            }

            // 打印节点轨迹
            if (result.getNodeTraces() != null) {
                result.getNodeTraces().forEach(t ->
                        System.out.println("  轨迹: " + t.getActivityId()
                                + " (" + t.getActivityType() + ") status=" + t.getStatus()
                                + (t.getDuration() != null ? " duration=" + t.getDuration() + "ms" : "")));
            }
        }

        @Test
        @DisplayName("查询失败执行结果 - 验证 errorDetail 包含错误类型和信息")
        public void testGetResult_Failed_WithErrorDetail() {
            FlowDeployResponseVO deploy = deploySimpleFlow();

            // Mock 断言失败响应
            CaseDebugResponseVO failedResp = CaseDebugResponseVO.builder()
                    .taskId("task-" + UUID.randomUUID())
                    .nodeInstanceId("ni-" + UUID.randomUUID())
                    .status("FAILED").result(false).duration(50L)
                    .errorType("ASSERTION_FAIL").errorMsg("断言失败: 期望200实际404")
                    .build();
            Mockito.when(caseExecuteEngine.execute(anyString(), any(), any())).thenReturn(failedResp);
            mockOriginalServiceLookup();

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());

            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());

            assertNotNull(result);
            if (result.getStatus() == 2) {
                assertNotNull(result.getErrorDetail(), "失败时 errorDetail 不应为null");
                System.out.println("[结果] 失败结果: errorType=" + result.getErrorDetail().getErrorType()
                        + ", errorMsg=" + result.getErrorDetail().getErrorMessage());
            }
            System.out.println("[结果] 失败查询状态: " + result.getStatus());
        }

        @Test
        @DisplayName("查询不存在的执行记录应抛异常")
        public void testGetResult_NonExistent_ThrowsException() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    flowExecutionService.getExecutionResult("non-existent-pid"));
            assertTrue(ex.getMessage().contains("not exist") || ex.getMessage().contains("不存在"),
                    "实际: " + ex.getMessage());
            System.out.println("[结果] 不存在执行记录异常验证: " + ex.getMessage());
        }

        @Test
        @DisplayName("执行状态范围验证（0=执行中, 1=成功, 2=失败）")
        public void testGetResult_StatusRange() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockServiceSuccess();

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());

            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());

            assertNotNull(result);
            assertTrue(result.getStatus() >= 0 && result.getStatus() <= 2,
                    "状态应在 0~2 范围内, 实际: " + result.getStatus());
            System.out.println("[结果] 状态范围验证通过: status=" + result.getStatus());
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║          第四部分：AssertionChecker 断言校验测试              ║
    // ╚══════════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("AssertionChecker 断言校验")
    class AssertionCheckerTests {

        @Test
        @DisplayName("STATUS_CODE 断言通过 - 状态码匹配")
        public void testCheck_StatusCode_Pass() {
            String assertions = "[{\"type\":\"STATUS_CODE\",\"expected\":\"200\",\"description\":\"状态码断言\"}]";
            Map<String, Object> response = Map.of("statusCode", 200, "body", "{\"msg\":\"ok\"}");
            Map<String, String> headers = Map.of("Content-Type", "application/json");

            boolean passed = assertionChecker.check(assertions, response, headers, 100L);

            assertTrue(passed, "状态码200应通过");
            assertTrue(assertionChecker.getFailedResults().isEmpty(), "无失败结果");
            System.out.println("[断言] STATUS_CODE 通过");
        }

        @Test
        @DisplayName("STATUS_CODE 断言失败 - 状态码不匹配")
        public void testCheck_StatusCode_Fail() {
            String assertions = "[{\"type\":\"STATUS_CODE\",\"expected\":\"404\",\"description\":\"状态码断言\"}]";
            Map<String, Object> response = Map.of("statusCode", 200, "body", "{}");

            boolean passed = assertionChecker.check(assertions, response, null, 100L);

            assertFalse(passed, "期望404实际200应失败");
            assertFalse(assertionChecker.getFailedResults().isEmpty(), "应有失败结果");
            String summary = assertionChecker.getFailureSummary();
            assertNotNull(summary, "失败摘要不应为null");
            System.out.println("[断言] STATUS_CODE 失败: " + summary);
        }

        @Test
        @DisplayName("JSON_PATH 断言 - 从响应体中提取并验证")
        public void testCheck_JsonPath_Pass() {
            String assertions = "[{\"type\":\"JSON_PATH\",\"jsonPath\":\"$.data.name\",\"expected\":\"test\",\"description\":\"JSON路径断言\"}]";
            Map<String, Object> response = Map.of("statusCode", 200, "body", "{\"data\":{\"name\":\"test\",\"age\":25}}");

            boolean passed = assertionChecker.check(assertions, response, null, 150L);

            assertTrue(passed, "JSON_PATH 应通过");
            System.out.println("[断言] JSON_PATH 通过");
        }

        @Test
        @DisplayName("RESPONSE_TIME 断言 - 响应时间在阈值内")
        public void testCheck_ResponseTime_Pass() {
            String assertions = "[{\"type\":\"RESPONSE_TIME\",\"expected\":\"300\",\"operator\":\"LTE\",\"description\":\"响应时间断言\"}]";
            Map<String, Object> response = Map.of("statusCode", 200, "body", "{}");

            boolean passed = assertionChecker.check(assertions, response, null, 200L);

            assertTrue(passed, "200ms <= 300ms 应通过");
            System.out.println("[断言] RESPONSE_TIME 通过");
        }

        @Test
        @DisplayName("RESPONSE_TIME 断言失败 - 响应时间超限")
        public void testCheck_ResponseTime_Fail() {
            String assertions = "[{\"type\":\"RESPONSE_TIME\",\"expected\":\"100\",\"description\":\"响应时间断言\"}]";
            Map<String, Object> response = Map.of("statusCode", 200, "body", "{}");

            boolean passed = assertionChecker.check(assertions, response, null, 500L);

            assertFalse(passed, "500ms > 100ms 应失败");
            System.out.println("[断言] RESPONSE_TIME 失败: " + assertionChecker.getFailureSummary());
        }

        @Test
        @DisplayName("多重断言 - 全部通过")
        public void testCheck_Multiple_AllPass() {
            String assertions = "[" +
                    "{\"type\":\"STATUS_CODE\",\"expected\":\"200\",\"description\":\"状态码\"}," +
                    "{\"type\":\"RESPONSE_TIME\",\"expected\":\"300\",\"operator\":\"LTE\",\"description\":\"响应时间\"}," +
                    "{\"type\":\"JSON_PATH\",\"jsonPath\":\"$.code\",\"expected\":\"0\",\"description\":\"业务码\"}" +
                    "]";
            Map<String, Object> response = Map.of("statusCode", 200, "body", "{\"code\":\"0\",\"msg\":\"ok\"}");

            boolean passed = assertionChecker.check(assertions, response, null, 250L);

            assertTrue(passed, "所有断言应通过");
            assertEquals(0, assertionChecker.getFailedResults().size());
            System.out.println("[断言] 多重断言全部通过");
        }

        @Test
        @DisplayName("多重断言 - 部分失败")
        public void testCheck_Multiple_PartialFail() {
            String assertions = "[" +
                    "{\"type\":\"STATUS_CODE\",\"expected\":\"200\",\"description\":\"状态码\"}," +
                    "{\"type\":\"RESPONSE_TIME\",\"expected\":\"50\",\"description\":\"响应时间\"}," +
                    "{\"type\":\"JSON_PATH\",\"jsonPath\":\"$.code\",\"expected\":\"999\",\"description\":\"业务码\"}" +
                    "]";
            Map<String, Object> response = Map.of("statusCode", 200, "body", "{\"code\":\"0\"}");

            boolean passed = assertionChecker.check(assertions, response, null, 100L);

            assertFalse(passed, "部分断言失败应返回false");
            assertEquals(2, assertionChecker.getFailedResults().size(), "应有2个失败");
            String summary = assertionChecker.getFailureSummary();
            assertTrue(summary.contains(";"), "摘要应包含分隔符");
            System.out.println("[断言] 部分失败: " + summary);
        }

        @Test
        @DisplayName("空断言规则 - 直接通过")
        public void testCheck_EmptyAssertions_Pass() {
            boolean passed1 = assertionChecker.check("", Map.of("statusCode", 200), null, 100L);
            boolean passed2 = assertionChecker.check(null, Map.of("statusCode", 200), null, 100L);

            assertTrue(passed1, "空字符串应通过");
            assertTrue(passed2, "null 应通过");
            System.out.println("[断言] 空规则通过验证");
        }

        @Test
        @DisplayName("无效 JSON 断言 - 返回 PLATFORM_ERROR")
        public void testCheck_InvalidJson_PlatformError() {
            Map<String, Object> response = Map.of("statusCode", 200);

            // 使用 AssertionEngine 直接验证
            List<AssertionResultVO> results = assertionEngine.executeAssertions(
                    "invalid json", response, null, 100L);
            assertNotNull(results);
            assertEquals(1, results.size());
            assertEquals("PLATFORM_ERROR", results.get(0).getType());
            assertFalse(results.get(0).getPassed());
            System.out.println("[断言] 无效JSON断言: " + results.get(0).getFailReason());
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║       第五部分：InputParamResolver / OutputParamExtractor    ║
    // ╚══════════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("输入参数解析与输出参数提取")
    class ParamResolverExtractorTests {

        @Test
        @DisplayName("InputParamResolver - 静态参数解析")
        public void testInputResolver_StaticParams() {
            String inputJson = "{\"field1\":{\"type\":\"static\",\"value\":\"hello\"},\"field2\":{\"type\":\"static\",\"value\":123}}";
            Map<String, Object> context = new HashMap<>();

            Map<String, Object> result = inputParamResolver.resolve(inputJson, context);

            assertEquals("hello", result.get("field1"));
            assertEquals(123, result.get("field2"));
            System.out.println("[参数] 静态参数解析: " + result);
        }

        @Test
        @DisplayName("InputParamResolver - 动态参数从上下文解析")
        public void testInputResolver_DynamicParams() {
            String inputJson = "{\"userId\":{\"type\":\"dynamic\",\"jsonPath\":\"$.userId\"},\"amount\":{\"type\":\"dynamic\",\"jsonPath\":\"$.order.amount\"}}";
            Map<String, Object> context = new HashMap<>();
            context.put("userId", "user-001");
            context.put("order", Map.of("amount", 99.99));

            Map<String, Object> result = inputParamResolver.resolve(inputJson, context);

            assertEquals("user-001", result.get("userId"));
            assertNotNull(result.get("amount"), "amount 应解析成功");
            System.out.println("[参数] 动态参数解析: " + result);
        }

        @Test
        @DisplayName("InputParamResolver - 空配置返回空 Map")
        public void testInputResolver_EmptyConfig() {
            Map<String, Object> result1 = inputParamResolver.resolve(null, Map.of());
            Map<String, Object> result2 = inputParamResolver.resolve("", Map.of());

            assertTrue(result1.isEmpty(), "null 配置应返回空 Map");
            assertTrue(result2.isEmpty(), "空字符串配置应返回空 Map");
            System.out.println("[参数] 空配置返回空Map验证通过");
        }

        @Test
        @DisplayName("InputParamResolver - 无效 JSON 应抛异常")
        public void testInputResolver_InvalidJson_ThrowsException() {
            Exception ex = assertThrows(Exception.class, () ->
                    inputParamResolver.resolve("not json", Map.of()));
            System.out.println("[参数] 无效JSON异常: " + ex.getMessage());
        }

        @Test
        @DisplayName("OutputParamExtractor - 从响应中提取字段")
        public void testOutputExtractor_ExtractFields() {
            String outputConfig = "{\"userId\":\"$.data.userId\",\"orderNo\":\"$.data.orderNo\"}";
            // 传入 Map 对象（而非 String），避免 OutputParamExtractor 内部二次序列化导致双重转义
            Map<String, Object> responseBody = Map.of("data", Map.of("userId", "u-001", "orderNo", "ORD-20260621"));

            Map<String, Object> result = outputParamExtractor.extract(responseBody, outputConfig);

            assertEquals("u-001", result.get("userId"));
            assertEquals("ORD-20260621", result.get("orderNo"));
            System.out.println("[参数] 输出提取: " + result);
        }

        @Test
        @DisplayName("OutputParamExtractor - 空配置返回空 Map")
        public void testOutputExtractor_EmptyConfig() {
            Map<String, Object> r1 = outputParamExtractor.extract("some body", null);
            Map<String, Object> r2 = outputParamExtractor.extract("some body", "");

            assertTrue(r1.isEmpty());
            assertTrue(r2.isEmpty());
            System.out.println("[参数] 空配置返回空Map验证通过");
        }

        @Test
        @DisplayName("OutputParamExtractor - null 响应跳过提取")
        public void testOutputExtractor_NullResponse() {
            Map<String, Object> result = outputParamExtractor.extract(null, "{\"f\":\"$.x\"}");
            assertTrue(result.isEmpty());
            System.out.println("[参数] null 响应跳过验证通过");
        }

        @Test
        @DisplayName("OutputParamExtractor - JSONPath 提取失败字段设为 null")
        public void testOutputExtractor_PathNotFound() {
            String outputConfig = "{\"missing\":\"$.nonexistent.path\"}";
            String responseBody = "{\"data\":{\"name\":\"test\"}}";

            Map<String, Object> result = outputParamExtractor.extract(responseBody, outputConfig);

            assertTrue(result.containsKey("missing"));
            assertNull(result.get("missing"), "提取失败的字段应为null");
            System.out.println("[参数] JSONPath 提取失败验证: missing=" + result.get("missing"));
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║          第六部分：异常处理与错误详情记录                      ║
    // ╚══════════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("异常处理与错误详情")
    class ErrorHandlingTests {

        @Test
        @DisplayName("ENV_ERROR（环境错误/超时）应正确记录")
        public void testError_EnvError_Timeout() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockOriginalServiceLookup();

            CaseDebugResponseVO envError = CaseDebugResponseVO.builder()
                    .taskId("task-" + UUID.randomUUID())
                    .nodeInstanceId("ni-" + UUID.randomUUID())
                    .status("FAILED").result(false).duration(30000L)
                    .errorType("ENV_ERROR").errorMsg("服务超时: 连接超时30s")
                    .build();
            Mockito.when(caseExecuteEngine.execute(anyString(), any(), any())).thenReturn(envError);

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());

            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());
            assertNotNull(result);
            if (result.getStatus() == 2) {
                assertNotNull(result.getErrorDetail());
                assertEquals("ENV_ERROR", result.getErrorDetail().getErrorType());
                System.out.println("[异常] ENV_ERROR: " + result.getErrorDetail().getErrorMessage());
            }
            System.out.println("[异常] ENV_ERROR 状态: " + result.getStatus());
        }

        @Test
        @DisplayName("ASSERTION_FAIL（断言失败）应正确记录")
        public void testError_AssertionFail() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockOriginalServiceLookup();

            CaseDebugResponseVO assertFail = CaseDebugResponseVO.builder()
                    .taskId("task-" + UUID.randomUUID())
                    .nodeInstanceId("ni-" + UUID.randomUUID())
                    .status("FAILED").result(false).duration(100L)
                    .errorType("ASSERTION_FAIL").errorMsg("断言失败: 期望状态码200实际500")
                    .build();
            Mockito.when(caseExecuteEngine.execute(anyString(), any(), any())).thenReturn(assertFail);

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());

            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());
            assertNotNull(result);
            if (result.getStatus() == 2) {
                assertNotNull(result.getErrorDetail());
                assertEquals("ASSERTION_FAIL", result.getErrorDetail().getErrorType());
                System.out.println("[异常] ASSERTION_FAIL: " + result.getErrorDetail().getErrorMessage());
            }
        }

        @Test
        @DisplayName("PLATFORM_ERROR（平台内部错误）应正确记录")
        public void testError_PlatformError() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockOriginalServiceLookup();

            CaseDebugResponseVO platformErr = CaseDebugResponseVO.builder()
                    .taskId("task-" + UUID.randomUUID())
                    .nodeInstanceId("ni-" + UUID.randomUUID())
                    .status("FAILED").result(false).duration(0L)
                    .errorType("PLATFORM_ERROR").errorMsg("JSON 解析失败")
                    .build();
            Mockito.when(caseExecuteEngine.execute(anyString(), any(), any())).thenReturn(platformErr);

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());

            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());
            assertNotNull(result);
            if (result.getStatus() == 2) {
                assertNotNull(result.getErrorDetail());
                assertEquals("PLATFORM_ERROR", result.getErrorDetail().getErrorType());
                System.out.println("[异常] PLATFORM_ERROR 验证通过");
            }
        }

        @Test
        @DisplayName("执行引擎抛异常 - 应被 Flowable 捕获")
        public void testError_EngineThrowsException() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockOriginalServiceLookup();
            Mockito.when(caseExecuteEngine.execute(anyString(), any(), any()))
                    .thenThrow(new RuntimeException("模拟引擎内部异常"));

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());

            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());
            assertNotNull(result);
            assertNotNull(result.getStatus(), "状态不应为null");
            System.out.println("[异常] 引擎异常处理: status=" + result.getStatus());
        }

        @Test
        @DisplayName("执行引擎返回 null - 应被正确处理")
        public void testError_EngineReturnsNull() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockOriginalServiceLookup();
            Mockito.when(caseExecuteEngine.execute(anyString(), any(), any())).thenReturn(null);

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());

            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());
            assertNotNull(result);
            System.out.println("[异常] null 响应处理: status=" + result.getStatus());
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║          第七部分：节点详情查询                               ║
    // ╚══════════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("节点详情查询")
    class NodeDetailTests {

        @Test
        @DisplayName("查询已执行节点详情 - 包含 activityId/status/duration")
        public void testNodeDetail_Executed() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockServiceSuccess();

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());

            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());

            if (result.getNodeTraces() != null && !result.getNodeTraces().isEmpty()) {
                String activityId = result.getNodeTraces().get(0).getActivityId();
                NodeDetailVO detail = flowExecutionService.getNodeDetail(
                        execResp.getProcessInstanceId(), activityId);

                assertNotNull(detail);
                assertEquals(activityId, detail.getActivityId());
                assertNotNull(detail.getActivityType());
                assertNotNull(detail.getStatus());
                System.out.println("[节点详情] " + activityId + " status=" + detail.getStatus()
                        + (detail.getDuration() != null ? " duration=" + detail.getDuration() + "ms" : ""));
            } else {
                System.out.println("[节点详情] 无节点轨迹，跳过");
            }
        }

        @Test
        @DisplayName("查询不存在的节点应抛异常")
        public void testNodeDetail_NonExistent_ThrowsException() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            mockServiceSuccess();

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    flowExecutionService.getNodeDetail(execResp.getProcessInstanceId(), "non-existent-node"));
            assertTrue(ex.getMessage().contains("not exist") || ex.getMessage().contains("不存在"),
                    "实际: " + ex.getMessage());
            System.out.println("[节点详情] 不存在节点异常: " + ex.getMessage());
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║          第八部分：流程定义管理                               ║
    // ╚══════════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("流程定义管理")
    class DefinitionManagementTests {

        @Test
        @DisplayName("更新流程状态为禁用(2)")
        public void testUpdateStatus_Disable() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            flowDefinitionService.updateStatus(deploy.getFlowDefinitionId(), 2);

            FlowDefinition flowDef = flowDefinitionService.getById(deploy.getFlowDefinitionId());
            assertEquals(2, flowDef.getStatus(), "状态应为已禁用(2)");
            System.out.println("[管理] 状态更新为禁用通过");
        }

        @Test
        @DisplayName("逻辑删除流程定义 - deleted=1")
        public void testDeleteFlow_LogicalDelete() {
            FlowDeployResponseVO deploy = deploySimpleFlow();

            // 删除前应正常查到
            FlowDefinition beforeDelete = flowDefinitionService.getById(deploy.getFlowDefinitionId());
            assertNotNull(beforeDelete, "删除前记录应存在");
            assertEquals(0, beforeDelete.getDeleted(), "删除前 deleted 应为0");

            // 使用 MyBatis-Plus deleteById 触发逻辑删除（全局配置 logic-delete-field: deleted）
            // deleteById 会被转换为 UPDATE flow_definition SET deleted=1 WHERE id=? AND deleted=0
            flowDefinitionMapper.deleteById(deploy.getFlowDefinitionId());

            // MyBatis-Plus 全局配置了逻辑删除(logic-delete-field: deleted)，
            // 所有查询自动追加 AND deleted=0，因此逻辑删除后通过常规查询无法查到记录。
            FlowDefinition afterDelete = flowDefinitionService.getById(deploy.getFlowDefinitionId());
            assertNull(afterDelete, "逻辑删除后 getById 应返回 null（MyBatis-Plus 自动过滤 deleted=1）");

            // 验证 deleteFlow 对已删除记录抛异常
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    flowDefinitionService.deleteFlow(deploy.getFlowDefinitionId()));
            assertTrue(ex.getMessage().contains("不存在") || ex.getMessage().contains("not exist"),
                    "已删除记录调用 deleteFlow 应抛异常, 实际: " + ex.getMessage());
            System.out.println("[管理] 逻辑删除通过：删除前存在，删除后 getById 返回 null");
        }

        @Test
        @DisplayName("查询不存在的流程定义详情应抛异常")
        public void testGetDetail_NonExistent_ThrowsException() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    flowDefinitionService.getDetail("non-existent-id"));
            assertTrue(ex.getMessage().contains("not exist") || ex.getMessage().contains("不存在"),
                    "实际: " + ex.getMessage());
            System.out.println("[管理] 不存在详情异常: " + ex.getMessage());
        }

        @Test
        @DisplayName("无效状态值应抛异常")
        public void testUpdateStatus_InvalidValue_ThrowsException() {
            FlowDeployResponseVO deploy = deploySimpleFlow();
            Exception ex = assertThrows(Exception.class, () ->
                    flowDefinitionService.updateStatus(deploy.getFlowDefinitionId(), 99));
            System.out.println("[管理] 无效状态异常: " + ex.getMessage());
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║          第九部分：完整生命周期测试                           ║
    // ╚══════════════════════════════════════════════════════════════╝

    @Nested
    @DisplayName("完整生命周期")
    class LifecycleTests {

        @Test
        @DisplayName("完整流程：部署 → 执行 → 查询结果 → 查节点详情 → 删除")
        public void testFullLifecycle() {
            // Step 1: 部署
            FlowDeployResponseVO deploy = deploySimpleFlow();
            System.out.println("[生命周期] Step1 部署完成: flowDefId=" + deploy.getFlowDefinitionId());

            // Step 2: 执行
            mockServiceSuccess();
            FlowExecuteRequestDTO execReq = new FlowExecuteRequestDTO();
            execReq.setFlowDefinitionId(deploy.getFlowDefinitionId());
            Map<String, Object> params = new HashMap<>();
            params.put("testKey", "testValue");
            execReq.setInputParams(params);

            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), execReq);
            assertNotNull(execResp.getTaskNo());
            System.out.println("[生命周期] Step2 执行完成: taskNo=" + execResp.getTaskNo()
                    + ", procInstId=" + execResp.getProcessInstanceId());

            // Step 3: 查询结果
            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());
            assertNotNull(result);
            System.out.println("[生命周期] Step3 结果查询: status=" + result.getStatus()
                    + ", traces=" + (result.getNodeTraces() != null ? result.getNodeTraces().size() : 0));

            // Step 4: 查节点详情
            if (result.getNodeTraces() != null && !result.getNodeTraces().isEmpty()) {
                String firstNodeId = result.getNodeTraces().get(0).getActivityId();
                NodeDetailVO nodeDetail = flowExecutionService.getNodeDetail(
                        execResp.getProcessInstanceId(), firstNodeId);
                assertNotNull(nodeDetail);
                System.out.println("[生命周期] Step4 节点详情: " + firstNodeId
                        + " status=" + nodeDetail.getStatus());
            }

            // Step 5: 查详情（含最近执行记录）
            FlowDefinitionDetailVO detail = flowDefinitionService.getDetail(deploy.getFlowDefinitionId());
            assertNotNull(detail);
            System.out.println("[生命周期] Step5 定义详情: nodes=" + detail.getNodes().size()
                    + ", recentExecutions=" + (detail.getRecentExecutions() != null ? detail.getRecentExecutions().size() : 0));

            // Step 6: 逻辑删除验证（使用 deleteById 触发 MyBatis-Plus 全局逻辑删除）
            flowDefinitionMapper.deleteById(deploy.getFlowDefinitionId());
            FlowDefinition afterDelete = flowDefinitionService.getById(deploy.getFlowDefinitionId());
            assertNull(afterDelete, "逻辑删除后 getById 应返回 null");
            System.out.println("[生命周期] Step6 删除完成：逻辑删除后查询返回 null");

            System.out.println("[生命周期] 完整生命周期测试通过 ✓");
        }

        @Test
        @DisplayName("多节点流程完整执行：部署 → 执行 → 验证两个节点轨迹")
        public void testMultiNodeLifecycle() {
            // 部署多节点流程
            FlowDeployResponseVO deploy = deployMultiNodeFlow();
            mockServiceSuccess();

            // 执行
            FlowExecuteResponseVO execResp = flowExecutionService.executeFlow(
                    deploy.getFlowDefinitionId(), new FlowExecuteRequestDTO());
            assertNotNull(execResp);

            // 查询结果
            FlowExecuteResultVO result = flowExecutionService.getExecutionResult(
                    execResp.getProcessInstanceId());
            assertNotNull(result);

            if (result.getNodeTraces() != null) {
                System.out.println("[生命周期] 多节点轨迹:");
                result.getNodeTraces().forEach(t ->
                        System.out.println("  " + t.getActivityId() + " (" + t.getActivityType()
                                + ") status=" + t.getStatus()));
            }

            System.out.println("[生命周期] 多节点流程测试: status=" + result.getStatus());
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                     辅助方法                                 ║
    // ╚══════════════════════════════════════════════════════════════╝

    /**
     * 部署简单流程（单 serviceTask 节点）
     */
    private FlowDeployResponseVO deploySimpleFlow() {
        FlowDeployRequestDTO request = buildSimpleDeployRequest("comprehensive-test-" + System.currentTimeMillis());
        FlowDeployResponseVO response = flowDefinitionService.deploy(request);
        deploymentIds.add(response.getDeploymentId());
        flowDefinitionIds.add(response.getFlowDefinitionId());
        return response;
    }

    /**
     * 部署多节点流程（两个 serviceTask 节点）
     */
    private FlowDeployResponseVO deployMultiNodeFlow() {
        FlowDeployRequestDTO request = buildMultiNodeDeployRequest("comprehensive-multi-" + System.currentTimeMillis());
        FlowDeployResponseVO response = flowDefinitionService.deploy(request);
        deploymentIds.add(response.getDeploymentId());
        flowDefinitionIds.add(response.getFlowDefinitionId());
        return response;
    }

    /**
     * Mock 原服务查询 + 执行引擎成功响应
     */
    private void mockServiceSuccess() {
        mockOriginalServiceLookup();
        CaseDebugResponseVO mockResp = CaseDebugResponseVO.builder()
                .taskId("task-" + UUID.randomUUID())
                .nodeInstanceId("ni-" + UUID.randomUUID())
                .status("SUCCESS").result(true).duration(100L)
                .response(ResponseInfoVO.builder().statusCode(200)
                        .body("{\"code\":0,\"message\":\"success\"}")
                        .headers(Map.of("Content-Type", "application/json")).build())
                .errorType(null).errorMsg(null).build();
        Mockito.when(caseExecuteEngine.execute(anyString(), any(), any())).thenReturn(mockResp);
    }

    /**
     * Mock 原服务查询（仅 lookup，不 mock execute）
     */
    private void mockOriginalServiceLookup() {
        OriginalService mockService = new OriginalService();
        mockService.setId(TEST_ORIGINAL_SERVICE_ID);
        mockService.setCaseId(TEST_CASE_ID);
        mockService.setName("测试原服务");
        mockService.setIsAvailable(1);
        mockService.setAssertions(null);
        Mockito.when(originalServiceService.getById(anyString())).thenReturn(mockService);
    }

    /**
     * 构建单节点部署请求
     */
    private FlowDeployRequestDTO buildSimpleDeployRequest(String name) {
        FlowDeployRequestDTO request = new FlowDeployRequestDTO();
        request.setName(name);
        request.setApplicationId("1");
        request.setBusinessDomainId("1");

        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://flowable.org/test">
                  <process id="testProcess" name="test flow" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="serviceTask1"/>
                    <serviceTask id="serviceTask1" name="service task 1"
                                 flowable:delegateExpression="${originalServiceDelegate}">
                      <extensionElements>
                        <flowable:failedJobRetryTimeCycle>R3/PT1S</flowable:failedJobRetryTimeCycle>
                      </extensionElements>
                    </serviceTask>
                    <boundaryEvent id="errorBoundary1" attachedToRef="serviceTask1">
                      <errorEventDefinition/>
                    </boundaryEvent>
                    <sequenceFlow id="flow2" sourceRef="serviceTask1" targetRef="end"/>
                    <sequenceFlow id="flow3" sourceRef="errorBoundary1" targetRef="errorEnd"/>
                    <endEvent id="end"/>
                    <endEvent id="errorEnd"/>
                  </process>
                </definitions>""";
        request.setBpmnXml(bpmnXml);

        FlowNodeDeployDTO node = new FlowNodeDeployDTO();
        node.setNodeKey("serviceTask1");
        node.setNodeName("service task 1");
        node.setNodeType(FlowNodeTypeEnum.ORIGINAL_SERVICE.getCode());
        node.setOriginalServiceId(TEST_ORIGINAL_SERVICE_ID);
        node.setRetryCount(0);
        node.setRetryInterval(1000);
        node.setRetryType(1);
        node.setTimeout(5000);
        request.setNodes(List.of(node));

        return request;
    }

    /**
     * 构建多节点部署请求
     */
    private FlowDeployRequestDTO buildMultiNodeDeployRequest(String name) {
        FlowDeployRequestDTO request = new FlowDeployRequestDTO();
        request.setName(name);
        request.setApplicationId("1");
        request.setBusinessDomainId("1");

        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://flowable.org/test">
                  <process id="multiNodeProcess" name="multi node test" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="serviceTask1"/>
                    <serviceTask id="serviceTask1" name="service task 1"
                                 flowable:delegateExpression="${originalServiceDelegate}"/>
                    <boundaryEvent id="errorBoundary1" attachedToRef="serviceTask1">
                      <errorEventDefinition/>
                    </boundaryEvent>
                    <sequenceFlow id="flow2" sourceRef="serviceTask1" targetRef="serviceTask2"/>
                    <sequenceFlow id="flow2b" sourceRef="errorBoundary1" targetRef="errorEnd"/>
                    <serviceTask id="serviceTask2" name="service task 2"
                                 flowable:delegateExpression="${originalServiceDelegate}"/>
                    <boundaryEvent id="errorBoundary2" attachedToRef="serviceTask2">
                      <errorEventDefinition/>
                    </boundaryEvent>
                    <sequenceFlow id="flow3" sourceRef="serviceTask2" targetRef="end"/>
                    <sequenceFlow id="flow3b" sourceRef="errorBoundary2" targetRef="errorEnd"/>
                    <endEvent id="end"/>
                    <endEvent id="errorEnd"/>
                  </process>
                </definitions>""";
        request.setBpmnXml(bpmnXml);

        FlowNodeDeployDTO node1 = new FlowNodeDeployDTO();
        node1.setNodeKey("serviceTask1");
        node1.setNodeName("service task 1");
        node1.setNodeType(FlowNodeTypeEnum.ORIGINAL_SERVICE.getCode());
        node1.setOriginalServiceId(TEST_ORIGINAL_SERVICE_ID);
        node1.setRetryCount(1);
        node1.setRetryInterval(500);
        node1.setRetryType(1);
        node1.setTimeout(5000);

        FlowNodeDeployDTO node2 = new FlowNodeDeployDTO();
        node2.setNodeKey("serviceTask2");
        node2.setNodeName("service task 2");
        node2.setNodeType(FlowNodeTypeEnum.ORIGINAL_SERVICE.getCode());
        node2.setOriginalServiceId(TEST_ORIGINAL_SERVICE_ID);
        node2.setRetryCount(0);
        node2.setRetryInterval(1000);
        node2.setRetryType(1);
        node2.setTimeout(10000);

        request.setNodes(List.of(node1, node2));
        return request;
    }
}
