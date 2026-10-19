package com.example.testplatform;

import com.example.testplatform.apicase.domain.po.ApiCase;
import com.example.testplatform.apicase.mapper.ApiCaseMapper;
import com.example.testplatform.enums.*;
import com.example.testplatform.execution.domain.po.Task;
import com.example.testplatform.execution.domain.po.TaskNodeInstance;
import com.example.testplatform.execution.domain.vo.AutomationTaskVO;
import com.example.testplatform.execution.service.ITaskNodeInstanceService;
import com.example.testplatform.execution.service.ITaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自动化测试功能测试类
 * <p>
 * 测试范围：
 * 1. 启动自动化测试（startAutomationRun）
 * 2. 查询自动化测试详情（getAutomationDetail）
 * 3. 重试失败用例（retryFailedCases）
 * 4. 分页查询自动化测试列表（listAutomationTasks）
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
public class AutomationTest {

    @Resource
    private ITaskService taskService;

    @Resource
    private ITaskNodeInstanceService taskNodeInstanceService;

    @Resource
    private ApiCaseMapper apiCaseMapper;

    /** 测试用 HTTP 用例 ID（成功用例） */
    private String successCaseId;
    /** 测试用 HTTP 用例 ID（必定失败的用例） */
    private String failCaseId;
    /** 自动化测试任务 ID */
    private String automationTaskId;

    @BeforeAll
    void initClass() {
        System.out.println("===== 自动化测试功能测试开始 =====");
    }

    /**
     * 准备测试用例数据
     */
    private void prepareTestCases() {
        // 1. 创建一个能成功的 HTTP 用例
        ApiCase successCase = new ApiCase();
        successCase.setId(UUID.randomUUID().toString());
        successCase.setName("自动化测试-成功用例");
        successCase.setAddress("https://httpbin.org/get");
        successCase.setProtocolType(1);
        successCase.setProtocolTypeEnum(ProtocolTypeEnum.HTTP);
        successCase.setRequestType(RequestTypeEnum.valueOf("GET"));
        successCase.setRequestHeaders("{\"Content-Type\":\"application/json\"}");
        successCase.setRequestParams("{}");
        successCase.setRequestBody("{}");
        successCase.setResponseAssertions(null);
        successCase.setResponseFormat(ResponseFormatEnum.valueOf("JSON"));
        successCase.setApplicationId("app-001");
        successCase.setBusinessDomainId("domain-001");
        successCase.setIsAutomationEnabled(1);
        successCase.setEnv("test");
        successCase.setTimeout(30000);
        successCase.setCreatorId(1L);
        successCase.setOwnerId(1L);
        successCase.setUpdaterId(1L);
        successCase.setCreatedAt(LocalDateTime.now());
        successCase.setUpdatedAt(LocalDateTime.now());
        successCase.setDeleted(0);
        apiCaseMapper.insert(successCase);
        successCaseId = successCase.getId();

        // 2. 创建一个必定失败的用例（错误地址）
        ApiCase failCase = new ApiCase();
        failCase.setId(UUID.randomUUID().toString());
        failCase.setName("自动化测试-失败用例");
        failCase.setAddress("https://this-domain-does-not-exist-12345.com/api");
        failCase.setProtocolType(1);
        failCase.setProtocolTypeEnum(ProtocolTypeEnum.HTTP);
        failCase.setRequestType(RequestTypeEnum.valueOf("GET"));
        failCase.setRequestHeaders("{}");
        failCase.setRequestParams("{}");
        failCase.setRequestBody("{}");
        failCase.setResponseAssertions(null);
        failCase.setResponseFormat(ResponseFormatEnum.valueOf("JSON"));
        failCase.setApplicationId("app-001");
        failCase.setBusinessDomainId("domain-001");
        failCase.setIsAutomationEnabled(1);
        failCase.setEnv("test");
        failCase.setTimeout(5000);
        failCase.setCreatorId(1L);
        failCase.setOwnerId(1L);
        failCase.setUpdaterId(1L);
        failCase.setCreatedAt(LocalDateTime.now());
        failCase.setUpdatedAt(LocalDateTime.now());
        failCase.setDeleted(0);
        apiCaseMapper.insert(failCase);
        failCaseId = failCase.getId();

        System.out.println("测试用例已创建: successCaseId=" + successCaseId + ", failCaseId=" + failCaseId);
    }

    // ==================== 测试 1: 启动自动化测试 ====================

