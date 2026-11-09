package com.fenglin.intelligence.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.crypto.PiiHasher;
import com.fenglin.intelligence.mapper.CommonsMapper;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 云智 PII/凭据加解密与指纹：AES-256-GCM 密文 + 租户盐 SHA-256 哈希。
 */
@Component
public class IntelligenceCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AesGcmCipher cipher;
    private final PiiHasher hasher;
    private final CommonsMapper commonsMapper;

    public IntelligenceCrypto(AesGcmCipher cipher, PiiHasher hasher, CommonsMapper commonsMapper) {
        this.cipher = cipher;
        this.hasher = hasher;
        this.commonsMapper = commonsMapper;
    }

    public String encrypt(String plaintext, String aad) {
        return cipher.encrypt(plaintext, aad);
    }

    public String decrypt(String ciphertext, String aad) {
        return cipher.decrypt(ciphertext, aad);
    }

    public String hash(String value) {
        return hasher.hashHex(value, tenantSalt());
    }

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private String tenantSalt() {
        long tenantId = IntelligenceContext.tenantId();
        String salt = commonsMapper.findTenantSalt(tenantId);
        if (salt != null && !salt.isBlank()) {
            return salt;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String generated = Base64.getEncoder().encodeToString(bytes);
        commonsMapper.insertTenantSalt(IdWorker.getId(), tenantId, generated, IntelligenceContext.userId());
        return generated;
    }
}
