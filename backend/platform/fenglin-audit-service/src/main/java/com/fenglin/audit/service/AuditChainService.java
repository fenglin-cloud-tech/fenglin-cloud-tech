package com.fenglin.audit.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.audit.mapper.AuditChainMapper;
import com.fenglin.audit.mapper.AuditLogMapper;
import com.fenglin.audit.web.dto.ChainVerifyResult;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 审计哈希链：写入与校验。
 * 写入与审计日志同一事务，按 (租户, 月份) 维度串行追加；任一步失败抛异常，由事务回滚保证“链写不进去则审计日志也不落库”。
 */
@Service
public class AuditChainService {

    private static final DateTimeFormatter PARTITION_KEY = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    private final AuditChainMapper auditChainMapper;
    private final AuditLogMapper auditLogMapper;

    public AuditChainService(AuditChainMapper auditChainMapper, AuditLogMapper auditLogMapper) {
        this.auditChainMapper = auditChainMapper;
        this.auditLogMapper = auditLogMapper;
    }

    public void append(long logId, long tenantId, String partitionKey, String content) {
        auditChainMapper.lock(tenantId + ":" + partitionKey);
        Map<String, Object> last = auditChainMapper.findLast(tenantId, partitionKey);
        long seqNo = 1L;
        String prevHash = AuditChainHasher.ZERO_HASH;
        if (last != null) {
            seqNo = ((Number) last.get("seq_no")).longValue() + 1;
            prevHash = (String) last.get("chain_hash");
        }
        String contentHash = AuditChainHasher.sha256(content);
        String chainHash = AuditChainHasher.chainHash(prevHash, contentHash);
        auditChainMapper.insert(IdWorker.getId(), tenantId, logId, partitionKey, seqNo,
                contentHash, prevHash, chainHash);
    }

    /**
     * 校验 [from, to] 区间内该租户的哈希链：序号连续、prev_hash 衔接、content_hash 可复算、chain_hash 正确。
     * 链按月份独立串行（每月从 seq=1 起），因此逐月从头校验。
     */
    public ChainVerifyResult verify(long tenantId, Instant from, Instant to) {
        String fromKey = from == null ? "000000" : PARTITION_KEY.format(from);
        String toKey = to == null ? "999999" : PARTITION_KEY.format(to);
        List<String> partitionKeys = auditChainMapper.findPartitionKeys(tenantId, fromKey, toKey);
        long checked = 0;
        for (String partitionKey : partitionKeys) {
            List<Map<String, Object>> rows = auditChainMapper.findFrom(tenantId, partitionKey, 1L);
            long expectedSeq = 1L;
            String expectedPrev = AuditChainHasher.ZERO_HASH;
            for (Map<String, Object> row : rows) {
                long seqNo = ((Number) row.get("seq_no")).longValue();
                long logId = ((Number) row.get("log_id")).longValue();
                String contentHash = (String) row.get("content_hash");
                String prevHash = (String) row.get("prev_hash");
                String chainHash = (String) row.get("chain_hash");
                checked++;

                if (seqNo != expectedSeq) {
                    return broken(tenantId, checked, seqNo, logId,
                            "序号不连续：分区 " + partitionKey + " 期望 " + expectedSeq + "，实际 " + seqNo);
                }
                if (!expectedPrev.equals(prevHash)) {
                    return broken(tenantId, checked, seqNo, logId,
                            "prev_hash 与前一条 chain_hash 不一致：分区 " + partitionKey + " 序号 " + seqNo);
                }
                Map<String, Object> log = auditLogMapper.findContentById(logId);
                if (log == null) {
                    return broken(tenantId, checked, seqNo, logId,
                            "链记录对应的审计日志不存在：log_id=" + logId);
                }
                String recomputedContent = content(log);
                if (!AuditChainHasher.sha256(recomputedContent).equals(contentHash)) {
                    return broken(tenantId, checked, seqNo, logId,
                            "content_hash 与审计内容不一致：log_id=" + logId);
                }
                if (!AuditChainHasher.chainHash(prevHash, contentHash).equals(chainHash)) {
                    return broken(tenantId, checked, seqNo, logId,
                            "chain_hash 校验失败：log_id=" + logId);
                }

                expectedSeq++;
                expectedPrev = chainHash;
            }
        }
        return new ChainVerifyResult(tenantId, checked, true, null, null, "校验通过");
    }

    private ChainVerifyResult broken(long tenantId, long checked, long seqNo, long logId, String message) {
        return new ChainVerifyResult(tenantId, checked, false, seqNo, logId, message);
    }

    private String content(Map<String, Object> log) {
        return AuditChainHasher.content(
                ((Number) log.get("id")).longValue(),
                ((Number) log.get("tenant_id")).longValue(),
                (String) log.get("log_no"),
                (String) log.get("event_type"),
                (String) log.get("module"),
                (String) log.get("action"),
                log.get("operator_id") == null ? null : ((Number) log.get("operator_id")).longValue(),
                (String) log.get("operator_name"),
                (String) log.get("target_type"),
                (String) log.get("target_id"),
                (String) log.get("request_id"),
                (String) log.get("trace_id"),
                (String) log.get("client_type"),
                (String) log.get("result"),
                (String) log.get("risk_level"),
                AuditChainHasher.canonicalDetail((String) log.get("detail")),
                toInstant(log.get("occurred_at")));
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }
}
