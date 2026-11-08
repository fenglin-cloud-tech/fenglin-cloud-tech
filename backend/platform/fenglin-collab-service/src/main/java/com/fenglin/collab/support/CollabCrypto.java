package com.fenglin.collab.support;

import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.crypto.PiiHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 云协敏感内容处理：正文 AES-256-GCM 密文 + 租户盐 SHA-256 哈希，与 CRM/PII 方案保持一致。
 *
 * <p>缺口兜底：文档要求"租户盐独立生成并加密存储"，平台尚未提供租户盐下发接口，
 * 此处以服务密钥 + 租户 ID 确定性派生 32 字节租户盐；后续替换为 KMS 接口即可。</p>
 */
@Component
public class CollabCrypto {

    private final AesGcmCipher cipher;
    private final PiiHasher hasher;
    private final String saltSecret;

    public CollabCrypto(AesGcmCipher cipher, PiiHasher hasher,
                        @Value("${fenglin.collab.pii-salt-secret}") String saltSecret) {
        this.cipher = cipher;
        this.hasher = hasher;
        this.saltSecret = saltSecret;
    }

    public String tenantSalt(long tenantId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(saltSecret.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(Long.toString(tenantId).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }

    /** AAD 绑定租户、表与字段，防止密文跨租户/跨字段搬运。 */
    public String encrypt(long tenantId, String table, String field, String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        return cipher.encrypt(plain, tenantId + ":" + table + ":" + field);
    }

    public String decrypt(long tenantId, String table, String field, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        return cipher.decrypt(encoded, tenantId + ":" + table + ":" + field);
    }

    public String hash(long tenantId, String value) {
        if (value == null) {
            return null;
        }
        return hasher.hashHex(value, tenantSalt(tenantId));
    }

    /** 列表展示用脱敏摘要：文本截断，非文本按类型占位。 */
    public String summary(String msgType, String content) {
        if (content == null || content.isBlank()) {
            return switch (msgType == null ? "TEXT" : msgType) {
                case "IMAGE" -> "[图片]";
                case "FILE" -> "[文件]";
                case "AUDIO" -> "[语音]";
                case "VIDEO" -> "[视频]";
                case "CARD" -> "[卡片]";
                default -> null;
            };
        }
        String trimmed = content.strip();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "…";
    }
}
