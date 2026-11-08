package com.fenglin.common.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "fenglin.audit", name = "endpoint")
    public AuditSink httpAuditSink(@Value("${fenglin.audit.endpoint}") String endpoint) {
        return new HttpAuditSink(endpoint);
    }

    @Bean
    @ConditionalOnMissingBean(AuditSink.class)
    public AuditSink auditSink() {
        return new Slf4jAuditSink();
    }
}
