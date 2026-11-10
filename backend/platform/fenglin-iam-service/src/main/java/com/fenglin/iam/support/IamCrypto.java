package com.fenglin.iam.support;

import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.crypto.PiiHasher;
import com.fenglin.iam.service.IamTenantKeyService;
import org.springframework.stereotype.Component;

/**
 * IAM PII 处理：手机号/邮箱 AES-256-GCM 密文 + 租户盐 SHA-256 哈希。
 *
 * <p>租户盐由 {@link IamTenantKeyService} 在首次使用时落库（hub_tenant_key），后续复用。</p>
 */
@Component
public class IamCrypto {

    private static final String MOBILE_AAD_SUFFIX = ":hub_user:mobile_enc";
    private static final String EMAIL_AAD_SUFFIX = ":hub_user:email_enc";

    private final AesGcmCipher cipher;
    private final PiiHasher hasher;
    private final IamTenantKeyService tenantKeyService;

    public IamCrypto(AesGcmCipher cipher, PiiHasher hasher, IamTenantKeyService tenantKeyService) {
        this.cipher = cipher;
        this.hasher = hasher;
        this.tenantKeyService = tenantKeyService;
    }

    public String normalizePhone(String phone) {
        return hasher.normalizePhone(phone);
    }

    public String normalizeEmail(String email) {
        return hasher.normalizeEmail(email);
    }

    /** AAD 绑定租户、表与字段，防止密文跨租户/跨字段搬运。 */
    public String encryptMobile(long tenantId, String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        return cipher.encrypt(plain, tenantId + MOBILE_AAD_SUFFIX);
    }

    public String hashMobile(long tenantId, String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return hasher.hashHex(normalized, tenantKeyService.salt(tenantId));
    }

    public String encryptEmail(long tenantId, String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        return cipher.encrypt(plain, tenantId + EMAIL_AAD_SUFFIX);
    }

    public String hashEmail(long tenantId, String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return hasher.hashHex(normalized, tenantKeyService.salt(tenantId));
    }

    public String decryptMobile(long tenantId, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        return cipher.decrypt(encoded, tenantId + MOBILE_AAD_SUFFIX);
    }

    /** 手机号脱敏：保留前 3 位与后 4 位，例如 +86138****1234。 */
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
}
