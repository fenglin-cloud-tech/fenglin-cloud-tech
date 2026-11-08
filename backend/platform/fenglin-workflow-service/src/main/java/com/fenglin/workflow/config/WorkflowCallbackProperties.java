package com.fenglin.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "fenglin.workflow.callback")
public record WorkflowCallbackProperties(
        Duration connectTimeout,
        Duration readTimeout,
        int maxRetry,
        Duration retryBackoff) {

    public WorkflowCallbackProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        maxRetry = maxRetry < 0 ? 3 : maxRetry;
        retryBackoff = retryBackoff == null ? Duration.ofMillis(200) : retryBackoff;
    }
}
