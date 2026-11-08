package com.fenglin.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fenglin.gateway.config.GatewayProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 令牌撤销检查：调用 auth 内部接口 /internal/v1/auth/revocations/{jti}，结果本地缓存 30s。
 * 该内部接口未在《接口文档》定义（P1 契约缺口），以平台总纲的令牌撤销要求为准。
 * 回源失败或超时按 fail-open 放行并打印 WARN，避免 auth 故障放大为全站不可用。
 */
public class RevocationChecker {

    private static final Logger log = LoggerFactory.getLogger(RevocationChecker.class);

    private final GatewayProperties properties;
    private final WebClient webClient;
    private final Cache<String, Boolean> cache;

    public RevocationChecker(GatewayProperties properties, WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getRevocation().getCacheTtl())
                .maximumSize(100_000)
                .build();
    }

    public Mono<Boolean> isRevoked(String jti, long tenantId) {
        if (!properties.getRevocation().isEnabled() || jti == null || jti.isBlank()) {
            return Mono.just(false);
        }
        Boolean cached = cache.getIfPresent(jti);
        if (cached != null) {
            return Mono.just(cached);
        }
        return webClient.get()
                .uri("/internal/v1/auth/revocations/{jti}", jti)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("撤销状态响应为空")))
                .timeout(properties.getRevocation().getTimeout())
                .map(node -> node.path("data").path("revoked").asBoolean(false))
                .doOnNext(revoked -> cache.put(jti, revoked))
                .onErrorResume(ex -> {
                    log.warn("令牌撤销状态查询失败，按未撤销放行（fail-open） jti={} 原因={}", jti, ex.toString());
                    cache.put(jti, false);
                    return Mono.just(false);
                });
    }
}
