package com.fenglin.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * PII 检索哈希：SHA-256(tenantSalt || normalizedValue)。盐按租户独立生成并加密存储。
 */
public final class PiiHasher {

    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s\\-()]");

    public String hashHex(String normalizedValue, String saltBase64) {
        if (normalizedValue == null || normalizedValue.isEmpty()) {
            return null;
        }
        if (saltBase64 == null || saltBase64.isBlank()) {
            throw new IllegalArgumentException("租户哈希盐不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(saltBase64));
            digest.update((byte) '|');
            digest.update(normalizedValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new CryptoException("当前 JVM 不支持 SHA-256", ex);
        }
    }

    public String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String value = PHONE_SEPARATORS.matcher(phone.trim()).replaceAll("");
        if (value.startsWith("+")) {
            return value;
        }
        if (value.length() == 11 && value.startsWith("1")) {
            return "+86" + value;
        }
        if (value.length() == 13 && value.startsWith("86")) {
            return "+" + value;
        }
        return value;
    }

    public String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
