package com.fenglin.mdm.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Map 行取值工具（MyBatis 注解查询统一返回 Map）。
 */
public final class Rows {

    private Rows() {
    }

    public static String str(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : String.valueOf(value);
    }

    public static Long lng(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    public static long lngVal(Map<String, Object> row, String key) {
        Long value = lng(row, key);
        return value == null ? 0L : value;
    }

    public static Integer intVal(Map<String, Object> row, String key) {
        Long value = lng(row, key);
        return value == null ? 0 : value.intValue();
    }

    public static BigDecimal dec(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    public static Instant instant(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(String.valueOf(value));
    }

    private static Object value(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(toSnake(key));
    }

    private static String toSnake(String key) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c)) {
                builder.append('_').append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
