package com.fenglin.common.mq;

import java.time.Instant;
import java.util.UUID;

/**
 * 统一事件信封。事件 ID 同时作为消费端幂等键。
 */
public record EventEnvelope<T>(String eventId, String eventType, int eventVersion, Long tenantId,
                               String producer, Instant occurredAt, T payload) {

    public static <T> EventEnvelope<T> of(String eventType, int eventVersion, Long tenantId,
                                          String producer, T payload) {
        return new EventEnvelope<>(UUID.randomUUID().toString(), eventType, eventVersion, tenantId,
                producer, Instant.now(), payload);
    }
}
