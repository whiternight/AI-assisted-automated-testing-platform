package com.example.testplatform.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 快照 JSON 解析工具
 * <p>
 * 解决快照中 body 字段存储为转义 JSON 字符串的问题。
 * 将 {"body": "{\"key\":\"value\"}"} 转换为 {"body": {"key": "value"}}
 * <p>
 * 适用于：requestSnapshot、responseSnapshot、outputSnapshot 等所有 JSON 快照字段。
 *
 * @author TestPlatform
 */
@Slf4j
public final class SnapshotUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SnapshotUtil() {}

    /**
     * 解析快照 JSON 字符串，将其中的 body 字段从转义字符串转换为结构化 JSON 对象
     *
     * @param snapshotJson 原始快照 JSON 字符串
     * @return 解析后的快照 JSON 字符串（body 字段为嵌套 JSON 对象）
     */
    public static String parseBodyFields(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isEmpty()) {
            return snapshotJson;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(snapshotJson);

            if (root instanceof ObjectNode objectNode) {
                // 解析 body 字段
                parseFieldIfJsonString(objectNode, "body");
                // 解析 result 字段（Dubbo 响应）
                parseFieldIfJsonString(objectNode, "result");
                return OBJECT_MAPPER.writeValueAsString(objectNode);
            }

            return snapshotJson;
        } catch (Exception e) {
            log.debug("快照解析跳过（非 JSON 格式）: {}", e.getMessage());
            return snapshotJson;
        }
    }

    /**
     * 如果指定字段是 JSON 字符串，则解析为 JSON 对象替换原值
     */
    private static void parseFieldIfJsonString(ObjectNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field != null && field.isTextual()) {
            String text = field.asText().trim();
            if (text.isEmpty()) {
                return;
            }
            // 仅当字符串以 { 或 [ 开头时才尝试解析为 JSON
            if (text.charAt(0) == '{' || text.charAt(0) == '[') {
                try {
                    JsonNode parsed = OBJECT_MAPPER.readTree(text);
                    node.set(fieldName, parsed);
                } catch (Exception ignored) {
                    // 非合法 JSON，保留原字符串
                }
            }
        }
    }
}
