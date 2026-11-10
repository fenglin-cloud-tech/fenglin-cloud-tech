package com.fenglin.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.gateway.config.GatewayProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PLAT-10 权限解析器：JWT 只携带 roles + pv，权限点在网关按 (tenant,user,pv) 解析。
 * <p>
 * 读取顺序：L1 Caffeine(60s) → L2 Redis(10min，超过 60s 的条目仍需回源校验 pv) → auth 内部接口。
 * 降级策略（与设计文档一致）：
 * <ul>
 *   <li>Redis 不可用：跳过 L2，L1 TTL 降为 10s，继续直连 auth 回源；</li>
 *   <li>auth 不可用：抛出 {@link PermissionResolutionException}，由过滤器 fail-close 返回 503。</li>
 * </ul>
 * 权限变更后 ≤60s 生效：L1 最多存活 60s；L2 条目的可信窗口同为 60s，过期后必须回源拿权威 pv。
 */
public class PermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(PermissionResolver.class);
    private static final String L2_PREFIX = "fenglin:perm:";

    private final GatewayProperties properties;
    private final WebClient webClient;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Cache<String, ResolvedPermissions> l1;
    private final Cache<String, ResolvedPermissions> degradedL1;
    private volatile boolean degraded;

    public PermissionResolver(GatewayProperties properties, WebClient webClient,
                              ReactiveStringRedisTemplate redis, ObjectMapper objectMapper) {
        this.properties = properties;
        this.webClient = webClient;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.l1 = Caffeine.newBuilder()
                .expireAfterWrite(properties.getPermissionCache().getL1Ttl())
                .maximumSize(properties.getPermissionCache().getMaxSize())
                .build();
        this.degradedL1 = Caffeine.newBuilder()
                .expireAfterWrite(properties.getPermissionCache().getDegradedL1Ttl())
                .maximumSize(properties.getPermissionCache().getMaxSize())
                .build();
        this.degraded = redis == null || !properties.getPermissionCache().isRedisEnabled();
        log.info("权限解析器初始化 source={} redis={} l1={} l2={} degraded={}",
                properties.getPermissionCache().getSource(), redis != null,
                properties.getPermissionCache().getL1Ttl(),
                properties.getPermissionCache().getL2Ttl(), degraded);
    }

    /** 旧令牌带 permissions claim 且未切到 resolver 模式时，直接使用 claim，行为与瘦身前一致。 */
    public boolean useClaim(JWTClaimsSet claims) {
        return "jwt".equalsIgnoreCase(properties.getPermissionCache().getSource())
                && claims.getClaim("permissions") != null;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public Mono<ResolvedPermissions> resolve(long tenantId, long userId, Long tokenVersion) {
        String key = tenantId + ":" + userId + ":" + (tokenVersion == null ? "latest" : tokenVersion);
        ResolvedPermissions cached = activeCache().getIfPresent(key);
        if (cached != null) {
            return Mono.just(cached);
        }
        return readL2(key)
                .flatMap(value -> {
                    long maxAge = activeTtl().toMillis();
                    if (System.currentTimeMillis() - value.resolvedAt() > maxAge) {
                        return Mono.empty();
                    }
                    activeCache().put(key, value);
                    return Mono.just(value);
                })
                .switchIfEmpty(Mono.defer(() -> fetch(tenantId, userId, tokenVersion)
                        .flatMap(value -> {
                            activeCache().put(key, value);
                            return writeL2(key, value).thenReturn(value);
                        })));
    }

    private Mono<ResolvedPermissions> fetch(long tenantId, long userId, Long tokenVersion) {
        return webClient.get()
                .uri(builder -> {
                    var path = builder.path("/internal/v1/auth/principals/{userId}/permissions");
                    if (tokenVersion != null) {
                        path = path.queryParam("pv", tokenVersion);
                    }
                    return path.build(userId);
                })
                .header("X-Tenant-Id", String.valueOf(tenantId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(properties.getPermissionCache().getTimeout())
                .switchIfEmpty(Mono.error(new PermissionResolutionException("权限解析响应为空")))
                .map(node -> toResolved(node.path("data")))
                .onErrorMap(ex -> ex instanceof PermissionResolutionException ? ex
                        : new PermissionResolutionException("权限解析回源失败: " + ex.getMessage(), ex))
                .doOnError(ex -> log.warn("权限解析失败（fail-close） tenant={} user={} 原因={}",
                        tenantId, userId, ex.toString()));
    }

    private Mono<ResolvedPermissions> readL2(String key) {
        if (redis == null || !properties.getPermissionCache().isRedisEnabled()) {
            return Mono.empty();
        }
        return redis.opsForValue().get(L2_PREFIX + key)
                .doOnNext(value -> markAvailable())
                .mapNotNull(this::parseL2)
                .onErrorResume(ex -> {
                    markDegraded(key, ex);
                    return Mono.empty();
                });
    }

    private Mono<Void> writeL2(String key, ResolvedPermissions value) {
        if (redis == null || !properties.getPermissionCache().isRedisEnabled()) {
            return Mono.empty();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pv", value.permissionVersion());
        payload.put("roles", value.roles());
        payload.put("permissions", value.permissions());
        payload.put("resolved_at", value.resolvedAt());
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return Mono.empty();
        }
        return redis.opsForValue()
                .set(L2_PREFIX + key, json, properties.getPermissionCache().getL2Ttl())
                .doOnNext(ok -> markAvailable())
                .onErrorResume(ex -> {
                    markDegraded(key, ex);
                    return Mono.empty();
                })
                .then();
    }

    private ResolvedPermissions parseL2(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new ResolvedPermissions(node.path("pv").asLong(0),
                    strings(node.path("roles")), strings(node.path("permissions")),
                    node.path("resolved_at").asLong(0));
        } catch (Exception ex) {
            log.warn("权限 L2 缓存解析失败，按未命中处理 key={} 原因={}", keyForLog(json), ex.toString());
            return null;
        }
    }

    private ResolvedPermissions toResolved(JsonNode data) {
        return new ResolvedPermissions(data.path("permission_version").asLong(0),
                strings(data.path("roles")), strings(data.path("permissions")),
                System.currentTimeMillis());
    }

    private List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(node.size());
        node.forEach(item -> values.add(item.asText()));
        return List.copyOf(values);
    }

    private Cache<String, ResolvedPermissions> activeCache() {
        return degraded ? degradedL1 : l1;
    }

    private Duration activeTtl() {
        return degraded ? properties.getPermissionCache().getDegradedL1Ttl()
                : properties.getPermissionCache().getL1Ttl();
    }

    private void markAvailable() {
        if (degraded) {
            degraded = false;
            log.info("权限缓存 Redis 已恢复，L1 TTL 回到 {}",
                    properties.getPermissionCache().getL1Ttl());
        }
    }

    private void markDegraded(String key, Throwable ex) {
        if (!degraded) {
            degraded = true;
            log.warn("权限缓存 Redis 不可用，降级为直连 auth + L1 {} key={} 原因={}",
                    properties.getPermissionCache().getDegradedL1Ttl(), key, ex.toString());
        }
    }

    private String keyForLog(String json) {
        return json == null || json.length() < 32 ? String.valueOf(json) : json.substring(0, 32);
    }
}
