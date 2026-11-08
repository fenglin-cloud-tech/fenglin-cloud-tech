package com.fenglin.talent.support;

import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.crypto.PiiHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 云才 PII 处理：手机号/身份证号 AES-256-GCM 密文 + 租户盐 SHA-256 哈希，与 CRM/云协方案保持一致。
 *
 * <p>缺口兜底：文档要求"租户盐独立生成并加密存储"（talent_tenant_key），平台尚未提供租户密钥下发接口，
 * 此处以服务密钥 + 租户 ID 确定性派生 32 字节租户盐；后续替换为 KMS 接口即可。</p>
 */
@Component
public class TalentCrypto {

    private final AesGcmCipher cipher;
    private final PiiHasher hasher;
    private final String saltSecret;

    public TalentCrypto(AesGcmCipher cipher, PiiHasher hasher,
                        @Value("${fenglin.talent.pii-salt-secret}") String saltSecret) {
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
        if (value == null || value.isBlank()) {
            return null;
        }
        return hasher.hashHex(value, tenantSalt(tenantId));
    }

    /** 手机号脱敏：保留前 3 位与后 4 位。 */
    public String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.strip();
        if (trimmed.length() <= 7) {
            return "***";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    /** 身份证脱敏：保留前 4 位与后 4 位。 */
    public String maskIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return null;
        }
        String trimmed = idCard.strip();
        if (trimmed.length() <= 8) {
            return "***";
        }
        return trimmed.substring(0, 4) + "**********" + trimmed.substring(trimmed.length() - 4);
    }
}
