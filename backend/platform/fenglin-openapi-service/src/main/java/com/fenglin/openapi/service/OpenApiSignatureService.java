package com.fenglin.openapi.service;

import com.fenglin.common.core.BizException;
import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.crypto.CryptoException;
import com.fenglin.openapi.OpenApiErrorCode;
import com.fenglin.openapi.mapper.AppKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * 开放 API 签名验签：HMAC-SHA256(AppSecret, method + path + timestamp + nonce + bodyHash)。
 * 防重放：时间戳偏差 ≤ 5min + Redis nonce 一次性（TTL 5min）。
 */
@Service
public class OpenApiSignatureService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSignatureService.class);
    private static final String AAD_SUFFIX = ":hub_app_key:app_secret_enc";

    private final AppKeyMapper keyMapper;
    private final AesGcmCipher cipher;
    private final StringRedisTemplate redisTemplate;
    private final long timestampSkewSeconds;
    private final Duration nonceTtl;

    public OpenApiSignatureService(AppKeyMapper keyMapper,
                                   AesGcmCipher cipher,
                                   StringRedisTemplate redisTemplate,
                                   @Value("${fenglin.openapi.timestamp-skew-seconds:300}") long timestampSkewSeconds,
                                   @Value("${fenglin.openapi.nonce-ttl:PT5M}") Duration nonceTtl) {
        this.keyMapper = keyMapper;
        this.cipher = cipher;
        this.redisTemplate = redisTemplate;
        this.timestampSkewSeconds = timestampSkewSeconds;
        this.nonceTtl = nonceTtl;
    }

    public VerifiedApp verify(String appKey, String timestamp, String nonce, String signature,
                              String method, String path, byte[] body) {
        if (isBlank(appKey) || isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            throw invalid("缺少 X-App-Key/X-Timestamp/X-Nonce/X-Signature");
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException ex) {
            throw invalid("X-Timestamp 必须为秒级时间戳");
        }
        if (Math.abs(Instant.now().getEpochSecond() - epochSeconds) > timestampSkewSeconds) {
            throw invalid("请求时间戳超出 " + timestampSkewSeconds + " 秒允许窗口");
        }

        Map<String, Object> row = keyMapper.resolve(appKey);
        if (row == null) {
            throw invalid("AppKey 不存在");
        }
        if (!"ACTIVE".equals(row.get("key_status")) || !"ENABLED".equals(row.get("app_status"))) {
            throw invalid("AppKey 或应用未启用");
        }
        if (isExpired(row.get("expire_at"))) {
            throw invalid("AppKey 已过期");
        }

        long tenantId = ((Number) row.get("tenant_id")).longValue();
        String secret;
        try {
            secret = cipher.decrypt((String) row.get("app_secret_enc"), tenantId + AAD_SUFFIX);
        } catch (CryptoException ex) {
            log.error("AppSecret 解密失败：appKey={}", appKey, ex);
            throw invalid("AppSecret 不可用");
        }

        String bodyHash = sha256Hex(body == null ? new byte[0] : body);
        String payload = method.toUpperCase() + path + timestamp.trim() + nonce.trim() + bodyHash;
        String expected = hmacSha256Hex(secret, payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw invalid("签名不匹配");
        }

        String nonceKey = "openapi:nonce:" + appKey + ":" + nonce.trim();
        Boolean stored;
        try {
            stored = redisTemplate.opsForValue().setIfAbsent(nonceKey, "1", nonceTtl);
        } catch (RuntimeException ex) {
            log.error("nonce 存储不可用，拒绝请求：appKey={}", appKey, ex);
            throw new BizException(com.fenglin.common.core.CommonErrorCode.INTERNAL_ERROR, "防重放组件不可用");
        }
        if (!Boolean.TRUE.equals(stored)) {
            throw invalid("nonce 已被使用");
        }
        return new VerifiedApp(tenantId, ((Number) row.get("app_id")).longValue(), appKey);
    }

    static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", ex);
        }
    }

    static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 计算失败", ex);
        }
    }

    private boolean isExpired(Object expireAt) {
        if (expireAt == null) {
            return false;
        }
        if (expireAt instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().isBefore(Instant.now());
        }
        if (expireAt instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().isBefore(Instant.now());
        }
        if (expireAt instanceof java.util.Date date) {
            return date.toInstant().isBefore(Instant.now());
        }
        return false;
    }

    private BizException invalid(String message) {
        return new BizException(OpenApiErrorCode.SIGNATURE_INVALID, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record VerifiedApp(long tenantId, long appId, String appKey) {
    }
}
