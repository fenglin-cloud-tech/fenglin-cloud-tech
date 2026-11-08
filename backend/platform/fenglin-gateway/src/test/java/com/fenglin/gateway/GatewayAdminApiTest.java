package com.fenglin.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.gateway.security.JwtVerifier;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关管理 API 集成测试：本地端点不走 GlobalFilter，验证 WebFilter 内的验签与权限校验。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayAdminApiTest {

    private static final String ISSUER = "https://hub.fenglincloud.com";
    private static final String READ_PERMISSION = "hub:gw:route:read";
    private static final RSAKey RSA_KEY;

    static {
        try {
            RSA_KEY = new RSAKeyGenerator(2048).keyID("gateway-test").generate();
        } catch (Exception e) {
            throw new IllegalStateException("测试 RSA 密钥生成失败", e);
        }
    }

    @TestConfiguration
    static class JwtTestConfig {

        @Bean
        @Primary
        JwtVerifier testJwtVerifier() {
            return new JwtVerifier(new ImmutableJWKSet<SecurityContext>(
                    new JWKSet(RSA_KEY.toPublicJWK())), ISSUER);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void configureClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void requiresValidTokenAndPermission() {
        ResponseEntity<String> anonymous = get("/api/hub/v1/admin/gateway/routes", null);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat(anonymous.getBody()).contains("FL-COMMON-0401");

        ResponseEntity<String> noPermission = get("/api/hub/v1/admin/gateway/routes",
                token(List.of("biz:report:view"), "ADMIN"));
        assertThat(noPermission.getStatusCode().value()).isEqualTo(403);
        assertThat(noPermission.getBody()).contains("FL-COMMON-0403");

        ResponseEntity<String> portal = get("/api/hub/v1/admin/gateway/routes",
                token(List.of(READ_PERMISSION), "PORTAL"));
        assertThat(portal.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void exposesRoutesGrayRulesAndRateLimits() throws Exception {
        ResponseEntity<String> routes = get("/api/hub/v1/admin/gateway/routes",
                token(List.of(READ_PERMISSION), "ADMIN"));
        assertThat(routes.getStatusCode().value()).isEqualTo(200);
        JsonNode data = read(routes.getBody()).get("data");
        assertThat(data).hasSizeGreaterThanOrEqualTo(10);
        assertThat(data.toString()).contains("fenglin-iam-service");
        assertThat(data.toString()).contains("/api/hub/v1/admin/report/**");

        ResponseEntity<String> gray = get("/api/hub/v1/admin/gateway/gray-rules",
                token(List.of(READ_PERMISSION), "ADMIN"));
        JsonNode grayData = read(gray.getBody()).get("data");
        assertThat(grayData.get("enabled").asBoolean()).isTrue();
        assertThat(grayData.get("rules").get(0).get("route_id_prefix").asText())
                .isEqualTo("fenglin-iam-service");

        ResponseEntity<String> rateLimits = get("/api/hub/v1/admin/gateway/rate-limits",
                token(List.of(READ_PERMISSION), "ADMIN"));
        JsonNode rateData = read(rateLimits.getBody()).get("data");
        assertThat(rateData.get("login_qps").asInt()).isEqualTo(50);
        assertThat(rateData.get("read_qps").asInt()).isEqualTo(500);
        assertThat(rateData.get("write_qps").asInt()).isEqualTo(100);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange("http://localhost:" + port + path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private String token(List<String> permissions, String clientType) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("1")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("token_use", "access")
                .claim("tenant_id", 1L)
                .claim("user_id", 1L)
                .claim("tenant_code", "T001")
                .claim("client_type", clientType)
                .claim("roles", List.of("admin"))
                .claim("permissions", permissions)
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(), claims);
        try {
            jwt.sign(new RSASSASigner(RSA_KEY));
        } catch (Exception e) {
            throw new IllegalStateException("测试令牌签名失败", e);
        }
        return jwt.serialize();
    }

    private JsonNode read(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
