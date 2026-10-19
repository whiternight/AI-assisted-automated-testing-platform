package com.example.testplatform.workflow.engine.util;

import com.example.testplatform.apicase.domain.vo.AssertionResultVO;
import com.example.testplatform.execution.engine.assertion.AssertionEngine;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 断言校验器（便捷包装）
 * <p>
 * 对 AssertionEngine 的薄包装，提供更简洁的 API 供 delegate 和监听器使用。
 * 支持检查是否全部通过，并提供详细结果查询。
 * </p>
 * <pre>
 * assertions JSON 格式：
 * [
 *   {"type": "STATUS_CODE", "expected": "200"},
 *   {"type": "JSON_PATH", "path": "$.code", "expected": "0", "operator": "EQUALS"},
 *   {"type": "HEADER", "header": "Content-Type", "expected": "application/json", "operator": "CONTAINS"},
 *   {"type": "RESPONSE_TIME", "expected": "3000", "operator": "LT"},
 *   {"type": "REGEX", "expression": "$.message", "expected": "success", "operator": "MATCHES"}
 * ]
 * </pre>
 *
 * @author TestPlatform
 */
@Slf4j
@Component
public class AssertionChecker {

    @Resource
    private AssertionEngine assertionEngine;

    /** 最近一次检查的详细结果（注意：并发场景下可能被其他线程覆盖） */
    @Getter
    private volatile List<AssertionResultVO> lastResults = Collections.emptyList();

    /**
     * 执行断言校验
     *
     * @param assertionsJson 断言规则 JSON 数组
     * @param response       响应对象
     * @param headers        响应头（可选）
     * @param duration       响应时间毫秒（可选）
     * @return true=全部通过，false=存在失败
     */
    public boolean check(String assertionsJson, Object response,
                          Map<String, String> headers, Long duration) {
        if (assertionsJson == null || assertionsJson.isBlank()) {
            lastResults = Collections.emptyList();
            return true;
        }

        List<AssertionResultVO> results = assertionEngine.executeAssertions(assertionsJson, response, headers, duration);
        this.lastResults = results;

        boolean allPassed = lastResults.stream()
                .allMatch(r -> Boolean.TRUE.equals(r.getPassed()));

        if (!allPassed) {
            long failCount = lastResults.stream()
                    .filter(r -> !Boolean.TRUE.equals(r.getPassed()))
                    .count();
            log.warn("断言校验未通过: total={}, failed={}", lastResults.size(), failCount);
        }

        return allPassed;
    }

    /**
     * 获取所有失败的断言结果
     */
    public List<AssertionResultVO> getFailedResults() {
        return lastResults.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getPassed()))
                .toList();
    }

    /**
     * 获取失败摘要（用于错误消息）
     */
    public String getFailureSummary() {
        return getFailedResults().stream()
                .map(r -> {
                    String desc = r.getDescription() != null ? r.getDescription() : r.getType();
                    String reason = r.getFailReason() != null ? r.getFailReason() : "不匹配";
                    return desc + ": " + reason;
                })
                .reduce((a, b) -> a + "; " + b)
                .orElse("断言校验失败");
    }
}
