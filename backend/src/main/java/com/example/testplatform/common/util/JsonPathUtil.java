package com.example.testplatform.common.util;

import com.jayway.jsonpath.JsonPath;

public class JsonPathUtil {

    /**
     * 使用 JSONPath 提取字段值
     */
    public static Object extract(String json, String path) {
        if (json == null || path == null) {
            return null;
        }
        try {
            return JsonPath.parse(json).read(path);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 使用 JSONPath 提取字段值（带默认值）
     */
    public static Object extract(String json, String path, Object defaultValue) {
        Object result = extract(json, path);
        return result != null ? result : defaultValue;
    }
}
