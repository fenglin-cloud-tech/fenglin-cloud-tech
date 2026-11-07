package com.fenglin.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jAuditSink implements AuditSink {

    private static final Logger log = LoggerFactory.getLogger(Slf4jAuditSink.class);

    @Override
    public void publish(AuditEvent event) {
        log.info("audit event={} tenant={} user={} action={} resource={}:{}",
                event.eventType(), event.tenantId(), event.userId(), event.action(),
                event.resourceType(), event.resourceId());
    }
}
