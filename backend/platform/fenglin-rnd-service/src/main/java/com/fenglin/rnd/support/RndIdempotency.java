package com.fenglin.rnd.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.rnd.mapper.CommonsMapper;
import org.springframework.stereotype.Component;

/**
 * 幂等：同一租户同一 Idempotency-Key 只产生一次业务单据。
 */
@Component
public class RndIdempotency {

    private final CommonsMapper commonsMapper;

    public RndIdempotency(CommonsMapper commonsMapper) {
        this.commonsMapper = commonsMapper;
    }

    public Long find(String key) {
        if (key == null) {
            return null;
        }
        return commonsMapper.findIdempotentBizId(RndContext.tenantId(), key);
    }

    public void remember(String key, String apiPath, long bizId) {
        if (key == null) {
            return;
        }
        commonsMapper.insertIdempotent(IdWorker.getId(), RndContext.tenantId(), key, apiPath, bizId,
                RndContext.userId());
    }
}
