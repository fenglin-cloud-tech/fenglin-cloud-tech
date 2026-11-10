package com.fenglin.gateway.filter;

import com.fenglin.gateway.config.GatewayProperties;
import com.fenglin.gateway.security.GatewayPrincipal;
import com.fenglin.gateway.security.JwtVerifier;
import com.fenglin.gateway.security.PermissionResolver;
import com.fenglin.gateway.security.RevocationChecker;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayFilterTest {

    private JwtVerifier verifier;
    private AuthGlobalFilter authFilter;

    @BeforeEach
    void setUp() {
        verifier = mock(JwtVerifier.class);
        RevocationChecker revocationChecker = mock(RevocationChecker.class);
        when(revocationChecker.isRevoked(any(), anyLong())).thenReturn(Mono.just(false));
        PermissionResolver permissionResolver = mock(PermissionResolver.class);
        when(permissionResolver.useClaim(any())).thenReturn(true);
        authFilter = new AuthGlobalFilter(verifier, revocationChecker, permissionResolver);
    }

    @Test
    void missingTokenReturns401() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/hub/v1/admin/users"));
        AtomicBoolean downstream = new AtomicBoolean();
        authFilter.filter(exchange, chain(downstream)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("FL-COMMON-0401");
        assertThat(downstream).isFalse();
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        when(verifier.verify(anyString())).thenThrow(new java.text.ParseException("bad token", 0));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/hub/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer broken"));
        AtomicBoolean downstream = new AtomicBoolean();
        authFilter.filter(exchange, chain(downstream)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(downstream).isFalse();
    }

    @Test
    void loginIsPublic() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/hub/v1/sso/login"));
        AtomicBoolean downstream = new AtomicBoolean();
        authFilter.filter(exchange, chain(downstream)).block();

        assertThat(downstream).isTrue();
    }

    @Test
    void validTokenInjectsTrustedHeaders() throws Exception {
        when(verifier.verify("good-token")).thenReturn(claims("access", "ADMIN"));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/hub/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                .header("X-Permissions", "spoofed:permission"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = filtered -> {
            forwarded.set(filtered);
            return Mono.empty();
        };
        authFilter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("1001");
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("2001");
        assertThat(headers.getFirst("X-Client-Type")).isEqualTo("ADMIN");
        assertThat(headers.getFirst("X-Permissions")).isEqualTo("hub:iam:user:read,hub:audit:read");
        assertThat(headers.getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    void tenantHeaderMustMatchToken() throws Exception {
        when(verifier.verify("good-token")).thenReturn(claims("access", "ADMIN"));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/hub/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                .header("X-Tenant-Id", "1002"));
        AtomicBoolean downstream = new AtomicBoolean();
        authFilter.filter(exchange, chain(downstream)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(downstream).isFalse();
    }

    @Test
    void portalTokenCannotAccessAdminApi() throws Exception {
        when(verifier.verify("portal-token")).thenReturn(claims("access", "PORTAL"));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/hub/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer portal-token"));
        AtomicBoolean downstream = new AtomicBoolean();
        authFilter.filter(exchange, chain(downstream)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(downstream).isFalse();
    }

    @Test
    void refreshTokenIsRejectedAsAccessToken() throws Exception {
        when(verifier.verify("refresh-token")).thenReturn(claims("refresh", "ADMIN"));
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/hub/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer refresh-token"));
        AtomicBoolean downstream = new AtomicBoolean();
        authFilter.filter(exchange, chain(downstream)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(downstream).isFalse();
    }

    @Test
    void rateLimitReturns429AfterThreshold() {
        GatewayProperties properties = new GatewayProperties();
        properties.getRateLimit().setReadQps(2);
        RateLimitGlobalFilter rateLimit = new RateLimitGlobalFilter(properties);
        AtomicBoolean downstream = new AtomicBoolean();
        GatewayFilterChain chain = filtered -> {
            downstream.set(true);
            return Mono.empty();
        };

        assertThat(run(rateLimit, chain, "/api/hub/v1/admin/users", 1001L).getResponse().getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(run(rateLimit, chain, "/api/hub/v1/admin/users", 1001L).getResponse().getStatusCode())
                .isEqualTo(HttpStatus.OK);
        MockServerWebExchange limited = run(rateLimit, chain, "/api/hub/v1/admin/users", 1001L);
        assertThat(limited.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getResponse().getBodyAsString().block()).contains("FL-COMMON-0429");

        // 其他租户独立计数
        assertThat(run(rateLimit, chain, "/api/hub/v1/admin/users", 1002L).getResponse().getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void grayRuleAddsDownstreamTag() {
        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.GrayRule rule = new GatewayProperties.GrayRule();
        rule.setMatchHeader("X-Gray-Tag");
        rule.setMatchValue("iam-canary");
        rule.setTargetTag("iam-canary");
        properties.getGray().getRules().add(rule);
        GrayGlobalFilter grayFilter = new GrayGlobalFilter(properties);

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/hub/v1/admin/users")
                .header("X-Gray-Tag", "iam-canary"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        grayFilter.filter(exchange, filtered -> {
            forwarded.set(filtered);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Gray-Route")).isEqualTo("iam-canary");

        MockServerWebExchange plain = exchange(MockServerHttpRequest.get("/api/hub/v1/admin/users"));
        AtomicReference<ServerWebExchange> plainForwarded = new AtomicReference<>();
        grayFilter.filter(plain, filtered -> {
            plainForwarded.set(filtered);
            return Mono.empty();
        }).block();
        assertThat(plainForwarded.get().getRequest().getHeaders().getFirst("X-Gray-Route")).isNull();
    }

    private MockServerWebExchange run(RateLimitGlobalFilter filter, GatewayFilterChain chain,
                                      String path, long tenantId) {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get(path));
        exchange.getAttributes().put(AuthGlobalFilter.PRINCIPAL_ATTRIBUTE,
                new GatewayPrincipal(tenantId, 2001, "fenglin", "ADMIN", List.of(), List.of()));
        filter.filter(exchange, chain).block();
        if (exchange.getResponse().getStatusCode() == null) {
            exchange.getResponse().setStatusCode(HttpStatus.OK);
        }
        return exchange;
    }

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> builder) {
        return MockServerWebExchange.from(builder.build());
    }

    private GatewayFilterChain chain(AtomicBoolean downstream) {
        return filtered -> {
            downstream.set(true);
            return Mono.empty();
        };
    }

    private JWTClaimsSet claims(String tokenUse, String clientType) {
        return new JWTClaimsSet.Builder()
                .subject("2001")
                .issuer("https://hub.test")
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .claim("token_use", tokenUse)
                .claim("tenant_id", 1001L)
                .claim("tenant_code", "fenglin")
                .claim("client_type", clientType)
                .claim("roles", List.of("TENANT_ADMIN"))
                .claim("permissions", List.of("hub:iam:user:read", "hub:audit:read"))
                .build();
    }
}
