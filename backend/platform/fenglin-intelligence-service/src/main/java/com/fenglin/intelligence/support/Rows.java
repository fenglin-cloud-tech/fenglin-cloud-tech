package com.fenglin.intelligence.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * MyBatis Map 结果读取工具：SQL 统一 snake_case 别名输出。
 */
public final class Rows {

    private Rows() {
    }

    public static Long lng(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).longValue();
    }

    public static long lngVal(Map<String, Object> row, String key) {
        Long value = lng(row, key);
        return value == null ? 0L : value;
    }

    public static Integer intg(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).intValue();
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
        Object value = row.get(key);
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return null;
    }

    public static LocalDate date(Map<String, Object> row, String key) {
        String value = str(row, key);
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
}
