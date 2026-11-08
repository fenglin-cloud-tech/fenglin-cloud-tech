package com.fenglin.crm.support;

import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.crypto.PiiHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * CRM PII 处理：手机号 AES-256-GCM 加密存储 + 租户盐 SHA-256 检索哈希。
 *
 * <p>文档缺口兜底（01_丰麟云销_03 4.1 要求"盐按租户独立生成并加密存储"，P0–P3 未提供租户盐下发接口）：
 * 此处以服务密钥 + 租户 ID 确定性派生 32 字节租户盐。同一租户内可稳定查重，跨租户哈希不可比对，
 * 且盐本身不落库。后续若平台提供 KMS 租户盐接口，仅需替换 {@link #tenantSalt(long)}。</p>
 */
@Component
public class CrmPiiService {

    private final AesGcmCipher cipher;
    private final PiiHasher hasher;
    private final String saltSecret;

    public CrmPiiService(AesGcmCipher cipher,
                         PiiHasher hasher,
                         @Value("${fenglin.crm.pii-salt-secret}") String saltSecret) {
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

    public String mobileHash(long tenantId, String rawMobile) {
        String normalized = hasher.normalizePhone(rawMobile);
        return hasher.hashHex(normalized, tenantSalt(tenantId));
    }

    /** AAD 绑定租户与字段，防止密文跨租户/跨字段搬运。 */
    public String encryptMobile(long tenantId, String table, String rawMobile) {
        if (rawMobile == null || rawMobile.isBlank()) {
            return null;
        }
        String normalized = hasher.normalizePhone(rawMobile);
        return cipher.encrypt(normalized, tenantId + ":" + table + ":mobile");
    }

    public String decryptMobile(long tenantId, String table, String encoded) {
        return cipher.decrypt(encoded, tenantId + ":" + table + ":mobile");
    }

    /** 仅保留尾号用于展示与客服核对。 */
    public String mobileTail(String rawMobile) {
        String normalized = hasher.normalizePhone(rawMobile);
        if (normalized == null || normalized.length() < 4) {
            return null;
        }
        return normalized.substring(normalized.length() - 4);
    }

    /** 列表/详情脱敏展示，禁止返回明文。 */
    public String maskByTail(String tail) {
        return tail == null || tail.isBlank() ? null : "***" + tail;
    }

    public boolean sameMobile(long tenantId, String rawMobile, String storedHash) {
        if (rawMobile == null || rawMobile.isBlank() || storedHash == null) {
            return false;
        }
        return storedHash.equals(mobileHash(tenantId, rawMobile));
    }
}
