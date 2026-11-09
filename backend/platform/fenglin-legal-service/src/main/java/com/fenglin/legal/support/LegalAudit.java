package com.fenglin.legal.support;

import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 云法审计事件发布（复用 fenglin-common-audit）。
 */
@Component
public class LegalAudit {

    private final AuditSink auditSink;

    public LegalAudit(AuditSink auditSink) {
        this.auditSink = auditSink;
    }

    public void publish(String eventType, String action, String resourceType, Object resourceId, String riskLevel) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                "legal",
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

    public void low(String action, String resourceType, Object resourceId) {
        publish("legal." + action, action, resourceType, resourceId, "LOW");
    }

    public void high(String action, String resourceType, Object resourceId) {
        publish("legal." + action, action, resourceType, resourceId, "HIGH");
    }
}
