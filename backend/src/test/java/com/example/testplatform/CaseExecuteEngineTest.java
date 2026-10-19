package com.example.testplatform;

import com.example.testplatform.apicase.domain.dto.CaseDebugRequestDTO;
import com.example.testplatform.apicase.domain.po.ApiCase;
import com.example.testplatform.apicase.domain.vo.CaseDebugResponseVO;
import com.example.testplatform.execution.engine.CaseExecuteEngine;
import com.example.testplatform.enums.ProtocolTypeEnum;
import com.example.testplatform.enums.RequestTypeEnum;
import com.example.testplatform.enums.ResponseFormatEnum;
import com.example.testplatform.apicase.mapper.ApiCaseMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class CaseExecuteEngineTest {

    @Resource
    private CaseExecuteEngine executeEngine;

    @Resource
    private ApiCaseMapper apiCaseMapper;

    private String testCaseId;

    @BeforeEach
    public void setUp() {
        // 清理测试数据或准备测试环境
        System.out.println("准备测试环境...");
    }

    @Test
    public void testExecuteHttpCase_Success() {
        // 准备测试数据：创建 HTTP 用例
        ApiCase apiCase = createHttpTestCase();
        apiCaseMapper.insert(apiCase);
        testCaseId = apiCase.getId();

        // 准备请求参数
        CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();
        requestDTO.setEnv("test");

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        requestDTO.setHeaders(headers);

        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        requestDTO.setParams(params);

        requestDTO.setTimeout(5000);

        // 执行
        CaseDebugResponseVO response = executeEngine.execute(testCaseId, requestDTO, 1L);

        // 验证结果
        assertNotNull(response, "响应不应为null");
        assertNotNull(response.getTaskId(), "任务ID不应为null");
        assertNotNull(response.getNodeInstanceId(), "节点实例ID不应为null");
        assertEquals("SUCCESS", response.getStatus(), "执行状态应为SUCCESS");
        assertTrue(response.getResult(), "执行结果应为成功");
        assertNotNull(response.getDuration(), "执行耗时不应为null");
        assertTrue(response.getDuration() >= 0, "执行耗时应该大于等于0");

        System.out.println("HTTP用例执行成功: taskId=" + response.getTaskId() +
                ", duration=" + response.getDuration() + "ms");
    }

    @Test
    public void testExecuteHttpCase_AssertionFail() {
        // 准备测试数据：创建带有断言规则的HTTP用例
        ApiCase apiCase = createHttpTestCaseWithAssertion();
        apiCaseMapper.insert(apiCase);
        testCaseId = apiCase.getId();

        // 准备请求参数
        CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();
        requestDTO.setEnv("test");
        requestDTO.setTimeout(5000);

        // 执行
        CaseDebugResponseVO response = executeEngine.execute(testCaseId, requestDTO, 1L);

        // 验证结果
        assertNotNull(response, "响应不应为null");
        assertEquals("FAILED", response.getStatus(), "执行状态应为FAILED");
        assertFalse(response.getResult(), "执行结果应为失败");
        assertNotNull(response.getAssertions(), "断言结果不应为null");
        assertTrue(response.getAssertions().size() > 0, "应有断言结果");
        assertEquals("ASSERTION_FAIL", response.getErrorType(), "错误类型应为ASSERTION_FAIL");
        assertNotNull(response.getErrorMsg(), "错误信息不应为null");

        System.out.println("HTTP用例断言失败: errorType=" + response.getErrorType() +
                ", errorMsg=" + response.getErrorMsg());
    }

    @Test
    public void testExecuteHttpCase_Timeout() {
        // 准备测试数据：创建超时用例
        ApiCase apiCase = createHttpTestCaseWithTimeout();
        apiCaseMapper.insert(apiCase);
        testCaseId = apiCase.getId();

        // 准备请求参数，设置很短的超时时间
        CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();
        requestDTO.setEnv("test");
        requestDTO.setTimeout(1); // 设置极短超时时间以触发超时

        // 执行
        CaseDebugResponseVO response = executeEngine.execute(testCaseId, requestDTO, 1L);

        // 验证结果
        assertNotNull(response, "响应不应为null");
        assertEquals("FAILED", response.getStatus(), "执行状态应为FAILED");
        assertFalse(response.getResult(), "执行结果应为失败");
        assertNotNull(response.getErrorType(), "错误类型不应为null");
        // 超时可能归类为ENV_ERROR或其他错误类型

        System.out.println("HTTP用例执行超时: errorType=" + response.getErrorType() +
                ", errorMsg=" + response.getErrorMsg());
    }

    @Test
    public void testExecuteDubboCase_Success() {
        // 准备测试数据：创建 Dubbo 用例
        ApiCase apiCase = createDubboTestCase();
        apiCaseMapper.insert(apiCase);
        testCaseId = apiCase.getId();

        // 准备请求参数
        CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();
        requestDTO.setEnv("test");

        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        requestDTO.setParams(params);

        requestDTO.setTimeout(5000);

        // 执行
        CaseDebugResponseVO response = executeEngine.execute(testCaseId, requestDTO, 1L);

        // 验证结果
        assertNotNull(response, "响应不应为null");
        assertNotNull(response.getTaskId(), "任务ID不应为null");
        assertNotNull(response.getNodeInstanceId(), "节点实例ID不应为null");
        // Dubbo执行结果取决于具体实现，这里主要验证基本结构
        assertNotNull(response.getStatus(), "执行状态不应为null");
        assertNotNull(response.getResult(), "执行结果不应为null");
        assertNotNull(response.getDuration(), "执行耗时不应为null");

        System.out.println("Dubbo用例执行: status=" + response.getStatus() +
                ", duration=" + response.getDuration() + "ms");
    }

    @Test
    public void testExecuteCase_InvalidCaseId() {
        // 测试无效用例ID的情况
        String invalidCaseId = "invalid-case-id";
        CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();
        requestDTO.setEnv("test");

        // 执行并验证异常
        Exception exception = assertThrows(Exception.class, () -> {
            executeEngine.execute(invalidCaseId, requestDTO, 1L);
        });

        assertTrue(exception.getMessage().contains("用例不存在"), "应抛出用例不存在的异常");
        System.out.println("无效用例ID测试通过: " + exception.getMessage());
    }

    @Test
    public void testExecuteCase_WithCustomHeadersAndBody() {
        // 准备测试数据
        ApiCase apiCase = createHttpTestCase();
        apiCaseMapper.insert(apiCase);
        testCaseId = apiCase.getId();

        // 准备请求参数，包含自定义headers和body
        CaseDebugRequestDTO requestDTO = new CaseDebugRequestDTO();
        requestDTO.setEnv("test");

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token123");
        headers.put("Custom-Header", "custom-value");
        requestDTO.setHeaders(headers);

        Map<String, Object> body = new HashMap<>();
        body.put("key1", "value1");
        body.put("key2", 123);
        requestDTO.setBody(body);

        requestDTO.setTimeout(5000);

        // 执行
        CaseDebugResponseVO response = executeEngine.execute(testCaseId, requestDTO, 1L);

        // 验证结果
        assertNotNull(response, "响应不应为null");
        assertNotNull(response.getTaskId(), "任务ID不应为null");
        assertEquals("SUCCESS", response.getStatus(), "执行状态应为SUCCESS");

        System.out.println("带自定义Headers和Body的用例执行成功");
    }

    // 辅助方法：创建HTTP测试用例
    private ApiCase createHttpTestCase() {
        ApiCase apiCase = new ApiCase();
        apiCase.setId(UUID.randomUUID().toString());
        apiCase.setName("测试HTTP用例");
        apiCase.setAddress("https://httpbin.org/get");
        apiCase.setProtocolType(1); // HTTP
        apiCase.setProtocolTypeEnum(ProtocolTypeEnum.HTTP);
        apiCase.setRequestType(RequestTypeEnum.valueOf("GET"));
        apiCase.setRequestHeaders("{\"Content-Type\":\"application/json\"}");
        apiCase.setRequestParams("{}");
        apiCase.setRequestBody("{}");
        apiCase.setResponseAssertions(null); // 无断言
        apiCase.setResponseFormat(ResponseFormatEnum.valueOf("JSON"));
        apiCase.setApplicationId("app-001");
        apiCase.setBusinessDomainId("domain-001");
        apiCase.setIsAutomationEnabled(1);
        apiCase.setEnv("test");
        apiCase.setTimeout(30000);
        apiCase.setCreatorId(1L);
        apiCase.setOwnerId(1L);
        apiCase.setUpdaterId(1L);
        apiCase.setCreatedAt(LocalDateTime.now());
        apiCase.setUpdatedAt(LocalDateTime.now());
        apiCase.setDeleted(0);
        return apiCase;
    }

    // 辅助方法：创建带断言规则的HTTP测试用例
    private ApiCase createHttpTestCaseWithAssertion() {
        ApiCase apiCase = createHttpTestCase();
        // 设置一个必定失败的断言规则
        String assertions = "[{\"type\":\"STATUS_CODE\",\"expected\":\"999\",\"description\":\"状态码断言\"}]";
        apiCase.setResponseAssertions(assertions);
        return apiCase;
    }

    // 辅助方法：创建超时测试用例
    private ApiCase createHttpTestCaseWithTimeout() {
        ApiCase apiCase = createHttpTestCase();
        apiCase.setAddress("https://httpbin.org/delay/10"); // 延迟10秒的接口
        return apiCase;
    }

    // 辅助方法：创建Dubbo测试用例
    private ApiCase createDubboTestCase() {
        ApiCase apiCase = new ApiCase();
        apiCase.setId(UUID.randomUUID().toString());
        apiCase.setName("测试Dubbo用例");
        apiCase.setAddress("com.example.DemoService");
        apiCase.setProtocolType(2); // DUBBO
        apiCase.setProtocolTypeEnum(ProtocolTypeEnum.DUBBO);
        apiCase.setRequestType(RequestTypeEnum.valueOf("POST"));
        apiCase.setRequestHeaders("{}");
        apiCase.setRequestParams("{}");
        apiCase.setRequestBody("{\"method\":\"sayHello\",\"params\":[\"world\"]}");
        apiCase.setResponseAssertions(null);
        apiCase.setResponseFormat(ResponseFormatEnum.valueOf("JSON"));
        apiCase.setApplicationId("app-001");
        apiCase.setBusinessDomainId("domain-001");
        apiCase.setIsAutomationEnabled(1);
        apiCase.setEnv("test");
        apiCase.setTimeout(30000);
        apiCase.setCreatorId(1L);
        apiCase.setOwnerId(1L);
        apiCase.setUpdaterId(1L);
        apiCase.setCreatedAt(LocalDateTime.now());
        apiCase.setUpdatedAt(LocalDateTime.now());
        apiCase.setDeleted(0);
        return apiCase;
    }
}
