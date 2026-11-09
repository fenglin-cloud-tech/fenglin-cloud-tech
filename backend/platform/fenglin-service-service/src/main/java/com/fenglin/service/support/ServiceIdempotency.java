package com.fenglin.service.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.service.mapper.CommonsMapper;
import org.springframework.stereotype.Component;

/**
 * 幂等：同一租户同一 Idempotency-Key + 接口路径只产生一次业务单据。
 */
@Component
public class ServiceIdempotency {

    private final CommonsMapper commonsMapper;

    public ServiceIdempotency(CommonsMapper commonsMapper) {
        this.commonsMapper = commonsMapper;
    }

    public Long find(String key, String apiPath) {
        if (key == null) {
            return null;
        }
        return commonsMapper.findIdempotentBizId(ServiceContext.tenantId(), key, apiPath);
    }

    public void remember(String key, String apiPath, long bizId) {
        if (key == null) {
            return;
        }
        commonsMapper.insertIdempotent(IdWorker.getId(), ServiceContext.tenantId(), key, apiPath, bizId,
                ServiceContext.userId());
    }
}
