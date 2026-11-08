package com.fenglin.audit.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.audit.mapper.AuditChainMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 审计哈希链写入：与审计日志同一事务，按 (租户, 月份) 维度串行追加。
 * 任一步失败抛异常，由事务回滚保证“链写不进去则审计日志也不落库”。
 */
@Service
public class AuditChainService {

    private final AuditChainMapper auditChainMapper;

    public AuditChainService(AuditChainMapper auditChainMapper) {
        this.auditChainMapper = auditChainMapper;
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
}
