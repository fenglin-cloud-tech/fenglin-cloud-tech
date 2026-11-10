package com.fenglin.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.gateway.config.GatewayProperties;
import com.fenglin.gateway.filter.AuthGlobalFilter;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PLAT-10：网关权限解析（L1 缓存、回源刷新、Redis 降级、fail-close、旧令牌双读）。
 */
class PermissionResolverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void slimTokenIsResolvedFromAuthAndCachedInL1() {
        AtomicInteger calls = new AtomicInteger();
        PermissionResolver resolver = resolver(props -> { }, request -> {
            calls.incrementAndGet();
            assertThat(request.url().getPath())
                    .isEqualTo("/internal/v1/auth/principals/2001/permissions");
            assertThat(request.headers().getFirst("X-Tenant-Id")).isEqualTo("1001");
            assertThat(request.url().getQuery()).isEqualTo("pv=3");
            return response(3, "hub:iam:user:read");
        });

        ResolvedPermissions first = resolver.resolve(1001L, 2001L, 3L).block();
        ResolvedPermissions second = resolver.resolve(1001L, 2001L, 3L).block();

        assertThat(first).isNotNull();
        assertThat(first.permissionVersion()).isEqualTo(3);
        assertThat(first.permissions()).containsExactly("hub:iam:user:read");
        assertThat(second.permissions()).isEqualTo(first.permissions());
        assertThat(calls.get()).as("L1 命中后不再回源").isEqualTo(1);
    }

    @Test
    void staleTokenGetsAuthoritativeVersionFromAuth() {
        GatewayProperties properties = baseProperties();
        properties.getPermissionCache().setL1Ttl(Duration.ofMillis(80));
        properties.getPermissionCache().setDegradedL1Ttl(Duration.ofMillis(80));
        AtomicInteger calls = new AtomicInteger();
        PermissionResolver resolver = resolver(properties, request -> {
            int call = calls.incrementAndGet();
            return call == 1 ? response(3, "old:permission") : response(4, "new:permission");
        });

        assertThat(resolver.resolve(1001L, 2001L, 3L).block().permissions())
                .containsExactly("old:permission");
        sleep(120);
        ResolvedPermissions refreshed = resolver.resolve(1001L, 2001L, 3L).block();
        assertThat(refreshed.permissionVersion()).as("以 auth 权威版本为准").isEqualTo(4);
        assertThat(refreshed.permissions()).containsExactly("new:permission");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void redisFailureDegradesToShortLivedL1() {
        GatewayProperties properties = baseProperties();
        properties.getPermissionCache().setL1Ttl(Duration.ofSeconds(60));
        properties.getPermissionCache().setDegradedL1Ttl(Duration.ofMillis(80));

        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        when(valueOps.get(anyString())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        when(valueOps.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        AtomicInteger calls = new AtomicInteger();
        PermissionResolver resolver = new PermissionResolver(properties,
                client(request -> {
                    calls.incrementAndGet();
                    return response(1, "hub:iam:user:read");
                }), redis, MAPPER);

        resolver.resolve(1001L, 2001L, 1L).block();
        resolver.resolve(1001L, 2001L, 1L).block();
        assertThat(resolver.isDegraded()).isTrue();
        assertThat(calls.get()).as("降级后仍由 L1 10s 缓存兜住").isEqualTo(1);

        sleep(120);
        resolver.resolve(1001L, 2001L, 1L).block();
        assertThat(calls.get()).as("降级 L1 过期后回源 auth").isEqualTo(2);
    }

    @Test
    void authFailureTriggersFailClose503() throws Exception {
        GatewayProperties properties = baseProperties();
        PermissionResolver resolver = new PermissionResolver(properties,
                client(request -> { throw new IllegalStateException("auth down"); }), null, MAPPER);
        JwtVerifier verifier = mock(JwtVerifier.class);
        when(verifier.verify("slim-token")).thenReturn(slimClaims(3L));
        RevocationChecker revocation = mock(RevocationChecker.class);
        when(revocation.isRevoked(any(), anyLong())).thenReturn(Mono.just(false));
        AuthGlobalFilter filter = new AuthGlobalFilter(verifier, revocation, resolver);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/hub/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer slim-token"));
        AtomicBoolean downstream = new AtomicBoolean();
        filter.filter(exchange, filtered -> {
            downstream.set(true);
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("FL-COMMON-0503");
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(downstream).as("fail-close 不得放行下游").isFalse();
    }

    @Test
    void oldTokenClaimWinsAndSkipsResolver() throws Exception {
        GatewayProperties properties = baseProperties();
        AtomicInteger resolverCalls = new AtomicInteger();
        PermissionResolver resolver = new PermissionResolver(properties,
                client(request -> {
                    resolverCalls.incrementAndGet();
                    return response(9, "should-not-be-used");
                }), null, MAPPER);
        JwtVerifier verifier = mock(JwtVerifier.class);
        when(verifier.verify("old-token")).thenReturn(oldClaims());
        RevocationChecker revocation = mock(RevocationChecker.class);
        when(revocation.isRevoked(any(), anyLong())).thenReturn(Mono.just(false));
        AuthGlobalFilter filter = new AuthGlobalFilter(verifier, revocation, resolver);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/hub/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer old-token"));
        AtomicReference<org.springframework.web.server.ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, filtered -> {
            forwarded.set(filtered);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Permissions"))
                .isEqualTo("legacy:one,legacy:two");
        assertThat(resolverCalls.get()).as("旧令牌不触发回源").isZero();
    }

    @Test
    void resolverModeIgnoresClaim() {
        GatewayProperties properties = baseProperties();
        properties.getPermissionCache().setSource("resolver");
        PermissionResolver resolver = resolver(properties, request -> response(5, "from:auth"));

        assertThat(resolver.useClaim(oldClaims())).isFalse();
        assertThat(resolver.resolve(1001L, 2001L, null).block().permissions())
                .containsExactly("from:auth");
    }

    private PermissionResolver resolver(java.util.function.Consumer<GatewayProperties> customizer,
                                        java.util.function.Function<org.springframework.web.reactive.function.client.ClientRequest,
                                                Mono<ClientResponse>> handler) {
        GatewayProperties properties = baseProperties();
        customizer.accept(properties);
        return new PermissionResolver(properties, client(handler), null, MAPPER);
    }

    private PermissionResolver resolver(GatewayProperties properties,
                                        java.util.function.Function<org.springframework.web.reactive.function.client.ClientRequest,
                                                Mono<ClientResponse>> handler) {
        return new PermissionResolver(properties, client(handler), null, MAPPER);
    }

    private PermissionResolver resolver(
            java.util.function.Function<org.springframework.web.reactive.function.client.ClientRequest,
                    Mono<ClientResponse>> handler) {
        return resolver(props -> { }, handler);
    }

    private GatewayProperties baseProperties() {
        GatewayProperties properties = new GatewayProperties();
        properties.getPermissionCache().setTimeout(Duration.ofMillis(500));
        return properties;
    }

    private WebClient client(
            java.util.function.Function<org.springframework.web.reactive.function.client.ClientRequest,
                    Mono<ClientResponse>> handler) {
        return WebClient.builder().exchangeFunction(handler::apply).build();
    }

    private Mono<ClientResponse> response(long pv, String... permissions) {
        String body = "{\"code\":\"0\",\"data\":{\"user_id\":\"2001\",\"permission_version\":" + pv
                + ",\"roles\":[\"TENANT_ADMIN\"],\"permissions\":[\""
                + String.join("\",\"", permissions) + "\"]}}";
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private JWTClaimsSet slimClaims(Long pv) {
        return new JWTClaimsSet.Builder()
                .subject("2001")
                .issuer("https://hub.test")
                .jwtID("jti-slim")
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .claim("token_use", "access")
                .claim("tenant_id", 1001L)
                .claim("tenant_code", "fenglin")
                .claim("client_type", "ADMIN")
                .claim("roles", List.of("TENANT_ADMIN"))
                .claim("pv", pv)
                .build();
    }

    private JWTClaimsSet oldClaims() {
        return new JWTClaimsSet.Builder()
                .subject("2001")
                .issuer("https://hub.test")
                .jwtID("jti-old")
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .claim("token_use", "access")
                .claim("tenant_id", 1001L)
                .claim("tenant_code", "fenglin")
                .claim("client_type", "ADMIN")
                .claim("roles", List.of("TENANT_ADMIN"))
                .claim("permissions", List.of("legacy:one", "legacy:two"))
                .build();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
