package com.fenglin.collab.support;

import com.fenglin.collab.CollabErrorCode;
import com.fenglin.common.core.BizException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 文档图片短期访问令牌（COL-07）。
 *
 * <p>图片走服务端代理，不暴露 MinIO 预签名直链；<img> 无法携带 Authorization，
 * 因此由文档详情/分享详情签发 5 分钟有效的 asset_token，绑定
 * 租户 + docId + fileId + 主体（登录用户或分享链接令牌哈希）+ 过期时间，HMAC-SHA256 防伪。</p>
 */
@Component
public class ImageAssetTokenService {

    /** 令牌有效期：5 分钟。 */
    public static final long TTL_SECONDS = 300;
    private static final String PREFIX = "v1";

    private final byte[] signingKey;

    public ImageAssetTokenService(ImageCrypto imageCrypto) {
        this.signingKey = imageCrypto.tokenKey();
    }

    public record AssetToken(long tenantId, String docId, long fileId, String subject, long expiresAt) {
    }

    public String issue(long tenantId, String docId, long fileId, String subject) {
        return issue(tenantId, docId, fileId, subject, TTL_SECONDS);
    }

    public String issue(long tenantId, String docId, long fileId, String subject, long ttlSeconds) {
        long expiresAt = System.currentTimeMillis() / 1000 + ttlSeconds;
        String payload = String.join(".", PREFIX, String.valueOf(tenantId), docId,
                String.valueOf(fileId), subject, String.valueOf(expiresAt));
        return payload + "." + sign(payload);
    }

    public AssetToken verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(CollabErrorCode.DOC_NO_PERMISSION, "缺少图片访问令牌");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 7 || !PREFIX.equals(parts[0])) {
            throw new BizException(CollabErrorCode.DOC_NO_PERMISSION, "图片访问令牌格式非法");
        }
        String payload = String.join(".", parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
        byte[] expected = sign(payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = parts[6].getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BizException(CollabErrorCode.DOC_NO_PERMISSION, "图片访问令牌签名不匹配");
        }
        try {
            long tenantId = Long.parseLong(parts[1]);
            long fileId = Long.parseLong(parts[3]);
            long expiresAt = Long.parseLong(parts[5]);
            if (tenantId <= 0 || fileId <= 0 || parts[2].isBlank() || parts[4].isBlank()) {
                throw new NumberFormatException("blank");
            }
            if (expiresAt < System.currentTimeMillis() / 1000) {
                throw new BizException(CollabErrorCode.DOC_NO_PERMISSION, "图片访问令牌已过期");
            }
            return new AssetToken(tenantId, parts[2], fileId, parts[4], expiresAt);
        } catch (NumberFormatException ex) {
            throw new BizException(CollabErrorCode.DOC_NO_PERMISSION, "图片访问令牌格式非法");
        }
    }

    /** 登录用户主体。 */
    public String userSubject(long userId) {
        return "u" + userId;
    }

    /** 分享链接主体：链接令牌哈希，避免把原令牌写进图片 URL。 */
    public String linkSubject(String linkToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(linkToken.getBytes(StandardCharsets.UTF_8));
            return "lh" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("图片令牌签名失败", ex);
        }
    }
}
