package com.fenglin.gateway.security;

import com.fenglin.gateway.config.GatewayProperties;
import com.fenglin.gateway.filter.AuthGlobalFilter;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A1：网关撤销检查（缓存 TTL、fail-open、401 拦截）。
 */
class GatewayRevocationTest {

    @Test
    void revocationResultIsCachedUntilTtlExpires() throws Exception {
        AtomicBoolean revoked = new AtomicBoolean(false);
        AtomicInteger calls = new AtomicInteger();
        WebClient client = WebClient.builder()
                .baseUrl("http://auth-stub")
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    assertThat(request.url().getPath()).isEqualTo("/internal/v1/auth/revocations/jti-1");
                    assertThat(request.headers().getFirst("X-Tenant-Id")).isEqualTo("9001");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"code\":\"0\",\"data\":{\"revoked\":" + revoked.get() + "}}")
                            .build());
                })
                .build();
        GatewayProperties properties = new GatewayProperties();
        properties.getRevocation().setCacheTtl(Duration.ofMillis(200));
        RevocationChecker checker = new RevocationChecker(properties, client);

        assertThat(checker.isRevoked("jti-1", 9001L).block()).isFalse();
        revoked.set(true);
        assertThat(checker.isRevoked("jti-1", 9001L).block()).as("TTL 内命中缓存").isFalse();

        Thread.sleep(260);

        assertThat(checker.isRevoked("jti-1", 9001L).block()).as("TTL 过期后回源拿到已撤销").isTrue();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void authFailureFailsOpen() {
        GatewayProperties properties = new GatewayProperties();
        WebClient failing = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new IllegalStateException("auth down")))
                .build();

        assertThat(new RevocationChecker(properties, failing).isRevoked("jti-2", 9001L).block()).isFalse();
    }

    @Test
    void authTimeoutFailsOpen() {
        GatewayProperties properties = new GatewayProperties();
        properties.getRevocation().setTimeout(Duration.ofMillis(50));
        WebClient hanging = WebClient.builder()
                .exchangeFunction(request -> Mono.never())
                .build();

        assertThat(new RevocationChecker(properties, hanging).isRevoked("jti-3", 9001L).block()).isFalse();
    }

    @Test
    void disabledSwitchSkipsRemoteCall() {
        GatewayProperties properties = new GatewayProperties();
        properties.getRevocation().setEnabled(false);
        AtomicInteger calls = new AtomicInteger();
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.never();
                })
                .build();

        assertThat(new RevocationChecker(properties, client).isRevoked("jti-4", 9001L).block()).isFalse();
        assertThat(calls.get()).isZero();
    }

    @Test
    void revokedTokenIsRejectedWith401() throws Exception {
        JwtVerifier verifier = mock(JwtVerifier.class);
        when(verifier.verify("revoked-token")).thenReturn(claims("jti-9"));
        RevocationChecker checker = mock(RevocationChecker.class);
        when(checker.isRevoked("jti-9", 1001L)).thenReturn(Mono.just(true));
        AuthGlobalFilter filter = new AuthGlobalFilter(verifier, checker);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/hub/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer revoked-token"));
        AtomicBoolean downstream = new AtomicBoolean();
        filter.filter(exchange, filtered -> {
            downstream.set(true);
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("FL-COMMON-0401");
        assertThat(downstream).isFalse();
    }

    private JWTClaimsSet claims(String jti) {
        return new JWTClaimsSet.Builder()
                .subject("2001")
                .issuer("https://hub.test")
                .jwtID(jti)
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .claim("token_use", "access")
                .claim("tenant_id", 1001L)
                .claim("tenant_code", "fenglin")
                .claim("client_type", "ADMIN")
                .claim("roles", List.of("TENANT_ADMIN"))
                .claim("permissions", List.of("hub:audit:read"))
                .build();
    }
}

