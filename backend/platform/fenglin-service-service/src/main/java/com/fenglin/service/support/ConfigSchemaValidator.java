package com.fenglin.service.support;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务台配置的字段级弱强类型校验。
 *
 * <p>口径（2026-11-15 定稿）：只校验能从页面语义与存量数据确定的字段；
 * 未知键原样保留，未知类型不拦截，字段缺失不报错，null 不报错。
 * 目的不是全量白名单，而是拦住“该是字符串却填了对象/数组”这类明显错误，同时保证存量配置零误拒。</p>
 */
@Component
public class ConfigSchemaValidator {

    public enum FieldType {
        STRING, NUMBER, BOOLEAN, ARRAY, OBJECT
    }

    private record Field(String name, FieldType type) {
    }

    /** 7 类服务台配置的已知字段骨架；同一语义的 snake/camel 变体都列入，避免误拒。 */
    private static final Map<String, Map<String, FieldType>> SCHEMAS = buildSchemas();

    /** 需要参与校验与 dry-run 的配置类型。 */
    public static final List<String> CONFIG_TYPES = List.of(
            "TICKET_TYPE", "HANDLE_GROUP", "DUTY_ROSTER", "DISPATCH_RULE",
            "ASSET_REMINDER", "RPA_RETRY_POLICY", "SERVICE_REPORT", "MINING_COLLECTOR");

    private static Map<String, Map<String, FieldType>> buildSchemas() {
        Map<String, Map<String, FieldType>> schemas = new LinkedHashMap<>();
        schemas.put("TICKET_TYPE", fields(
                "name", FieldType.STRING, "title", FieldType.STRING, "code", FieldType.STRING,
                "ticket_type", FieldType.STRING, "priority", FieldType.STRING,
                "need_approval", FieldType.BOOLEAN, "needApproval", FieldType.BOOLEAN,
                "approval_required", FieldType.BOOLEAN, "sla_minutes", FieldType.NUMBER,
                "slaMinutes", FieldType.NUMBER, "enabled", FieldType.BOOLEAN));
        schemas.put("HANDLE_GROUP", fields(
                "name", FieldType.STRING, "group_name", FieldType.STRING, "code", FieldType.STRING,
                "member_ids", FieldType.ARRAY, "memberIds", FieldType.ARRAY,
                "skills", FieldType.ARRAY, "enabled", FieldType.BOOLEAN));
        schemas.put("DUTY_ROSTER", fields(
                "name", FieldType.STRING, "group_id", FieldType.STRING, "groupId", FieldType.STRING,
                "start_at", FieldType.STRING, "startAt", FieldType.STRING,
                "end_at", FieldType.STRING, "endAt", FieldType.STRING,
                "members", FieldType.ARRAY, "escalations", FieldType.ARRAY, "enabled", FieldType.BOOLEAN));
        schemas.put("DISPATCH_RULE", fields(
                "name", FieldType.STRING, "ticket_type", FieldType.STRING, "ticketType", FieldType.STRING,
                "priority", FieldType.STRING, "strategy", FieldType.STRING,
                "group_id", FieldType.STRING, "groupId", FieldType.STRING,
                "max_load", FieldType.NUMBER, "maxLoad", FieldType.NUMBER, "enabled", FieldType.BOOLEAN));
        schemas.put("ASSET_REMINDER", fields(
                "days_before", FieldType.NUMBER, "daysBefore", FieldType.NUMBER,
                "channels", FieldType.ARRAY, "channel", FieldType.STRING, "enabled", FieldType.BOOLEAN));
        schemas.put("RPA_RETRY_POLICY", fields(
                "max_retries", FieldType.NUMBER, "maxRetries", FieldType.NUMBER,
                "backoff_seconds", FieldType.NUMBER, "backoffSeconds", FieldType.NUMBER,
                "strategy", FieldType.STRING, "enabled", FieldType.BOOLEAN));
        schemas.put("SERVICE_REPORT", fields(
                "period", FieldType.STRING, "export_format", FieldType.STRING, "exportFormat", FieldType.STRING,
                "metrics", FieldType.ARRAY, "enabled", FieldType.BOOLEAN));
        schemas.put("MINING_COLLECTOR", fields(
                "code", FieldType.STRING, "name", FieldType.STRING, "type", FieldType.STRING,
                "url", FieldType.STRING, "method", FieldType.STRING,
                "headers", FieldType.OBJECT, "body", FieldType.OBJECT,
                "interval_minutes", FieldType.NUMBER, "intervalMinutes", FieldType.NUMBER,
                "enabled", FieldType.BOOLEAN, "list_path", FieldType.STRING, "listPath", FieldType.STRING,
                "field_mapping", FieldType.OBJECT, "fieldMapping", FieldType.OBJECT,
                "last_run_at", FieldType.STRING, "last_status", FieldType.STRING,
                "last_error", FieldType.STRING, "last_inserted", FieldType.NUMBER));
        return schemas;
    }

    private static Map<String, FieldType> fields(Object... kv) {
        Map<String, FieldType> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), (FieldType) kv[i + 1]);
        }
        return map;
    }

    /**
     * 校验配置值；返回人类可读的错误路径列表，空列表表示通过。
     * 未知配置类型直接放行。
     */
    public List<String> validate(String configType, Object body) {
        Map<String, FieldType> schema = SCHEMAS.get(configType);
        if (schema == null || body == null) {
            return List.of();
        }
        List<String> errors = new ArrayList<>();
        if (body instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Map<?, ?> map) {
                    validateMap(schema, map, "$[" + i + "]", errors);
                }
                // 数组元素不是对象时不拦截：未知结构放行，避免误伤存量
            }
        } else if (body instanceof Map<?, ?> map) {
            validateMap(schema, map, "$", errors);
        }
        return errors;
    }

    private void validateMap(Map<String, FieldType> schema, Map<?, ?> map, String path, List<String> errors) {
        map.forEach((rawKey, value) -> {
            if (value == null) {
                return;
            }
            FieldType expected = schema.get(String.valueOf(rawKey));
            if (expected == null) {
                return;
            }
            if (!matches(expected, value)) {
                errors.add(path + "." + rawKey + " 应为 " + describe(expected));
            }
        });
    }

    private boolean matches(FieldType expected, Object value) {
        return switch (expected) {
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number || (value instanceof String text && isNumeric(text));
            case BOOLEAN -> value instanceof Boolean
                    || (value instanceof String text && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)));
            case ARRAY -> value instanceof List<?>;
            case OBJECT -> value instanceof Map<?, ?>;
        };
    }

    private boolean isNumeric(String text) {
        try {
            new java.math.BigDecimal(text.trim());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String describe(FieldType type) {
        return switch (type) {
            case STRING -> "字符串";
            case NUMBER -> "数字";
            case BOOLEAN -> "布尔值";
            case ARRAY -> "数组";
            case OBJECT -> "对象";
        };
    }
}
