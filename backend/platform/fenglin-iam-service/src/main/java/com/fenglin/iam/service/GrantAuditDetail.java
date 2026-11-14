package com.fenglin.iam.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PLAT-25 审计事实：角色授权事件只记录变更前后的权限条数与权限码哈希，
 * 不落明文权限码列表，避免审计库成为权限全集的第二份副本。
 */
final class GrantAuditDetail {

    private GrantAuditDetail() {
    }

    static Map<String, Object> of(int beforeCount, List<String> afterCodes) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before_count", beforeCount);
        detail.put("after_count", afterCodes.size());
        detail.put("after_codes_hash", codesHash(afterCodes));
        return detail;
    }

    /** 对去重、排序后的权限码按换行拼接后取 SHA-256，保证与入参顺序无关且可复算。 */
    static String codesHash(List<String> codes) {
        String canonical = codes.stream().distinct().sorted().collect(Collectors.joining("\n"));
        return "sha256:" + hex(sha256(canonical));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
