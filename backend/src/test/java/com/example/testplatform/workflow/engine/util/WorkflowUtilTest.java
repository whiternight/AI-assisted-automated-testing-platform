package com.example.testplatform.workflow.engine.util;

import com.example.testplatform.apicase.domain.vo.AssertionResultVO;
import com.example.testplatform.common.exception.BusinessException;
import com.example.testplatform.execution.engine.assertion.AssertionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 工作流工具类单元测试
 *
 * @author TestPlatform
 */
@ExtendWith(MockitoExtension.class)
class WorkflowUtilTest {

    // ==================== InputParamResolver 测试 ====================

    @Nested
    @DisplayName("InputParamResolver 测试")
    class InputParamResolverTest {

        @InjectMocks
        private InputParamResolver inputParamResolver;

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        private Map<String, Object> context;

        @BeforeEach
        void setUp() {
            context = new HashMap<>();
            context.put("userId", "12345");
            context.put("orderInfo", Map.of("orderId", "ORD001", "amount", 99.9));
        }

        @Test
        @DisplayName("空 inputParams 返回空 Map")
        void resolve_nullInputParams_returnsEmpty() {
            assertThat(inputParamResolver.resolve(null, context)).isEmpty();
            assertThat(inputParamResolver.resolve("", context)).isEmpty();
            assertThat(inputParamResolver.resolve("  ", context)).isEmpty();
        }

        @Test
        @DisplayName("static 类型直接取 value")
        void resolve_staticType_returnsValue() {
            String json = """
                {
                    "apiKey": {"type": "static", "value": "sk-abc123"},
                    "timeout": {"type": "static", "value": 5000}
                }
                """;

            Map<String, Object> result = inputParamResolver.resolve(json, context);

            assertThat(result).containsEntry("apiKey", "sk-abc123");
            assertThat(result).containsEntry("timeout", 5000);
        }

        @Test
        @DisplayName("dynamic 类型从 context 提取")
        void resolve_dynamicType_extractsFromContext() {
            String json = """
                {
                    "user": {"type": "dynamic", "jsonPath": "$.userId"},
                    "order": {"type": "dynamic", "jsonPath": "$.orderInfo.orderId"}
                }
                """;

            Map<String, Object> result = inputParamResolver.resolve(json, context);

            assertThat(result).containsEntry("user", "12345");
            assertThat(result).containsEntry("order", "ORD001");
        }

        @Test
        @DisplayName("dynamic 类型 JSONPath 解析失败抛异常")
        void resolve_dynamicType_jsonPathFails_throwsException() {
            String json = """
                {
                    "missing": {"type": "dynamic", "jsonPath": "$.nonexistent.field"}
                }
                """;

            assertThatThrownBy(() -> inputParamResolver.resolve(json, context))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("JSONPath解析失败");
        }

        @Test
        @DisplayName("jsonPath 为空时抛异常")
        void resolve_dynamicType_emptyJsonPath_throwsException() {
            String json = """
                {
                    "field": {"type": "dynamic", "jsonPath": ""}
                }
                """;

            assertThatThrownBy(() -> inputParamResolver.resolve(json, context))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("JSONPath解析失败");
        }

        @Test
        @DisplayName("非法 JSON 格式抛异常")
        void resolve_invalidJson_throwsException() {
            String json = "{invalid json}";

            assertThatThrownBy(() -> inputParamResolver.resolve(json, context))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("格式错误");
        }
    }

    // ==================== OutputParamExtractor 测试 ====================

    @Nested
    @DisplayName("OutputParamExtractor 测试")
    class OutputParamExtractorTest {

        @InjectMocks
        private OutputParamExtractor extractor;

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("空 outputParams 返回空 Map")
        void extract_nullOutputParams_returnsEmpty() {
            assertThat(extractor.extract("some response", null)).isEmpty();
            assertThat(extractor.extract("some response", "")).isEmpty();
        }

        @Test
        @DisplayName("正常提取字段")
        void extract_validConfig_extractsFields() {
            Map<String, Object> response = Map.of(
                    "data", Map.of("userId", "U001", "orderNo", "ORD002")
            );
            String config = """
                {
                    "userId": "$.data.userId",
                    "orderNo": "$.data.orderNo"
                }
                """;

            Map<String, Object> result = extractor.extract(response, config);

            assertThat(result).containsEntry("userId", "U001");
            assertThat(result).containsEntry("orderNo", "ORD002");
        }

        @Test
        @DisplayName("提取失败的字段设为 null")
        void extract_missingField_setsNull() {
            Map<String, Object> response = Map.of("data", Map.of("userId", "U001"));
            String config = """
                {
                    "userId": "$.data.userId",
                    "missing": "$.data.nonexistent"
                }
                """;

            Map<String, Object> result = extractor.extract(response, config);

            assertThat(result).containsEntry("userId", "U001");
            assertThat(result).containsKey("missing");
            assertThat(result.get("missing")).isNull();
        }

        @Test
        @DisplayName("非法 JSON 配置返回空 Map")
        void extract_invalidConfig_returnsEmpty() {
            Map<String, Object> result = extractor.extract("response", "{bad json}");
            assertThat(result).isEmpty();
        }
    }

    // ==================== AssertionChecker 测试 ====================

    @Nested
    @DisplayName("AssertionChecker 测试")
    class AssertionCheckerTest {

        @InjectMocks
        private AssertionChecker assertionChecker;

        @Mock
        private AssertionEngine assertionEngine;

        @Test
        @DisplayName("空断言规则返回 true")
        void check_nullAssertions_returnsTrue() {
            assertThat(assertionChecker.check(null, "response", null, null)).isTrue();
            assertThat(assertionChecker.check("", "response", null, null)).isTrue();
            assertThat(assertionChecker.check("  ", "response", null, null)).isTrue();
        }

        @Test
        @DisplayName("所有断言通过返回 true")
        void check_allPassed_returnsTrue() {
            List<AssertionResultVO> results = List.of(
                    AssertionResultVO.builder().type("STATUS_CODE").passed(true).build(),
                    AssertionResultVO.builder().type("JSON_PATH").passed(true).build()
            );
            when(assertionEngine.executeAssertions(anyString(), any(), any(), any()))
                    .thenReturn(results);

            boolean result = assertionChecker.check("[{}]", "response", null, null);

            assertThat(result).isTrue();
            assertThat(assertionChecker.getFailedResults()).isEmpty();
        }

        @Test
        @DisplayName("存在失败断言返回 false")
        void check_someFailed_returnsFalse() {
            List<AssertionResultVO> results = List.of(
                    AssertionResultVO.builder().type("STATUS_CODE").passed(true).build(),
                    AssertionResultVO.builder()
                            .type("JSON_PATH")
                            .passed(false)
                            .description("code字段检查")
                            .failReason("期望 0 实际 1")
                            .build()
            );
            when(assertionEngine.executeAssertions(anyString(), any(), any(), any()))
                    .thenReturn(results);

            boolean result = assertionChecker.check("[{}]", "response", null, null);

            assertThat(result).isFalse();
            assertThat(assertionChecker.getFailedResults()).hasSize(1);
            assertThat(assertionChecker.getFailureSummary()).contains("code字段检查");
        }
    }
}
