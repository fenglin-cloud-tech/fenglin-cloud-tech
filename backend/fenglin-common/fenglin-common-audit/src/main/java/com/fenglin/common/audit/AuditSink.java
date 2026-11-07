package com.fenglin.common.audit;

public interface AuditSink {

    void publish(AuditEvent event);
}
