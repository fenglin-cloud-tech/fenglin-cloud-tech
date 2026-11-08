package com.fenglin.finance.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.finance.FinanceErrorCode;
import com.fenglin.finance.mapper.CommonsMapper;
import com.fenglin.finance.mapper.VoucherMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 凭证哈希链：record_hash = SHA-256(prev_hash | action | operator | occurred_at | payload)，
 * signature = HMAC-SHA256(secret, record_hash)。每次状态流转追加一条，链式校验防篡改。
 *
 * <p>偏差标注：文档要求 chain_seq 在账套内连续，平台实现使用全局序列保证唯一；校验以 prev_hash 连链为准。</p>
 */
@Component
public class VoucherHashChain {

    private final VoucherMapper voucherMapper;
    private final CommonsMapper commonsMapper;
    private final String secret;

    public VoucherHashChain(VoucherMapper voucherMapper, CommonsMapper commonsMapper,
                            @Value("${fenglin.finance.chain-secret:fenglin-finance-chain-dev}") String secret) {
        this.voucherMapper = voucherMapper;
        this.commonsMapper = commonsMapper;
        this.secret = secret;
    }

    public String append(long tenantId, long voucherId, String action, long operatorId, String payload) {
        Map<String, Object> last = voucherMapper.findLastAuditLog(tenantId, voucherId);
        String prevHash = last == null ? "GENESIS" : Rows.str(last, "recordHash");
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String raw = prevHash + "|" + action + "|" + operatorId + "|" + occurredAt.toInstant().toEpochMilli()
                + "|" + payload;
        String recordHash = sha256(raw);
        voucherMapper.insertAuditLog(IdWorker.getId(), tenantId, voucherId, commonsMapper.nextChainSeq(), action,
                operatorId, String.valueOf(operatorId), occurredAt, recordHash, prevHash, sign(recordHash));
        return recordHash;
    }

    /** 校验链完整性，返回最后一条记录哈希；断链抛 4001009。 */
    public String verify(long tenantId, long voucherId) {
        List<Map<String, Object>> logs = voucherMapper.listAuditLogs(tenantId, voucherId);
        String prev = "GENESIS";
        for (Map<String, Object> log : logs) {
            String prevHash = Rows.str(log, "prevHash");
            String recordHash = Rows.str(log, "recordHash");
            String signature = Rows.str(log, "signature");
            if (!prev.equals(prevHash) || !sign(recordHash).equals(signature)) {
                throw new BizException(FinanceErrorCode.HASH_CHAIN_BROKEN);
            }
            prev = recordHash;
        }
        return prev;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }
}
