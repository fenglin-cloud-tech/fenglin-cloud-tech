package com.fenglin.gateway.web;

import com.fenglin.gateway.security.GatewayPrincipal;
import com.fenglin.gateway.security.JwtVerifier;
import com.fenglin.gateway.security.PermissionResolutionException;
import com.fenglin.gateway.security.PermissionResolver;
import com.fenglin.gateway.security.ResolvedPermissions;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 网关管理接口鉴权（扩展）：本地端点不经过 Gateway GlobalFilter，
 * 因此必须在 WebFilter 中自行验签并校验权限，不能信任客户端裸传的 X-Permissions。
 */
@Component
public class GatewayAdminAuthFilter implements WebFilter, Ordered {

    public static final String PRINCIPAL_ATTRIBUTE = "fenglin.gateway.admin.principal";
    private static final String ADMIN_PREFIX = "/api/hub/v1/admin/gateway/";
    private static final String READ_PERMISSION = "hub:gw:route:read";

    private final JwtVerifier jwtVerifier;
    private final PermissionResolver permissionResolver;

    public GatewayAdminAuthFilter(JwtVerifier jwtVerifier, PermissionResolver permissionResolver) {
        this.jwtVerifier = jwtVerifier;
        this.permissionResolver = permissionResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(ADMIN_PREFIX)) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED,
                    "FL-COMMON-0401", "未认证或令牌失效");
        }
        JWTClaimsSet claims;
        try {
            claims = jwtVerifier.verify(authorization.substring(7));
        } catch (Exception e) {
            return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED,
                    "FL-COMMON-0401", "未认证或令牌失效");
        }
        return authorize(exchange, chain, claims);
    }

    private Mono<Void> authorize(ServerWebExchange exchange, WebFilterChain chain,
                                 JWTClaimsSet claims) {
        try {
            if (!"access".equals(claims.getStringClaim("token_use"))) {
                return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED,
                        "FL-COMMON-0401", "令牌类型不匹配");
            }
            String clientType = claims.getStringClaim("client_type");
            if ("PORTAL".equals(clientType)) {
                return GatewayResponses.error(exchange, HttpStatus.FORBIDDEN,
                        "FL-COMMON-0403", "端别不允许访问管理端接口");
            }
            Long tenantId = claims.getLongClaim("tenant_id");
            Long userId = claims.getLongClaim("user_id");
            if (tenantId == null || userId == null) {
                return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED,
                        "FL-COMMON-0401", "令牌缺少身份信息");
            }
            List<String> roles = claims.getStringListClaim("roles");
            List<String> claimPermissions = claims.getStringListClaim("permissions");
            String tenantCode = claims.getStringClaim("tenant_code");
            Long tokenVersion = claims.getLongClaim("pv");
            return resolvePermissions(claims, tenantId, userId, tokenVersion, roles, claimPermissions)
                    .flatMap(resolved -> {
                        if (!resolved.permissions().contains(READ_PERMISSION)) {
                            return GatewayResponses.error(exchange, HttpStatus.FORBIDDEN,
                                    "FL-COMMON-0403", "缺少网关路由查看权限");
                        }
                        exchange.getAttributes().put(PRINCIPAL_ATTRIBUTE, new GatewayPrincipal(tenantId,
                                userId, tenantCode,
                                clientType == null ? "ADMIN" : clientType,
                                resolved.roles().isEmpty() && roles != null ? roles : resolved.roles(),
                                resolved.permissions()));
                        return chain.filter(exchange);
                    })
                    .onErrorResume(PermissionResolutionException.class, ex -> {
                        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
                        return GatewayResponses.error(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "FL-COMMON-0503", "权限服务暂不可用，请稍后重试");
                    });
        } catch (java.text.ParseException e) {
            return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED,
                    "FL-COMMON-0401", "令牌声明解析失败");
        }
    }

    private Mono<ResolvedPermissions> resolvePermissions(JWTClaimsSet claims, long tenantId, long userId,
                                                        Long tokenVersion, List<String> roles,
                                                        List<String> claimPermissions) {
        if (permissionResolver.useClaim(claims)) {
            return Mono.just(new ResolvedPermissions(0L, roles, claimPermissions,
                    System.currentTimeMillis()));
        }
        return permissionResolver.resolve(tenantId, userId, tokenVersion);
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
