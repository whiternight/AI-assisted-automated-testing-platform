package com.example.testplatform.workflow.engine.util;

import com.example.testplatform.common.exception.BusinessException;
import com.example.testplatform.common.util.JsonPathUtil;
import com.example.testplatform.enums.BusinessExceptionEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 输入参数解析器（通用版本）
 * <p>
 * 解析 inputParams JSON 配置，将静态值和动态 JSONPath 引用合并为请求参数 Map。
 * 与 Flowable DelegateExecution 解耦，接受通用 Map 上下文。
 * </p>
 * <pre>
 * inputParams JSON 格式：
 * {
 *   "field1": {"type": "static", "value": "123"},
 *   "field2": {"type": "dynamic", "jsonPath": "$.node_prev.result.userId"}
 * }
 * </pre>
 *
 * @author TestPlatform
 */
@Slf4j
@Component("utilInputParamResolver")
public class InputParamResolver {

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 解析 inputParams JSON 为请求参数 Map
     *
     * @param inputParamsJson FlowNode.inputParams JSON 字符串
     * @param context         上下文变量 Map（如 execution.getVariables()）
     * @return 解析后的参数 Map
     * @throws BusinessException 当 JSON 格式错误或 JSONPath 解析失败时
     */
    public Map<String, Object> resolve(String inputParamsJson, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();

        if (inputParamsJson == null || inputParamsJson.isBlank()) {
            log.debug("inputParams 为空，跳过参数解析");
            return result;
        }

        Map<String, Map<String, Object>> paramMap;
        try {
            paramMap = objectMapper.readValue(inputParamsJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR,
                    "inputParams JSON 格式错误: " + e.getMessage());
        }

        // 将上下文序列化为 JSON 供 JSONPath 提取
        String contextJson = null;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            log.warn("上下文序列化失败: {}", e.getMessage());
        }

        for (Map.Entry<String, Map<String, Object>> entry : paramMap.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> fieldConfig = entry.getValue();

            String type = (String) fieldConfig.getOrDefault("type", "static");
            if (type == null) {
                type = "static";
            }
            Object value;

            switch (type.toLowerCase()) {
                case "static":
                    value = fieldConfig.get("value");
                    break;

                case "dynamic":
                    String jsonPath = (String) fieldConfig.get("jsonPath");
                    value = resolveFromContext(jsonPath, contextJson, context, fieldName);
                    break;

                default:
                    value = fieldConfig.get("value");
                    log.debug("未知的 type: {}, 使用 value", type);
                    break;
            }

            result.put(fieldName, value);
            log.debug("参数解析: field={}, type={}, value={}", fieldName, type, value);
        }

        return result;
    }

    /**
     * 从上下文中通过 JSONPath 提取值
     *
     * @throws BusinessException JSONPath 解析失败时
     */
    private Object resolveFromContext(String jsonPath, String contextJson,
                                       Map<String, Object> context, String fieldName) {
        if (jsonPath == null || jsonPath.isBlank()) {
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR,
                    "JSONPath解析失败: " + fieldName + " 的 jsonPath 为空");
        }

        // 1. 尝试直接 Map 获取（简写路径如 $.userId）
        if (jsonPath.startsWith("$.")) {
            String varName = jsonPath.substring(2);
            if (!varName.contains(".")) {
                Object directValue = context.get(varName);
                if (directValue != null) {
                    return directValue;
                }
            }
        }

        // 2. JSONPath 从序列化 JSON 中提取
        if (contextJson != null) {
            Object extracted = JsonPathUtil.extract(contextJson, jsonPath);
            if (extracted != null) {
                return extracted;
            }
        }

        // 3. 解析失败，抛出异常
        throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR,
                "JSONPath解析失败: " + jsonPath);
    }
}
