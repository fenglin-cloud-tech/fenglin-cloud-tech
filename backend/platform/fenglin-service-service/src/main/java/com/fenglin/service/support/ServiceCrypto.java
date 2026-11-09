package com.fenglin.service.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.crypto.PiiHasher;
import com.fenglin.service.mapper.CommonsMapper;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 云服 PII/凭据处理：提单人联系方式、RPA 凭据 AES-256-GCM 密文 + 租户盐 SHA-256 哈希。
 */
@Component
public class ServiceCrypto {

    private final AesGcmCipher cipher;
    private final PiiHasher hasher;
    private final CommonsMapper commonsMapper;
    private final SecureRandom random = new SecureRandom();

    public ServiceCrypto(AesGcmCipher cipher, PiiHasher hasher, CommonsMapper commonsMapper) {
        this.cipher = cipher;
        this.hasher = hasher;
        this.commonsMapper = commonsMapper;
    }

    public String tenantSalt(long tenantId) {
        String salt = commonsMapper.findTenantSalt(tenantId);
        if (salt != null) {
            return salt;
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        salt = Base64.getEncoder().encodeToString(bytes);
        commonsMapper.insertTenantKey(IdWorker.getId(), tenantId, salt, ServiceContext.userId());
        return salt;
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

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 2) + "****" + trimmed.substring(trimmed.length() - 2);
    }
}
