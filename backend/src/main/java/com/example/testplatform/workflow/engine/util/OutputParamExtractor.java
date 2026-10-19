package com.example.testplatform.workflow.engine.util;

import com.example.testplatform.common.util.JsonPathUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 输出参数提取器
 * <p>
 * 根据 outputParams 配置，从原服务响应中用 JSONPath 提取字段。
 * 提取失败的字段设为 null 并记录 warn 日志。
 * </p>
 * <pre>
 * outputParams JSON 格式：
 * {
 *   "userId": "$.data.userId",
 *   "orderNo": "$.data.orderNo"
 * }
 * </pre>
 *
 * @author TestPlatform
 */
@Slf4j
@Component
public class OutputParamExtractor {

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 从响应对象中提取输出参数
     *
     * @param response        原服务返回的原始响应对象
     * @param outputParamsJson outputParams JSON 配置
     * @return 提取后的字段 Map
     */
    public Map<String, Object> extract(Object response, String outputParamsJson) {
        Map<String, Object> result = new HashMap<>();

        if (outputParamsJson == null || outputParamsJson.isBlank()) {
            log.debug("outputParams 为空，跳过输出提取");
            return result;
        }

        if (response == null) {
            log.warn("原服务响应为 null，跳过输出提取");
            return result;
        }

        // 序列化响应为 JSON 供 JSONPath 提取
        String responseJson = null;
        try {
            responseJson = objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.warn("响应对象序列化失败: {}", e.getMessage());
            return result;
        }

        Map<String, String> fieldMappings;
        try {
            fieldMappings = objectMapper.readValue(outputParamsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("outputParams JSON 解析失败: {}", e.getMessage());
            return result;
        }

        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            String fieldName = entry.getKey();
            String jsonPath = entry.getValue();

            Object value = JsonPathUtil.extract(responseJson, jsonPath);
            if (value == null) {
                log.warn("JSONPath 提取失败: field={}, jsonPath={}", fieldName, jsonPath);
            }
            result.put(fieldName, value);
            log.debug("输出提取: field={}, jsonPath={}, value={}", fieldName, jsonPath, value);
        }

        return result;
    }
}