    @Test
    @Order(1)
    void testStartAutomationRun() {
        prepareTestCases();

        // 启动自动化测试（2 个用例：1 成功 + 1 失败）
        List<String> caseIds = Arrays.asList(successCaseId, failCaseId);
        Task task = taskService.startAutomationRun(caseIds, 1L, "回归测试-自动化测试套件", "test", 30000);

        // 验证任务创建成功
        assertNotNull(task, "任务不应为 null");
        assertNotNull(task.getId(), "任务 ID 不应为 null");
        assertNotNull(task.getTaskNo(), "任务流水号不应为 null");
        assertEquals(1L, task.getExecutorId(), "执行人 ID 应为 1");
        assertEquals("回归测试-自动化测试套件", task.getTestSuiteName(), "用例集名称应匹配");
        assertEquals(ExecuteModeEnum.ASYNC.getCode(), task.getExecuteMode(), "执行模式应为异步");
        assertEquals(TaskStatusEnum.RUNNING.getCode(), task.getStatus(), "初始状态应为执行中");
        assertEquals(2, task.getTotalCases(), "总用例数应为 2");
        assertEquals(0, task.getRetryRound(), "初始重试轮次应为 0");
        assertEquals(0.0, task.getPassRate(), "初始通过率应为 0");

        // 保存任务 ID 供后续测试使用
        automationTaskId = task.getId();

        System.out.println("✅ 自动化测试任务创建成功: taskId=" + task.getId() +
                ", taskNo=" + task.getTaskNo() + ", testSuiteName=" + task.getTestSuiteName());
    }

    // ==================== 测试 2: 等待异步执行完成 + 查询详情 ====================

    @Test
    @Order(2)
    void testGetAutomationDetail_AfterExecution() throws InterruptedException {
        assertNotNull(automationTaskId, "任务 ID 不应为 null（依赖测试 1）");

        // 等待异步执行完成（最多等 30 秒）
        System.out.println("等待异步执行完成...");
        Task task = null;
        for (int i = 0; i < 30; i++) {
            TimeUnit.SECONDS.sleep(1);
            task = taskService.getById(automationTaskId);
            if (task != null && task.getStatus() != null &&
                    task.getStatus() != TaskStatusEnum.RUNNING.getCode()) {
                System.out.println("异步执行已完成，耗时约 " + (i + 1) + " 秒");
                break;
            }
        }

        assertNotNull(task, "任务应存在");
        assertNotEquals(TaskStatusEnum.RUNNING.getCode(), task.getStatus(),
                "任务应已完成（非执行中状态）");

        // 查询详情
        AutomationTaskVO detail = taskService.getAutomationDetail(automationTaskId);

        assertNotNull(detail, "详情不应为 null");
        assertEquals(automationTaskId, detail.getTaskId(), "任务 ID 应匹配");
        assertEquals("回归测试-自动化测试套件", detail.getTestSuiteName(), "用例集名称应匹配");
        assertEquals(1L, detail.getExecutorId(), "执行人应匹配");
        assertEquals(2, detail.getTotalCases(), "总用例数应为 2");
        assertNotNull(detail.getPassRate(), "通过率不应为 null");
        assertNotNull(detail.getStatusDesc(), "状态描述不应为 null");
        assertNotNull(detail.getNodeResults(), "节点结果列表不应为 null");
        assertTrue(detail.getNodeResults().size() >= 2, "应至少有 2 个节点结果");

        System.out.println("✅ 自动化测试详情查询成功: status=" + detail.getStatusDesc() +
                ", passRate=" + detail.getPassRate() + "%" +
                ", successCount=" + detail.getSuccessCount() +
                ", failCount=" + detail.getFailCount());

        // 验证每个节点结果
        detail.getNodeResults().forEach(node -> {
            System.out.println("  节点: caseId=" + node.getCaseId() +
                    ", caseName=" + node.getCaseName() +
                    ", status=" + node.getStatusDesc() +
                    ", success=" + node.getSuccess());
        });
    }

    // ==================== 测试 3: 重试失败用例 ====================

    @Test
    @Order(3)
    void testRetryFailedCases() throws InterruptedException {
        assertNotNull(automationTaskId, "任务 ID 不应为 null（依赖测试 1）");

        // 先检查是否有失败用例
        Task taskBefore = taskService.getById(automationTaskId);
        if (taskBefore.getFailCount() == null || taskBefore.getFailCount() == 0) {
            System.out.println("⚠️ 没有失败用例，跳过重试测试");
            return;
        }

        int retryRoundBefore = taskBefore.getRetryRound() != null ? taskBefore.getRetryRound() : 0;

        // 执行重试
        Task retryTask = taskService.retryFailedCases(automationTaskId);
        assertNotNull(retryTask, "重试任务不应为 null");
        assertEquals(retryRoundBefore + 1, retryTask.getRetryRound(), "重试轮次应 +1");
        assertEquals(TaskStatusEnum.RUNNING.getCode(), retryTask.getStatus(), "重试后状态应为执行中");

        System.out.println("✅ 重试已触发: retryRound=" + retryTask.getRetryRound());

        // 等待重试完成
        for (int i = 0; i < 20; i++) {
            TimeUnit.SECONDS.sleep(1);
            Task t = taskService.getById(automationTaskId);
            if (t != null && t.getStatus() != null &&
                    t.getStatus() != TaskStatusEnum.RUNNING.getCode()) {
                System.out.println("重试执行已完成，耗时约 " + (i + 1) + " 秒");
                break;
            }
        }

        // 验证重试后的详情
        AutomationTaskVO detail = taskService.getAutomationDetail(automationTaskId);
        assertNotNull(detail.getPassRate(), "重试后通过率不应为 null");

        System.out.println("✅ 重试后结果: status=" + detail.getStatusDesc() +
                ", passRate=" + detail.getPassRate() + "%" +
                ", successCount=" + detail.getSuccessCount() +
                ", failCount=" + detail.getFailCount());
    }

