package com.fenglin.common.mq;

public interface EventPublisher {

    <T> void publish(EventEnvelope<T> event);
}
