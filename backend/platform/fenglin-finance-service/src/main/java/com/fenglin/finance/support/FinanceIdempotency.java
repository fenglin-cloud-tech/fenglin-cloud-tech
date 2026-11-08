package com.fenglin.finance.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.finance.mapper.CommonsMapper;
import org.springframework.stereotype.Component;

/**
 * 幂等：同一租户同一 Idempotency-Key 只产生一次业务单据。
 * 业务侧先 find，命中直接返回原单据；未命中执行业务后 remember。
 */
@Component
public class FinanceIdempotency {

    private final CommonsMapper commonsMapper;

    public FinanceIdempotency(CommonsMapper commonsMapper) {
        this.commonsMapper = commonsMapper;
    }

    public Long find(String key) {
        if (key == null) {
            return null;
        }
        return commonsMapper.findIdempotentBizId(FinanceContext.tenantId(), key);
    }

    public void remember(String key, String apiPath, long bizId) {
        if (key == null) {
            return;
        }
        commonsMapper.insertIdempotent(IdWorker.getId(), FinanceContext.tenantId(), key, apiPath, bizId,
                FinanceContext.userId());
    }
}
