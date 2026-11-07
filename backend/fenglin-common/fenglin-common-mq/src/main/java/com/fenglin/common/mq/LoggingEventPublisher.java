package com.fenglin.common.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public <T> void publish(EventEnvelope<T> event) {
        log.info("publish event id={} type={} tenant={} producer={}",
                event.eventId(), event.eventType(), event.tenantId(), event.producer());
    }
}