    // ==================== 测试 4: 重试无失败用例 ====================

    @Test
    @Order(4)
    void testRetryNoFailedCases() {
        // 创建一个新的自动化测试，只有成功用例
        List<String> caseIds = Arrays.asList(successCaseId);
        Task task = taskService.startAutomationRun(caseIds, 1L, "无失败用例测试", "test", 30000);
        assertNotNull(task, "任务不应为 null");

        // 等待执行完成
        try {
            for (int i = 0; i < 20; i++) {
                TimeUnit.SECONDS.sleep(1);
                Task t = taskService.getById(task.getId());
                if (t != null && t.getStatus() != null &&
                        t.getStatus() != TaskStatusEnum.RUNNING.getCode()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 重试（应无失败用例）
        Task retryResult = taskService.retryFailedCases(task.getId());
        assertNotNull(retryResult, "重试结果不应为 null");
        // 没有失败用例，重试轮次不应增加
        assertEquals(0, retryResult.getRetryRound() != null ? retryResult.getRetryRound() : 0,
                "无失败用例时重试轮次不应增加");

        System.out.println("✅ 无失败用例重试测试通过");
    }

    // ==================== 测试 5: 分页查询自动化测试列表 ====================

    @Test
    @Order(5)
    void testListAutomationTasks() {
        Page<Task> page = taskService.listAutomationTasks(1, 10);

        assertNotNull(page, "分页结果不应为 null");
        assertTrue(page.getTotal() > 0, "应有自动化测试任务记录");
        assertFalse(page.getRecords().isEmpty(), "记录列表不应为空");

        // 验证每条记录都是异步模式
        page.getRecords().forEach(task -> {
            assertEquals(ExecuteModeEnum.ASYNC.getCode(), task.getExecuteMode(),
                    "列表中每条记录应为异步模式");
        });

        System.out.println("✅ 分页查询成功: total=" + page.getTotal() +
                ", current=" + page.getCurrent() + ", size=" + page.getSize());
    }

    // ==================== 测试 6: 参数校验 ====================

    @Test
    @Order(6)
    void testStartAutomationRun_InvalidParams() {
        // 空用例列表
        assertThrows(IllegalArgumentException.class, () -> {
            taskService.startAutomationRun(null, 1L, "测试", "test", 5000);
        }, "空用例列表应抛异常");

        assertThrows(IllegalArgumentException.class, () -> {
            taskService.startAutomationRun(Arrays.asList(), 1L, "测试", "test", 5000);
        }, "空列表应抛异常");

        // 空执行人
        assertThrows(IllegalArgumentException.class, () -> {
            taskService.startAutomationRun(Arrays.asList("case1"), null, "测试", "test", 5000);
        }, "空执行人应抛异常");

        System.out.println("✅ 参数校验测试通过");
    }

    // ==================== 测试 7: 查询不存在的任务 ====================

    @Test
    @Order(7)
    void testGetAutomationDetail_NotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            taskService.getAutomationDetail("non-existent-task-id");
        }, "查询不存在的任务应抛异常");

        assertThrows(IllegalArgumentException.class, () -> {
            taskService.retryFailedCases("non-existent-task-id");
        }, "重试不存在的任务应抛异常");

        System.out.println("✅ 不存在任务查询测试通过");
    }

    // ==================== 清理测试数据 ====================

    @AfterAll
    void cleanup() {
        System.out.println("===== 清理测试数据 =====");
        // 清理测试用例
        if (successCaseId != null) {
            apiCaseMapper.deleteById(successCaseId);
        }
        if (failCaseId != null) {
            apiCaseMapper.deleteById(failCaseId);
        }
        // 清理自动化任务相关节点实例
        if (automationTaskId != null) {
            taskNodeInstanceService.remove(
                    new LambdaQueryWrapper<TaskNodeInstance>()
                            .eq(TaskNodeInstance::getTaskId, automationTaskId));
            taskService.removeById(automationTaskId);
        }
        System.out.println("===== 自动化测试功能测试结束 =====");
    }
}
