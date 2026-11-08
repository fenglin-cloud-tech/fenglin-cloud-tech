package com.fenglin.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * 审计哈希链的规范化与摘要计算：写入与校验必须走同一套规则。
 * content_hash = SHA-256(审计字段规范化拼接)，chain_hash = SHA-256(prev_hash + content_hash)。
 */
public final class AuditChainHasher {

    public static final String ZERO_HASH = "0".repeat(64);

    private static final String FIELD_SEPARATOR = "\u001F";
    private static final String NULL_VALUE = "\u0000";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuditChainHasher() {
    }

    /**
     * jsonb 在库内会重排键并重写空白，因此哈希前统一按“键排序 + 紧凑输出”规范化。
     */
    public static String canonicalDetail(JsonNode detail) {
        if (detail == null || detail.isNull()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(sortKeys(detail));
        } catch (Exception e) {
            throw new IllegalStateException("审计详情 JSON 规范化失败", e);
        }
    }

    public static String canonicalDetail(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return null;
        }
        try {
            return canonicalDetail(MAPPER.readTree(detailJson));
        } catch (Exception e) {
            throw new IllegalStateException("审计详情 JSON 解析失败", e);
        }
    }

    public static String content(long id, long tenantId, String logNo, String eventType, String module,
                                 String action, Long operatorId, String operatorName, String targetType,
                                 String targetId, String requestId, String traceId, String clientType,
                                 String result, String riskLevel, String canonicalDetail, Instant occurredAt) {
        List<String> values = new ArrayList<>(List.of(
                value(id), value(tenantId), value(logNo), value(eventType), value(module), value(action),
                value(operatorId), value(operatorName), value(targetType), value(targetId),
                value(requestId), value(traceId), value(clientType), value(result), value(riskLevel),
                value(canonicalDetail), value(normalizeInstant(occurredAt))));
        return String.join(FIELD_SEPARATOR, values);
    }

    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static String chainHash(String prevHash, String contentHash) {
        return sha256(prevHash + contentHash);
    }

    /**
     * 时间戳统一截断到微秒，与 PostgreSQL timestamptz 精度一致，保证落库前后摘要可复算。
     */
    public static Instant normalizeInstant(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }

    private static String value(Object value) {
        return value == null ? NULL_VALUE : String.valueOf(value);
    }

    private static JsonNode sortKeys(JsonNode node) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            ObjectNode sorted = MAPPER.createObjectNode();
            for (String name : names) {
                sorted.set(name, sortKeys(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = MAPPER.createArrayNode();
            for (JsonNode child : node) {
                sorted.add(sortKeys(child));
            }
            return sorted;
        }
        return node;
    }
}
