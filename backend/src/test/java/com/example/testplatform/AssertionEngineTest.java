package com.example.testplatform;

import com.example.testplatform.apicase.domain.vo.AssertionResultVO;
import com.example.testplatform.execution.engine.assertion.AssertionEngine;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AssertionEngineTest {

    @Resource
    private AssertionEngine assertionEngine;

    @Test
    public void testStatusCodeAssertion() {
        // 准备测试数据
        String assertionsJson = "[{\"type\":\"STATUS_CODE\",\"expected\":\"200\",\"description\":\"状态码断言\"}]";

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 200);
        response.put("body", "{\"message\":\"success\"}");

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        Long duration = 100L;

        // 执行断言
        List<AssertionResultVO> results = assertionEngine.executeAssertions(
                assertionsJson, response, headers, duration);

        // 验证结果
        assertNotNull(results, "断言结果不应为null");
        assertEquals(1, results.size(), "应有1个断言结果");

        AssertionResultVO result = results.get(0);
        assertEquals("STATUS_CODE", result.getType(), "断言类型应为STATUS_CODE");
        assertEquals("200", result.getExpected(), "期望值应为200");
        assertEquals("200", result.getActual(), "实际值应为200");
        assertTrue(result.getPassed(), "断言应通过");
        assertNull(result.getFailReason(), "失败原因应为null");

        System.out.println("状态码断言测试通过: " + result.getDescription());
    }

    @Test
    public void testJsonPathAssertion() {
        // 准备测试数据
        String assertionsJson = "[{\"type\":\"JSON_PATH\",\"jsonPath\":\"$.data.name\",\"expected\":\"test\",\"description\":\"JSON路径断言\"}]";

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 200);
        response.put("body", "{\"data\":{\"name\":\"test\",\"age\":25}}");

        Map<String, String> headers = new HashMap<>();
        Long duration = 150L;

        // 执行断言
        List<AssertionResultVO> results = assertionEngine.executeAssertions(
                assertionsJson, response, headers, duration);

        // 验证结果
        assertNotNull(results, "断言结果不应为null");
        assertEquals(1, results.size(), "应有1个断言结果");

        AssertionResultVO result = results.get(0);
        assertEquals("JSON_PATH", result.getType(), "断言类型应为JSON_PATH");
        assertEquals("test", result.getExpected(), "期望值应为test");
        assertEquals("test", result.getActual(), "实际值应为test");
        assertTrue(result.getPassed(), "断言应通过");
        assertNull(result.getFailReason(), "失败原因应为null");

        System.out.println("JSON路径断言测试通过: " + result.getDescription());
    }

    @Test
    public void testRegexAssertion() {
        // 准备测试数据
        String assertionsJson = "[{\"type\":\"REGEX\",\"pattern\":\"success.*message\",\"expected\":\"match\",\"description\":\"正则表达式断言\"}]";

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 200);
        response.put("body", "{\"status\":\"success\",\"message\":\"操作成功\"}");

        Map<String, String> headers = new HashMap<>();
        Long duration = 120L;

        // 执行断言
        List<AssertionResultVO> results = assertionEngine.executeAssertions(
                assertionsJson, response, headers, duration);

        // 验证结果
        assertNotNull(results, "断言结果不应为null");
        assertEquals(1, results.size(), "应有1个断言结果");

        AssertionResultVO result = results.get(0);
        assertEquals("REGEX", result.getType(), "断言类型应为REGEX");
        assertTrue(result.getPassed(), "断言应通过");

        System.out.println("正则表达式断言测试通过: " + result.getDescription());
    }

    @Test
    public void testResponseTimeAssertion() {
        // 准备测试数据
        String assertionsJson = "[{\"type\":\"RESPONSE_TIME\",\"expected\":\"200\",\"description\":\"响应时间断言\"}]";

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 200);
        response.put("body", "{\"message\":\"success\"}");

        Map<String, String> headers = new HashMap<>();
        Long duration = 150L; // 实际响应时间150ms，小于期望的200ms

        // 执行断言
        List<AssertionResultVO> results = assertionEngine.executeAssertions(
                assertionsJson, response, headers, duration);

        // 验证结果
        assertNotNull(results, "断言结果不应为null");
        assertEquals(1, results.size(), "应有1个断言结果");

        AssertionResultVO result = results.get(0);
        assertEquals("RESPONSE_TIME", result.getType(), "断言类型应为RESPONSE_TIME");
        assertEquals("200", result.getExpected(), "期望值应为200");
        assertEquals("150", result.getActual(), "实际值应为150");
        assertTrue(result.getPassed(), "断言应通过");
        assertNull(result.getFailReason(), "失败原因应为null");

        System.out.println("响应时间断言测试通过: " + result.getDescription());
    }

    @Test
    public void testMultipleAssertions() {
        // 准备测试数据 - 多个断言规则
        String assertionsJson = "[" +
                "{\"type\":\"STATUS_CODE\",\"expected\":\"200\",\"description\":\"状态码断言\"}," +
                "{\"type\":\"RESPONSE_TIME\",\"expected\":\"300\",\"description\":\"响应时间断言\"}," +
                "{\"type\":\"JSON_PATH\",\"jsonPath\":\"$.code\",\"expected\":\"0\",\"description\":\"业务码断言\"}" +
                "]";

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 200);
        response.put("body", "{\"code\":\"0\",\"message\":\"success\"}");

        Map<String, String> headers = new HashMap<>();
        Long duration = 250L;

        // 执行断言
        List<AssertionResultVO> results = assertionEngine.executeAssertions(
                assertionsJson, response, headers, duration);

        // 验证结果
        assertNotNull(results, "断言结果不应为null");
        assertEquals(3, results.size(), "应有3个断言结果");

        // 验证所有断言都通过
        assertTrue(results.stream().allMatch(AssertionResultVO::getPassed), "所有断言都应通过");

        System.out.println("多重断言测试通过: 共" + results.size() + "个断言全部通过");
    }

    @Test
    public void testFailedAssertion() {
        // 准备测试数据 - 预期会失败的断言
        String assertionsJson = "[{\"type\":\"STATUS_CODE\",\"expected\":\"404\",\"description\":\"状态码断言\"}]";

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 200); // 实际状态码是200，但期望404
        response.put("body", "{\"message\":\"success\"}");

        Map<String, String> headers = new HashMap<>();
        Long duration = 100L;

        // 执行断言
        List<AssertionResultVO> results = assertionEngine.executeAssertions(
                assertionsJson, response, headers, duration);

        // 验证结果
        assertNotNull(results, "断言结果不应为null");
        assertEquals(1, results.size(), "应有1个断言结果");

        AssertionResultVO result = results.get(0);
        assertEquals("STATUS_CODE", result.getType(), "断言类型应为STATUS_CODE");
        assertEquals("404", result.getExpected(), "期望值应为404");
        assertEquals("200", result.getActual(), "实际值应为200");
        assertFalse(result.getPassed(), "断言不应通过");
        assertNotNull(result.getFailReason(), "失败原因不应为null");
        assertTrue(result.getFailReason().contains("期望值"), "失败原因应包含期望值信息");

        System.out.println("失败断言测试通过: " + result.getFailReason());
    }

    @Test
    public void testEmptyAssertions() {
        // 测试空断言规则的情况
        String assertionsJson = "[]";

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 200);

        Map<String, String> headers = new HashMap<>();
        Long duration = 100L;

        // 执行断言
        List<AssertionResultVO> results = assertionEngine.executeAssertions(
                assertionsJson, response, headers, duration);

        // 验证结果
        assertNotNull(results, "断言结果不应为null");
        assertEquals(0, results.size(), "空断言规则应返回空结果列表");

        System.out.println("空断言规则测试通过");
    }

    @Test
    public void testNullAssertions() {
        // 测试null断言规则的情况
        String assertionsJson = null;

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 200);

        Map<String, String> headers = new HashMap<>();
        Long duration = 100L;

        // 执行断言
        List<AssertionResultVO> results = assertionEngine.executeAssertions(
                assertionsJson, response, headers, duration);

        // 验证结果
        assertNotNull(results, "断言结果不应为null");
        assertEquals(0, results.size(), "null断言规则应返回空结果列表");

        System.out.println("null断言规则测试通过");
    }

    @Test
    public void testInvalidJsonAssertions() {
        // 测试无效JSON格式的断言规则
        String assertionsJson = "invalid json format";

        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", 200);

        Map<String, String> headers = new HashMap<>();
        Long duration = 100L;

        // 执行断言
        List<AssertionResultVO> results = assertionEngine.executeAssertions(
                assertionsJson, response, headers, duration);

        // 验证结果
        assertNotNull(results, "断言结果不应为null");
        assertEquals(1, results.size(), "无效JSON应返回1个错误结果");

        AssertionResultVO result = results.get(0);
        assertEquals("PLATFORM_ERROR", result.getType(), "错误类型应为PLATFORM_ERROR");
        assertFalse(result.getPassed(), "断言不应通过");
        assertNotNull(result.getFailReason(), "失败原因不应为null");

        System.out.println("无效JSON断言规则测试通过: " + result.getFailReason());
    }
}
