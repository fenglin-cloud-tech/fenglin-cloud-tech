package com.fenglin.intelligence.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.intelligence.mapper.CommonsMapper;
import org.springframework.stereotype.Component;

/**
 * 写接口幂等：同一租户 + 路径 + Idempotency-Key 复用首次业务 ID。
 */
@Component
public class IntelligenceIdempotency {

    private final CommonsMapper commonsMapper;

    public IntelligenceIdempotency(CommonsMapper commonsMapper) {
        this.commonsMapper = commonsMapper;
    }

    public Long existing(String apiPath, String idemKey) {
        if (idemKey == null || idemKey.isBlank()) {
            return null;
        }
        return commonsMapper.findIdempotentBizId(IntelligenceContext.tenantId(), apiPath, idemKey);
    }

    public void record(String apiPath, String idemKey, long bizId) {
        if (idemKey == null || idemKey.isBlank()) {
            return;
        }
        commonsMapper.insertIdempotencyRecord(IdWorker.getId(), IntelligenceContext.tenantId(), apiPath, idemKey,
                bizId, IntelligenceContext.userId());
    }
}
