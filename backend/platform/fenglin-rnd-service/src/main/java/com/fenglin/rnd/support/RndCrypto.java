package com.fenglin.rnd.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.crypto.PiiHasher;
import com.fenglin.rnd.mapper.CommonsMapper;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 云研 PII/凭据处理：作者邮箱、代码库访问令牌 AES-256-GCM 密文 + 租户盐 SHA-256 哈希。
 */
@Component
public class RndCrypto {

    private final AesGcmCipher cipher;
    private final PiiHasher hasher;
    private final CommonsMapper commonsMapper;
    private final SecureRandom random = new SecureRandom();

    public RndCrypto(AesGcmCipher cipher, PiiHasher hasher, CommonsMapper commonsMapper) {
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
        commonsMapper.insertTenantKey(IdWorker.getId(), tenantId, salt, RndContext.userId());
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

    /** 令牌脱敏：保留前 4 位与后 4 位。 */
    public String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String trimmed = token.strip();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
