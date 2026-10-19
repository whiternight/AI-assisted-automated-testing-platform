package com.example.testplatform.execution.engine.assertion;

import com.example.testplatform.apicase.domain.dto.AssertionRuleDTO;
import com.example.testplatform.apicase.domain.vo.AssertionResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 断言引擎
 * <p>
 * 统一核心断言处理逻辑，支持两条调用入口：
 * 1. executeDirect  —— 直连测试，接受 AssertionRuleDTO 列表
 * 2. executeAssertions —— 任务引擎，接受 JSON 字符串
 *
 * @author TestPlatform
 */
@Slf4j
@Component
public class AssertionEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 公开入口 ====================

    /**
     * 直连测试断言执行
     *
     * @param rules    断言规则 DTO 列表
     * @param response 响应对象（HttpResponse 或 DubboResponse）
     * @return 断言结果列表
     */
    public List<AssertionResultVO> executeDirect(List<AssertionRuleDTO> rules, Object response) {
        List<AssertionResultVO> results = new ArrayList<>();
        if (rules == null || rules.isEmpty()) {
            return results;
        }

        log.info("[Direct] 开始执行断言: total={}", rules.size());

        for (AssertionRuleDTO rule : rules) {
            AssertionResultVO result = doAssert(
                    rule.getType(),
                    rule.getDescription(),
                    rule.getExpectedValue(),
                    rule.getOperator() != null ? rule.getOperator() : "EQUALS",
                    rule.getExpression(),
                    response, null, null);
            results.add(result);
            log.debug("[Direct] 断言执行: type={}, passed={}", result.getType(), result.getPassed());
        }

        logSummary(results);
        return results;
    }

    /**
     * 任务引擎断言执行
     *
     * @param assertionsJson 断言规则 JSON 数组
     * @param response       响应对象
     * @param headers        响应头（可选，优先使用）
     * @param duration       响应时间（可选，优先使用）
     * @return 断言结果列表
     */
    @SuppressWarnings("unchecked")
    public List<AssertionResultVO> executeAssertions(String assertionsJson,
                                                      Object response,
                                                      Map<String, String> headers,
                                                      Long duration) {
        List<AssertionResultVO> results = new ArrayList<>();
        if (assertionsJson == null || assertionsJson.isEmpty()) {
            log.debug("没有配置断言规则");
            return results;
        }

        try {
            List<Map<String, Object>> assertions = objectMapper.readValue(assertionsJson, List.class);
            log.info("开始执行断言: total={}", assertions.size());

            for (Map<String, Object> assertion : assertions) {
                String type = (String) assertion.get("type");
                Object expectedRaw = assertion.get("expectedValue");
                if (expectedRaw == null) {
                    expectedRaw = assertion.get("expected");
                }
                String expected = String.valueOf(expectedRaw);
                String description = (String) assertion.getOrDefault("description", "");
                String operator = (String) assertion.getOrDefault("operator", "EQUALS");
                String expression = resolveExpression(assertion, type);

                AssertionResultVO result = doAssert(type, description, expected, operator,
                        expression, response, headers, duration);
                results.add(result);
                log.debug("断言执行: type={}, passed={}", result.getType(), result.getPassed());
            }

            logSummary(results);

        } catch (Exception e) {
            log.error("断言执行异常", e);
            results.add(AssertionResultVO.builder()
                    .type("PLATFORM_ERROR")
                    .description("断言执行异常")
                    .expected("success")
                    .actual(e.getMessage())
                    .passed(false)
                    .failReason("断言执行异常: " + e.getMessage())
                    .build());
        }

        return results;
    }

    // ==================== 核心断言处理 ====================

    /**
     * 统一断言执行核心
     *
     * @param type        断言类型（STATUS_CODE / HEADER / JSON_PATH / REGEX / RESPONSE_TIME）
     * @param description 断言描述
     * @param expected    期望值
     * @param operator    比较运算符（EQUALS / CONTAINS / MATCHES / GT / LT / GTE / LTE）
     * @param expression  表达式（JSONPath、正则、headerName 等）
     * @param response    响应对象
     * @param headers     显式响应头（优先，可 null）
     * @param duration    显式耗时（优先，可 null）
     * @return 断言结果
     */
    private AssertionResultVO doAssert(String type, String description, String expected,
                                        String operator, String expression,
                                        Object response,
                                        Map<String, String> headers, Long duration) {
        AssertionResultVO result = AssertionResultVO.builder()
                .type(type)
                .description(description != null ? description : "")
                .expected(expected)
                .build();

        try {
            boolean passed = false;
            String actual = "";

            switch (type.toUpperCase()) {
                case "STATUS_CODE":
                    actual = String.valueOf(extractStatusCode(response));
                    passed = compareValues(actual, expected, operator);
                    break;

                case "HEADER":
                    Map<String, String> effectiveHeaders = headers != null ? headers : extractResponseHeaders(response);
                    actual = effectiveHeaders != null ? effectiveHeaders.getOrDefault(expression, "") : "";
                    passed = compareValues(actual, expected, operator);
                    break;

                case "JSON_PATH":
                    Object responseBody = extractResponseBody(response);
                    String bodyJson;
                    if (responseBody instanceof String) {
                        bodyJson = (String) responseBody;
                    } else if (responseBody != null) {
                        bodyJson = objectMapper.writeValueAsString(responseBody);
                    } else {
                        bodyJson = "null";
                    }
                    Object jsonPathResult = JsonPath.read(bodyJson, expression);
                    actual = (jsonPathResult != null) ? jsonPathResult.toString() : "null";
                    passed = compareValues(actual, expected, operator);
                    break;

                case "REGEX":
                    Object bodyForRegex = extractResponseBody(response);
                    actual = String.valueOf(bodyForRegex);
                    passed = Pattern.compile(expression).matcher(actual).find();
                    break;

                case "RESPONSE_TIME":
                    long effectiveDuration = duration != null ? duration : extractDuration(response);
                    actual = String.valueOf(effectiveDuration);
                    passed = compareValues(actual, expected, operator);
                    break;

                default:
                    throw new IllegalArgumentException("不支持的断言类型: " + type);
            }

            result.setActual(actual);
            result.setPassed(passed);
            if (!passed) {
                result.setFailReason("期望值: " + expected + ", 实际值: " + actual);
            }

        } catch (Exception e) {
            log.error("断言执行异常: type={}", type, e);
            result.setPassed(false);
            result.setFailReason("断言执行异常: " + e.getMessage());
        }

        return result;
    }

    // ==================== 多态提取方法 ====================

    /**
     * 提取状态码 —— 支持 Map、HttpResponse（反射）
     */
    private Integer extractStatusCode(Object response) {
        if (response instanceof Map) {
            Map<String, Object> responseMap = (Map<String, Object>) response;
            Object statusCode = responseMap.get("statusCode");
            if (statusCode instanceof Number) {
                return ((Number) statusCode).intValue();
            }
        }
        // 反射获取 getStatusCode()（兼容 HttpResponse 等 VO）
        return extractIntField(response);
    }

    /**
     * 提取响应体 —— 支持 Map、HttpResponse/DubboResponse（反射）
     */
    private Object extractResponseBody(Object response) {
        if (response instanceof Map) {
            Map<String, Object> responseMap = (Map<String, Object>) response;
            return responseMap.get("body");
        }
        // 反射获取 getBody()（兼容 HttpResponse）或 getResult()（兼容 DubboResponse）
        try {
            java.lang.reflect.Method getBody = response.getClass().getMethod("getBody");
            return getBody.invoke(response);
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method getResult = response.getClass().getMethod("getResult");
            return getResult.invoke(response);
        } catch (Exception ignored) {
        }
        return response;
    }

    /**
     * 提取响应头 —— 支持 Map、HttpResponse（反射）
     */
    private Map<String, String> extractResponseHeaders(Object response) {
        if (response instanceof Map) {
            Map<String, Object> responseMap = (Map<String, Object>) response;
            Object headers = responseMap.get("headers");
            if (headers instanceof Map) {
                return (Map<String, String>) headers;
            }
        }
        try {
            java.lang.reflect.Method getHeaders = response.getClass().getMethod("getHeaders");
            Object headers = getHeaders.invoke(response);
            if (headers instanceof Map) {
                return (Map<String, String>) headers;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 提取耗时 —— 反射获取 getDuration()
     */
    private Long extractDuration(Object response) {
        return extractLongField(response);
    }

    // ==================== 工具方法 ====================

    /**
     * 从 JSON 断言规则 Map 中解析表达式
     * 不同断言类型的表达式字段名不同：jsonPath / pattern / headerName / 直接 value
     */
    private String resolveExpression(Map<String, Object> assertion, String type) {
        if (type == null) return "";
        return switch (type.toUpperCase()) {
            case "JSON_PATH" -> {
                String path = (String) assertion.get("jsonPath");
                if (path == null) path = (String) assertion.get("expression");
                yield path != null ? path : "";
            }
            case "REGEX" -> {
                String pattern = (String) assertion.get("pattern");
                if (pattern == null) pattern = (String) assertion.get("expression");
                yield pattern != null ? pattern : "";
            }
            case "HEADER" -> {
                String header = (String) assertion.get("headerName");
                if (header == null) header = (String) assertion.get("expression");
                yield header != null ? header : "";
            }
            default -> {
                Object val = assertion.get("expectedValue");
                if (val == null) val = assertion.get("expected");
                yield String.valueOf(val != null ? val : "");
            }
        };
    }

    /**
     * 值比较（支持多种 operator）
     */
    private boolean compareValues(String actual, String expected, String operator) {
        if (actual == null) return false;
        return switch (operator.toUpperCase()) {
            case "EQUALS" -> actual.equals(expected);
            case "CONTAINS" -> actual.contains(expected);
            case "MATCHES" -> Pattern.compile(expected).matcher(actual).find();
            case "GT" -> toDouble(actual) > toDouble(expected);
            case "LT" -> toDouble(actual) < toDouble(expected);
            case "GTE" -> toDouble(actual) >= toDouble(expected);
            case "LTE" -> toDouble(actual) <= toDouble(expected);
            default -> actual.equals(expected);
        };
    }

    private double toDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void logSummary(List<AssertionResultVO> results) {
        long passedCount = results.stream().filter(AssertionResultVO::getPassed).count();
        log.info("断言执行完成: total={}, passed={}, failed={}",
                results.size(), passedCount, results.size() - passedCount);
    }

    /**
     * 反射获取 int 字段值
     */
    private Integer extractIntField(Object obj) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod("getStatusCode");
            Object value = method.invoke(obj);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * 反射获取 long 字段值
     */
    private Long extractLongField(Object obj) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod("getDuration");
            Object value = method.invoke(obj);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }
}
