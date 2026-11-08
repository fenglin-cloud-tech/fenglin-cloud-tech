package com.fenglin.gateway.filter;

import com.fenglin.gateway.security.GatewayPrincipal;
import com.fenglin.gateway.security.JwtVerifier;
import com.fenglin.gateway.web.GatewayResponses;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * 统一鉴权（F-GW-02/03）：JWT 本地验签、端别校验、租户一致性校验，并把可信身份注入下游请求头。
 * 下游服务不得信任客户端裸传的 X-Tenant-Id / X-Permissions，一律以网关注入为准。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String PRINCIPAL_ATTRIBUTE = "fenglin.gateway.principal";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_CLIENT_TYPE = "X-Client-Type";
    public static final String HEADER_PERMISSIONS = "X-Permissions";

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/actuator/",
            "/api/hub/v1/sso/login",
            "/api/hub/v1/sso/token",
            "/api/hub/v1/sso/token:refresh",
            "/api/hub/v1/sso/token:revoke",
            "/.well-known/");

    private final JwtVerifier jwtVerifier;

    public AuthGlobalFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED, "FL-COMMON-0401", "未认证或令牌失效");
        }

        JWTClaimsSet claims;
        try {
            claims = jwtVerifier.verify(authorization.substring(7));
        } catch (Exception e) {
            return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED, "FL-COMMON-0401", "未认证或令牌失效");
        }

        GatewayPrincipal principal;
        try {
            if (!"access".equals(claims.getStringClaim("token_use"))) {
                return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED, "FL-COMMON-0401", "令牌类型不匹配");
            }
            Long tenantId = claims.getLongClaim("tenant_id");
            Long userId = claims.getLongClaim("user_id");
            if (userId == null && claims.getSubject() != null) {
                userId = Long.valueOf(claims.getSubject());
            }
            if (tenantId == null || userId == null) {
                return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED, "FL-COMMON-0401", "令牌缺少身份信息");
            }
            String clientType = claims.getStringClaim("client_type");
            principal = new GatewayPrincipal(tenantId, userId, claims.getStringClaim("tenant_code"),
                    clientType == null ? "ADMIN" : clientType,
                    safeList(claims.getStringListClaim("roles")),
                    safeList(claims.getStringListClaim("permissions")));
        } catch (Exception e) {
            return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED, "FL-COMMON-0401", "令牌声明不合法");
        }

        String tenantHeader = exchange.getRequest().getHeaders().getFirst(HEADER_TENANT_ID);
        if (tenantHeader != null && !tenantHeader.isBlank()
                && !tenantHeader.equals(String.valueOf(principal.tenantId()))) {
            return GatewayResponses.error(exchange, HttpStatus.FORBIDDEN, "FL-COMMON-0403", "租户上下文与令牌不一致");
        }
        if ("PORTAL".equals(principal.clientType()) && path.startsWith("/api/hub/v1/admin")) {
            return GatewayResponses.error(exchange, HttpStatus.FORBIDDEN, "FL-COMMON-0403", "端别不允许访问管理端接口");
        }

        ServerHttpRequest mutated = exchange.getRequest().mutate().headers(headers -> {
            headers.set(HEADER_TENANT_ID, String.valueOf(principal.tenantId()));
            headers.set(HEADER_USER_ID, String.valueOf(principal.userId()));
            headers.set(HEADER_CLIENT_TYPE, principal.clientType());
            headers.set(HEADER_PERMISSIONS, String.join(",", principal.permissions()));
            if (!headers.containsKey("X-Request-Id")) {
                headers.set("X-Request-Id", UUID.randomUUID().toString());
            }
        }).build();

        exchange.getAttributes().put(PRINCIPAL_ATTRIBUTE, principal);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isPublic(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
