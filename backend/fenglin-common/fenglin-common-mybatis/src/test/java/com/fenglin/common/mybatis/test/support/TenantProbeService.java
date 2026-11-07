package com.fenglin.common.mybatis.test.support;

import com.fenglin.common.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantProbeService {

    private final TenantProbeMapper mapper;

    public TenantProbeService(TenantProbeMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public ProbeResult queryFor(long tenantId) {
        TenantContext.set(tenantId, null, "ADMIN", "test-request", false);
        try {
            return new ProbeResult(mapper.findIds(), mapper.backendPid(), mapper.currentTenantSetting());
        } finally {
            TenantContext.clear();
        }
    }
}
