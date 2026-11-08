package com.fenglin.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.mapper.AppKeyMapper;
import com.fenglin.openapi.mapper.OpenAppMapper;
import com.fenglin.openapi.web.dto.OpenApiDtos.KeyResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * HUB-API-1003 密钥生成/轮换：AppSecret 明文仅本次回显，落库为 AES-256-GCM 密文。
 */
@Service
public class OpenApiKeyService {

    private static final String AAD_SUFFIX = ":hub_app_key:app_secret_enc";

    private final AppKeyMapper keyMapper;
    private final OpenAppMapper appMapper;
    private final AesGcmCipher cipher;
    private final AuditSink auditSink;
    private final SecureRandom secureRandom = new SecureRandom();

    public OpenApiKeyService(AppKeyMapper keyMapper,
                             OpenAppMapper appMapper,
                             AesGcmCipher cipher,
                             AuditSink auditSink) {
        this.keyMapper = keyMapper;
        this.appMapper = appMapper;
        this.cipher = cipher;
        this.auditSink = auditSink;
    }

    @Transactional
    public KeyResponse issue(long appId, String action, Integer graceHours, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> app = appMapper.findById(tenantId, appId);
        if (app == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "开放应用不存在: " + appId);
        }
        if ("REJECTED".equals(app.get("review_status"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "应用审核未通过，不能生成密钥");
        }
        String normalized = action == null || action.isBlank() ? "ROTATE" : action.trim().toUpperCase();
        if (!"ROTATE".equals(normalized) && !"CREATE".equals(normalized)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "action 仅支持 ROTATE/CREATE");
        }
        int grace = graceHours == null ? 0 : graceHours;
        if (grace < 0 || grace > 720) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "grace_hours 取值范围 0~720");
        }
        if ("ROTATE".equals(normalized)) {
            if (grace == 0) {
                keyMapper.revokeActive(tenantId, appId);
            } else {
                keyMapper.expireActiveAfter(tenantId, appId, grace);
            }
        }

        String appKey = "AK" + randomHex(12);
        String appSecret = "SK-" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        String fingerprint = sha256Hex(appSecret).substring(0, 6);
        long keyId = IdWorker.getId();
        keyMapper.insert(keyId, tenantId, appId, appKey,
                cipher.encrypt(appSecret, tenantId + AAD_SUFFIX), fingerprint, operatorId);

        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), "OPENAPI_APP_KEY_ISSUED", "openapi",
                "CREATE", "HIGH", tenantId, operatorId,
                context == null ? null : context.clientType(),
                context == null ? null : context.requestId(), "APP_KEY", String.valueOf(keyId), Instant.now()));
        return new KeyResponse(appKey, appSecret, Instant.now(), fingerprint);
    }

    byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    String randomHex(int length) {
        return HexFormat.of().formatHex(randomBytes(length));
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
