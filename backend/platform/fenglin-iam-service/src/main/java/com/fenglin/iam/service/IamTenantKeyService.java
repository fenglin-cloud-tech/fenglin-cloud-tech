package com.fenglin.iam.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.iam.mapper.IamTenantKeyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * 租户密钥：首次加密/哈希时按 tenant_id 落库，已存在则复用。
 *
 * <p>dek_cipher 以平台 AES-GCM 加密存储（AAD 绑定租户与字段），tenant_salt 作为哈希盐。</p>
 */
@Service
public class IamTenantKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IamTenantKeyMapper tenantKeyMapper;
    private final AesGcmCipher cipher;

    public IamTenantKeyService(IamTenantKeyMapper tenantKeyMapper, AesGcmCipher cipher) {
        this.tenantKeyMapper = tenantKeyMapper;
        this.cipher = cipher;
    }

    @Transactional
    public String salt(long tenantId) {
        Map<String, Object> existing = tenantKeyMapper.findActive(tenantId);
        String salt = existing == null ? null : (String) existing.get("tenantSalt");
        if (salt != null && !salt.isBlank()) {
            return salt;
        }
        byte[] saltBytes = new byte[32];
        byte[] dekBytes = new byte[32];
        RANDOM.nextBytes(saltBytes);
        RANDOM.nextBytes(dekBytes);
        salt = Base64.getEncoder().encodeToString(saltBytes);
        String dekCipher = cipher.encrypt(Base64.getEncoder().encodeToString(dekBytes),
                tenantId + ":hub_tenant_key:dek_cipher");
        TenantContext.Snapshot snapshot = TenantContext.get();
        Long userId = snapshot == null ? null : snapshot.userId();
        tenantKeyMapper.insertKey(IdWorker.getId(), tenantId, 1, dekCipher, salt,
                userId == null ? 0L : userId);
        return salt;
    }
}
