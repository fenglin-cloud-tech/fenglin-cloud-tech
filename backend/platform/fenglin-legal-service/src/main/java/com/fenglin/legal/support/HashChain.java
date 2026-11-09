package com.fenglin.legal.support;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 哈希链：prev_hash + 业务字段 -> block_hash，SHA-256，用于用印/签署/存证/DLP 审计防篡改。
 */
@Component
public class HashChain {

    public String sha256(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                if (part != null) {
                    digest.update(part.getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    public String block(String tenantId, String table, String bizId, String content, String prevHash) {
        return sha256(tenantId, "|", table, "|", bizId, "|", content == null ? "" : content,
                "|", prevHash == null ? "" : prevHash);
    }
}
