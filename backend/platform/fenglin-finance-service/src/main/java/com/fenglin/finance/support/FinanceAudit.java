package com.fenglin.finance.support;

import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 云财审计事件发布（复用 fenglin-common-audit，不重复实现审计链路）。
 */
@Component
public class FinanceAudit {

    private final AuditSink auditSink;

    public FinanceAudit(AuditSink auditSink) {
        this.auditSink = auditSink;
    }

    public void publish(String eventType, String action, String resourceType, Object resourceId, String riskLevel) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                "finance",
                action,
                riskLevel,
                snapshot == null ? null : snapshot.tenantId(),
                snapshot == null ? null : snapshot.userId(),
                snapshot == null ? null : snapshot.clientType(),
                snapshot == null ? null : snapshot.requestId(),
                resourceType,
                resourceId == null ? null : String.valueOf(resourceId),
                Instant.now()));
    }
}
