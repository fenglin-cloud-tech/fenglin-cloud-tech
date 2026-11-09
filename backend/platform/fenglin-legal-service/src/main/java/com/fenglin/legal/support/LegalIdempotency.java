package com.fenglin.legal.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.legal.LegalErrorCode;
import com.fenglin.legal.mapper.CommonsMapper;
import org.springframework.stereotype.Component;

/**
 * 幂等：同一租户同一 Idempotency-Key + 接口路径只产生一次业务单据。
 * 用印/签署/审批/放行类接口按文档要求强制幂等。
 */
@Component
public class LegalIdempotency {

    private final CommonsMapper commonsMapper;

    public LegalIdempotency(CommonsMapper commonsMapper) {
        this.commonsMapper = commonsMapper;
    }

    public Long find(String key, String apiPath) {
        if (key == null) {
            return null;
        }
        return commonsMapper.findIdempotentBizId(LegalContext.tenantId(), key, apiPath);
    }

    public void remember(String key, String apiPath, long bizId) {
        if (key == null) {
            return;
        }
        commonsMapper.insertIdempotent(IdWorker.getId(), LegalContext.tenantId(), key, apiPath, bizId,
                LegalContext.userId());
    }

    public String require(String header, String apiPath) {
        String key = LegalContext.idempotencyKey(header);
        if (key == null) {
            throw new BizException(LegalErrorCode.PARAM_INVALID, "该接口要求 Idempotency-Key");
        }
        return key;
    }
}
