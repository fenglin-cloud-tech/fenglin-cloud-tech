package com.fenglin.audit.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.audit.mapper.AuditChainMapper;
import com.fenglin.audit.mapper.AuditLogMapper;
import com.fenglin.audit.web.dto.ChainVerifyResult;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计哈希链：写入与校验。
 * 写入与审计日志同一事务，按 (租户, 月份) 维度串行追加；任一步失败抛异常，由事务回滚保证“链写不进去则审计日志也不落库”。
 */
@Service
public class AuditChainService {

    private static final DateTimeFormatter PARTITION_KEY = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);
    private static final String BASELINE_EVENT = "CHAIN_BASELINE";

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
     * 校验 [from, to] 区间内该租户的哈希链。
     *
     * <p>不再只验现存行的相对连续性，同时比对链路行数与同期审计日志行数，并区分缺口位置：
     * 基线（{@code CHAIN_BASELINE}）之前的缺口归为不可验证区间，基线之后的缺口直接判失败。
     * 链按月份独立串行（每月从 seq=1 起），因此逐月从头校验。
     */
    public ChainVerifyResult verify(long tenantId, Instant from, Instant to) {
        String fromKey = from == null ? "000000" : PARTITION_KEY.format(from);
        String toKey = to == null ? "999999" : PARTITION_KEY.format(to);

        List<Map<String, Object>> rows = auditChainMapper.findRows(tenantId, fromKey, toKey);
        long checked = rows.size();
        long auditLogCount = auditLogMapper.countInRange(tenantId, from, to);

        Map<String, Object> baseline = auditLogMapper.findBaseline(tenantId);
        Instant baselineAt = baseline == null ? null : toInstant(baseline.get("occurred_at"));
        Long baselineLogId = baseline == null ? null : ((Number) baseline.get("log_id")).longValue();

        Map<String, Object> unchained = auditLogMapper.summarizeUnchainedLogs(tenantId, from, to, baselineAt);
        long unchainedCount = number(unchained.get("total"));
        long beforeBaseline = baselineAt == null ? unchainedCount : number(unchained.get("before_baseline"));
        long afterBaseline = baselineAt == null ? 0L : number(unchained.get("after_baseline"));
        Long gapFromLogId = unchainedCount == 0 ? null : number(unchained.get("from_log_id"));
        Long gapToLogId = unchainedCount == 0 ? null : number(unchained.get("to_log_id"));

        Map<String, Long> baselineSeqByPartition = new HashMap<>();
        for (Map<String, Object> row : rows) {
            if (BASELINE_EVENT.equals(row.get("event_type"))) {
                baselineSeqByPartition.putIfAbsent((String) row.get("partition_key"),
                        ((Number) row.get("seq_no")).longValue());
            }
        }

        String currentPartition = null;
        long expectedSeq = 1L;
        String expectedPrev = AuditChainHasher.ZERO_HASH;
        long verified = 0;

        for (Map<String, Object> row : rows) {
            String partitionKey = (String) row.get("partition_key");
            long seqNo = ((Number) row.get("seq_no")).longValue();
            long logId = ((Number) row.get("log_id")).longValue();
            String contentHash = (String) row.get("content_hash");
            String prevHash = (String) row.get("prev_hash");
            String chainHash = (String) row.get("chain_hash");

            if (!partitionKey.equals(currentPartition)) {
                currentPartition = partitionKey;
                expectedSeq = 1L;
                expectedPrev = AuditChainHasher.ZERO_HASH;
            }
            verified++;

            if (seqNo > expectedSeq) {
                long lastMissing = seqNo - 1;
                Long baselineSeq = baselineSeqByPartition.get(partitionKey);
                // 缺口整体落在基线之前：记为不可验证区间，继续校验基线之后的部分
                if (baselineSeq != null && lastMissing < baselineSeq) {
                    beforeBaseline++;
                } else {
                    return result(tenantId, checked, auditLogCount, unchainedCount, false,
                            ChainVerifyResult.FAIL, seqNo, logId, gapFromLogId, gapToLogId, baselineLogId,
                            "序号不连续：分区 " + partitionKey + " 期望 " + expectedSeq + "，实际 " + seqNo);
                }
            } else if (seqNo != expectedSeq) {
                return result(tenantId, checked, auditLogCount, unchainedCount, false,
                        ChainVerifyResult.FAIL, seqNo, logId, gapFromLogId, gapToLogId, baselineLogId,
                        "序号重复或回退：分区 " + partitionKey + " 期望 " + expectedSeq + "，实际 " + seqNo);
            }
            if (!expectedPrev.equals(prevHash)) {
                return result(tenantId, checked, auditLogCount, unchainedCount, false,
                        ChainVerifyResult.FAIL, seqNo, logId, gapFromLogId, gapToLogId, baselineLogId,
                        "prev_hash 与前一条 chain_hash 不一致：分区 " + partitionKey + " 序号 " + seqNo);
            }
            Map<String, Object> log = auditLogMapper.findContentById(logId);
            if (log == null) {
                return result(tenantId, checked, auditLogCount, unchainedCount, false,
                        ChainVerifyResult.FAIL, seqNo, logId, gapFromLogId, gapToLogId, baselineLogId,
                        "链记录对应的审计日志不存在：log_id=" + logId);
            }
            String recomputedContent = content(log);
            if (!AuditChainHasher.sha256(recomputedContent).equals(contentHash)) {
                return result(tenantId, checked, auditLogCount, unchainedCount, false,
                        ChainVerifyResult.FAIL, seqNo, logId, gapFromLogId, gapToLogId, baselineLogId,
                        "content_hash 与审计内容不一致：log_id=" + logId);
            }
            if (!AuditChainHasher.chainHash(prevHash, contentHash).equals(chainHash)) {
                return result(tenantId, checked, auditLogCount, unchainedCount, false,
                        ChainVerifyResult.FAIL, seqNo, logId, gapFromLogId, gapToLogId, baselineLogId,
                        "chain_hash 校验失败：log_id=" + logId);
            }

            expectedSeq = seqNo + 1;
            expectedPrev = chainHash;
        }

        // 基线之后仍有未入链的审计日志：缺链已影响当前可验证区间，判失败
        if (afterBaseline > 0) {
            return result(tenantId, checked, auditLogCount, unchainedCount, false,
                    ChainVerifyResult.FAIL, null, null, gapFromLogId, gapToLogId, baselineLogId,
                    "校验失败：基线之后仍有 " + afterBaseline + " 条审计日志未入链");
        }

        // 跨租户被 RLS 拦截时看不到任何行，不能据此断言完整
        Long contextTenantId = TenantContext.getTenantId();
        if (checked == 0 && auditLogCount == 0 && contextTenantId != null && contextTenantId != tenantId) {
            return result(tenantId, checked, auditLogCount, unchainedCount, false,
                    ChainVerifyResult.UNVERIFIABLE, null, null, null, null, baselineLogId,
                    "跨租户校验被 RLS 拦截，结果不可判定");
        }

        if (beforeBaseline > 0) {
            return result(tenantId, checked, auditLogCount, unchainedCount, false,
                    ChainVerifyResult.UNVERIFIABLE, null, null, gapFromLogId, gapToLogId, baselineLogId,
                    "存在不可验证区间：基线之前有 " + beforeBaseline + " 条审计日志未入链，基线 log_id="
                            + baselineLogId + " 起可验证");
        }

        return result(tenantId, checked, auditLogCount, unchainedCount, true,
                ChainVerifyResult.PASS, null, null, null, null, baselineLogId,
                "校验通过：链 " + verified + " 行与审计日志 " + auditLogCount + " 条一一对应");
    }

    private ChainVerifyResult result(long tenantId, long checked, long auditLogCount, long unchainedCount,
                                     boolean valid, String status, Long brokenSeq, Long brokenLogId,
                                     Long gapFromLogId, Long gapToLogId, Long baselineLogId, String message) {
        return new ChainVerifyResult(tenantId, checked, auditLogCount, unchainedCount, valid, status,
                brokenSeq, brokenLogId, gapFromLogId, gapToLogId, baselineLogId, message);
    }

    private long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
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
