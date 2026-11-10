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
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalKeyView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
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

    /** 开发者门户：密钥列表只返回掩码与指纹，绝不返回 AppSecret 明文/密文。 */
    @Transactional(readOnly = true)
    public List<PortalKeyView> listKeys(long appId) {
        long tenantId = TenantContext.requireTenantId();
        return keyMapper.listByApp(tenantId, appId).stream()
                .map(row -> {
                    String fingerprint = (String) row.get("secret_fingerprint");
                    String masked = "SK-******" + (fingerprint == null ? "" : fingerprint);
                    return new PortalKeyView(
                            ((Number) row.get("id")).longValue(),
                            (String) row.get("app_key"),
                            masked,
                            fingerprint,
                            (String) row.get("status"),
                            toInstant(row.get("expire_at")),
                            toInstant(row.get("created_at")));
                })
                .toList();
    }

    /** 开发者门户签发/轮换：校验应用归属当前用户，且仅审核通过的应用可签发。 */
    @Transactional
    public KeyResponse issueOwned(long appId, String action, Integer graceHours, long ownerId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> app = appMapper.findById(tenantId, appId);
        if (app == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "开放应用不存在: " + appId);
        }
        Object owner = app.get("owner_id");
        if (!(owner instanceof Number number) || number.longValue() != ownerId) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权访问该应用: " + appId);
        }
        if (!"APPROVED".equals(app.get("review_status"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "应用审核通过后才能签发密钥");
        }
        return issue(appId, action, graceHours, ownerId);
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return null;
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
