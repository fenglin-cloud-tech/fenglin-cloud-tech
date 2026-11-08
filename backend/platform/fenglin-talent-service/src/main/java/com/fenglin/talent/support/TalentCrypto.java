package com.fenglin.talent.support;

import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.crypto.PiiHasher;
import com.fenglin.talent.service.TalentTenantKeyService;
import org.springframework.stereotype.Component;

/**
 * 云才 PII 处理：手机号/身份证号 AES-256-GCM 密文 + 租户盐 SHA-256 哈希，与 CRM/云协方案保持一致。
 *
 * <p>租户盐由 {@link TalentTenantKeyService} 在首次使用时落库（talent_tenant_key），后续复用。</p>
 */
@Component
public class TalentCrypto {

    private final AesGcmCipher cipher;
    private final PiiHasher hasher;
    private final TalentTenantKeyService tenantKeyService;

    public TalentCrypto(AesGcmCipher cipher, PiiHasher hasher, TalentTenantKeyService tenantKeyService) {
        this.cipher = cipher;
        this.hasher = hasher;
        this.tenantKeyService = tenantKeyService;
    }

    public String tenantSalt(long tenantId) {
        return tenantKeyService.salt(tenantId);
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
