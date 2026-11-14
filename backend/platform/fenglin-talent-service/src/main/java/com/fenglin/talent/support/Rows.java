package com.fenglin.talent.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Map 结果读取工具：SQL 统一以 camelCase 别名 + epoch 毫秒输出。
 *
 * <p>数值读取同时接受 JSON 数字与数字字符串：本服务会把含雪花 ID 的 Map 交给 ObjectMapper
 * 落库，而全局 Long 序列化会把超出 JS 安全整数的 ID 写成字符串，读回后仍是字符串形态。
 * 这里统一转成同一 long 值，避免调用点各写一份兼容逻辑。</p>
 */
public final class Rows {

    private Rows() {
    }

    public static Long lng(Map<String, Object> row, String key) {
        return toLong(row.get(key));
    }

    /** 读取 JSON 数字或数字字符串为 Long；非法字符串直接抛错，不静默返回 null。 */
    public static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.parseLong(text);
    }

    /** 读取 JSON 数字或数字字符串为 Integer；非法字符串直接抛错，不静默返回 null。 */
    public static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Integer.parseInt(text);
    }

    public static long lngVal(Map<String, Object> row, String key) {
        Long value = lng(row, key);
        return value == null ? 0L : value;
    }

    public static Integer intg(Map<String, Object> row, String key) {
        return toInt(row.get(key));
    }

    public static int intVal(Map<String, Object> row, String key) {
        Integer value = intg(row, key);
        return value == null ? 0 : value;
    }

    public static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static BigDecimal dec(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }

    public static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
    }

    public static Instant instant(Map<String, Object> row, String key) {
        Long millis = lng(row, key);
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }

    public static LocalDate date(Map<String, Object> row, String key) {
        String value = str(row, key);
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
}
