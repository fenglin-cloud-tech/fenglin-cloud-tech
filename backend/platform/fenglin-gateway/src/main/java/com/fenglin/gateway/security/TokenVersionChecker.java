package com.fenglin.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * PLAT-22 用户令牌版本校验：access token 的 {@code tv} 必须与 auth 的权威 token_version 一致。
 * 不做本地缓存，保证“改密/重置后旧 access token 立即 401”；auth 不可用时返回 -1（未知）按 fail-open 放行并告警，
 * 与既有撤销链路保持一致的降级策略。
 */
public class TokenVersionChecker {

    private static final Logger log = LoggerFactory.getLogger(TokenVersionChecker.class);

    private final WebClient webClient;
    private final Duration timeout;

    public TokenVersionChecker(WebClient webClient, Duration timeout) {
        this.webClient = webClient;
        this.timeout = timeout;
    }

    /** @return 权威 token_version；-1 表示无法获取（fail-open）。 */
    public Mono<Long> current(long tenantId, long userId) {
        return webClient.get()
                .uri("/internal/v1/auth/principals/{userId}/token-version", userId)
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(timeout)
                .map(node -> node.path("data").path("token_version").asLong(-1))
                .onErrorResume(ex -> {
                    log.warn("令牌版本查询失败，按未知放行（fail-open） tenant={} user={} 原因={}",
                            tenantId, userId, ex.toString());
                    return Mono.just(-1L);
                });
    }
}
