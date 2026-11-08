package com.fenglin.workflow.service;

import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class WorkflowAuditor {

    private final AuditSink auditSink;

    public WorkflowAuditor(AuditSink auditSink) {
        this.auditSink = auditSink;
    }

    public void publish(long tenantId, String eventType, String resourceType, String resourceId, String risk) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                "workflow",
                eventType,
                risk,
                tenantId,
                snapshot == null ? null : snapshot.userId(),
                snapshot == null ? null : snapshot.clientType(),
                RequestContext.requestId(),
                resourceType,
                resourceId,
                Instant.now()));
    }
}
