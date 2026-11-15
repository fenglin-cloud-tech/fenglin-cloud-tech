package com.fenglin.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 路由结果加工：JSONB 文本转 JsonNode，保证响应体不出现二次转义字符串。
 */
public final class ServiceMaps {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ServiceMaps() {
    }

    public static Map<String, Object> parse(Map<String, Object> row, String... jsonKeys) {
        if (row == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>(row);
        for (String key : jsonKeys) {
            Object value = result.get(key);
            if (value instanceof String text && !text.isBlank()) {
                try {
                    JsonNode node = MAPPER.readTree(text);
                    result.put(key, node);
                } catch (Exception ignored) {
                    // 非法 JSON 保留原始文本，交由调用方判断
                }
            }
        }
        return result;
    }

    public static List<Map<String, Object>> parseAll(List<Map<String, Object>> rows, String... jsonKeys) {
        return rows.stream().map(row -> parse(row, jsonKeys)).toList();
    }

    public static JsonNode node(Object value) {
        if (value == null) {
            return MAPPER.createObjectNode();
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        if (value instanceof String text) {
            try {
                return MAPPER.readTree(text);
            } catch (Exception ex) {
                return MAPPER.createObjectNode();
            }
        }
        return MAPPER.valueToTree(value);
    }

    /** 读取 JSON 文本为 Map/List/标量；非法 JSON 返回 null。 */
    public static Object read(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(text, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }

    public static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    public static Map<String, Object> of(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
