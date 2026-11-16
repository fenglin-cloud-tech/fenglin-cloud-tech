package com.fenglin.gateway.filter;

import com.fenglin.gateway.security.GatewayPrincipal;
import com.fenglin.gateway.security.JwtVerifier;
import com.fenglin.gateway.security.PermissionResolutionException;
import com.fenglin.gateway.security.PermissionResolver;
import com.fenglin.gateway.security.PlatformAccessAuditor;
import com.fenglin.gateway.security.PlatformAccessPolicy;
import com.fenglin.gateway.security.ResolvedPermissions;
import com.fenglin.gateway.security.RevocationChecker;
import com.fenglin.gateway.security.TokenVersionChecker;
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
 *
 * <p>OPEN-06 一期：带 {@code platform_ops} 的切换令牌只允许安全方法 + 正向白名单内的平台治理域路径；
 * 每个跨租户请求先写审计（tenant_id=目标租户）再放行，审计不可用时跨租户请求 503。</p>
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthGlobalFilter.class);

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
            "/api/hub/v1/sso/password/forgot",
            "/api/hub/v1/sso/password/reset",
            "/.well-known/");

    private final JwtVerifier jwtVerifier;
    private final RevocationChecker revocationChecker;
    private final TokenVersionChecker tokenVersionChecker;
    private final PermissionResolver permissionResolver;
    private final PlatformAccessAuditor platformAccessAuditor;

    public AuthGlobalFilter(JwtVerifier jwtVerifier, RevocationChecker revocationChecker,
                            TokenVersionChecker tokenVersionChecker, PermissionResolver permissionResolver,
                            PlatformAccessAuditor platformAccessAuditor) {
        this.jwtVerifier = jwtVerifier;
        this.revocationChecker = revocationChecker;
        this.tokenVersionChecker = tokenVersionChecker;
        this.permissionResolver = permissionResolver;
        this.platformAccessAuditor = platformAccessAuditor;
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

        Long tenantId;
        Long userId;
        String clientType;
        String tenantCode;
        Long tokenVersion;
        Long accessTokenVersion;
        List<String> claimRoles;
        List<String> claimPermissions;
        boolean platformOps;
        Long actHomeTenantId;
        String switchId;
        String operatorName;
        try {
            if (!"access".equals(claims.getStringClaim("token_use"))) {
                return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED, "FL-COMMON-0401", "令牌类型不匹配");
            }
            tenantId = claims.getLongClaim("tenant_id");
            userId = claims.getLongClaim("user_id");
            if (userId == null && claims.getSubject() != null) {
                userId = Long.valueOf(claims.getSubject());
            }
            if (tenantId == null || userId == null) {
                return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED, "FL-COMMON-0401", "令牌缺少身份信息");
            }
            clientType = claims.getStringClaim("client_type");
            clientType = clientType == null ? "ADMIN" : clientType;
            tenantCode = claims.getStringClaim("tenant_code");
            tokenVersion = claims.getLongClaim("pv");
            accessTokenVersion = claims.getLongClaim("tv");
            claimRoles = safeList(claims.getStringListClaim("roles"));
            claimPermissions = safeList(claims.getStringListClaim("permissions"));
            platformOps = Boolean.TRUE.equals(claims.getClaim("platform_ops"));
            actHomeTenantId = claims.getLongClaim("act_home_tenant_id");
            switchId = claims.getStringClaim("switch_id");
            operatorName = claims.getStringClaim("name");
        } catch (Exception e) {
            return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED, "FL-COMMON-0401", "令牌声明不合法");
        }

        String tenantHeader = exchange.getRequest().getHeaders().getFirst(HEADER_TENANT_ID);
        if (tenantHeader != null && !tenantHeader.isBlank()
                && !tenantHeader.equals(String.valueOf(tenantId))) {
            return GatewayResponses.error(exchange, HttpStatus.FORBIDDEN, "FL-COMMON-0403", "租户上下文与令牌不一致");
        }
        if ("PORTAL".equals(clientType) && path.startsWith("/api/hub/v1/admin")) {
            return GatewayResponses.error(exchange, HttpStatus.FORBIDDEN, "FL-COMMON-0403", "端别不允许访问管理端接口");
        }

        final Long resolvedTenantId = tenantId;
        final Long resolvedUserId = userId;
        final String resolvedClientType = clientType;
        final String resolvedTenantCode = tenantCode;
        final boolean resolvedPlatformOps = platformOps;
        final Long resolvedHomeTenantId = actHomeTenantId;
        final String resolvedSwitchId = switchId;
        final String resolvedOperatorName = operatorName;
        String jti = claims.getJWTID();
        final long claimedVersion = accessTokenVersion == null ? 0L : accessTokenVersion;
        final String requestId = resolveRequestId(exchange);
        // 切换令牌的 token_version 属于 home 租户；目标租户下查不到该用户，不能按目标租户校验
        final long versionTenantId = resolvedPlatformOps && resolvedHomeTenantId != null
                ? resolvedHomeTenantId : resolvedTenantId;

        if (resolvedPlatformOps) {
            if (!PlatformAccessPolicy.isSafeMethod(exchange.getRequest().getMethod().name())) {
                return denyPlatformRequest(exchange, resolvedTenantId, resolvedUserId, resolvedOperatorName,
                        resolvedHomeTenantId, resolvedSwitchId, requestId,
                        PlatformAccessPolicy.READ_ONLY_MESSAGE, "METHOD_NOT_SAFE");
            }
            if (!PlatformAccessPolicy.isGovernancePath(path)) {
                String message = PlatformAccessPolicy.isBusinessDomainPath(path)
                        ? PlatformAccessPolicy.BUSINESS_DOMAIN_MESSAGE
                        : PlatformAccessPolicy.GOVERNANCE_MESSAGE;
                return denyPlatformRequest(exchange, resolvedTenantId, resolvedUserId, resolvedOperatorName,
                        resolvedHomeTenantId, resolvedSwitchId, requestId, message, "OUT_OF_GOVERNANCE_SCOPE");
            }
        }

        // PLAT-22：先校验用户令牌版本（实时、不缓存），改密/重置后旧 access token 立即失效
        return tokenVersionChecker.current(versionTenantId, userId).flatMap(current -> {
            if (current >= 0 && current != claimedVersion) {
                return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED,
                        "FL-COMMON-0401", "登录状态已失效，请重新登录");
            }
            return revocationChecker.isRevoked(jti, tenantId).flatMap(revoked -> {
                if (Boolean.TRUE.equals(revoked)) {
                    return GatewayResponses.error(exchange, HttpStatus.UNAUTHORIZED, "FL-COMMON-0401", "令牌已撤销");
                }
                Mono<Void> next = resolvePermissions(claims, resolvedTenantId, resolvedUserId, tokenVersion,
                        claimRoles, claimPermissions)
                        .flatMap(resolved -> forward(exchange, chain, new GatewayPrincipal(resolvedTenantId,
                                resolvedUserId, resolvedTenantCode, resolvedClientType,
                                resolved.roles(), resolved.permissions()),
                                resolvedPlatformOps, resolvedHomeTenantId, resolvedSwitchId, requestId))
                        .onErrorResume(PermissionResolutionException.class, ex -> {
                            // fail-close：缓存与 auth 均不可用时拒绝，绝不以空权限放行下游
                            exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
                            return GatewayResponses.error(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                    "FL-COMMON-0503", "权限服务暂不可用，请稍后重试");
                        });
                if (!resolvedPlatformOps) {
                    return next;
                }
                return auditAllow(exchange, resolvedTenantId, resolvedUserId, resolvedOperatorName,
                        resolvedHomeTenantId, resolvedSwitchId, requestId)
                        .then(next)
                        .onErrorResume(AuditUnavailableException.class, ex ->
                                GatewayResponses.error(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                        "FL-COMMON-0503", "审计服务不可用，跨租户访问已阻断"));
            });
        });
    }

    /** 跨租户请求先审计再放行；审计失败阻断为 503，不进入下游。 */
    private Mono<Void> auditAllow(ServerWebExchange exchange, long tenantId, long userId, String operatorName,
                                  Long homeTenantId, String switchId, String requestId) {
        return platformAccessAuditor.record(tenantId, userId, operatorName,
                        homeTenantId == null ? tenantId : homeTenantId, switchId, "ALLOW",
                        exchange.getRequest(), requestId)
                .onErrorResume(ex -> {
                    log.error("OPEN-06 跨租户审计写入失败，已阻断请求 tenant={} user={} path={} 原因={}",
                            tenantId, userId, exchange.getRequest().getURI().getPath(), ex.toString());
                    return Mono.error(new AuditUnavailableException(ex));
                });
    }

    /** 越界请求同样写审计；审计不可用时优先返回 503。 */
    private Mono<Void> denyPlatformRequest(ServerWebExchange exchange, long tenantId, long userId,
                                           String operatorName, Long homeTenantId, String switchId,
                                           String requestId, String message, String reason) {
        return platformAccessAuditor.record(tenantId, userId, operatorName,
                        homeTenantId == null ? tenantId : homeTenantId, switchId, "DENY",
                        exchange.getRequest(), requestId)
                .onErrorResume(ex -> {
                    log.error("OPEN-06 跨租户审计写入失败，越界请求未放行 tenant={} user={} path={} 原因={}",
                            tenantId, userId, exchange.getRequest().getURI().getPath(), ex.toString());
                    return Mono.error(new AuditUnavailableException(ex));
                })
                .then(GatewayResponses.error(exchange, HttpStatus.FORBIDDEN, "FL-COMMON-0403", message))
                .onErrorResume(AuditUnavailableException.class, ex ->
                        GatewayResponses.error(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "FL-COMMON-0503", "审计服务不可用，跨租户访问已阻断"))
                .doOnSuccess(ignored -> log.warn(
                        "OPEN-06 拒绝跨租户请求 reason={} tenant={} user={} method={} path={}",
                        reason, tenantId, userId, exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getPath()));
    }

    /**
     * 兼容期双读：旧令牌带 permissions claim 时直接使用（行为与瘦身前一致）；
     * 新令牌只有 roles + pv，回源 auth 解析权限点。两种令牌在过渡期都可用。
     */
    private Mono<ResolvedPermissions> resolvePermissions(JWTClaimsSet claims, long tenantId, long userId,
                                                         Long tokenVersion, List<String> claimRoles,
                                                         List<String> claimPermissions) {
        if (permissionResolver.useClaim(claims)) {
            return Mono.just(new ResolvedPermissions(0L, claimRoles, claimPermissions,
                    System.currentTimeMillis()));
        }
        return permissionResolver.resolve(tenantId, userId, tokenVersion);
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain,
                               GatewayPrincipal principal, boolean platformOps,
                               Long homeTenantId, String switchId, String requestId) {
        if (log.isDebugEnabled()) {
            String injected = String.join(",", principal.permissions());
            log.debug("inject X-Permissions tenant={} user={} count={} bytes={}",
                    principal.tenantId(), principal.userId(), principal.permissions().size(),
                    injected.length());
        }
        ServerHttpRequest mutated = exchange.getRequest().mutate().headers(headers -> {
            headers.set(HEADER_TENANT_ID, String.valueOf(principal.tenantId()));
            headers.set(HEADER_USER_ID, String.valueOf(principal.userId()));
            headers.set(HEADER_CLIENT_TYPE, principal.clientType());
            headers.set(HEADER_PERMISSIONS, String.join(",", principal.permissions()));
            headers.set("X-Request-Id", requestId);
            if (platformOps) {
                headers.set("X-Platform-Ops", "on");
                headers.set("X-Platform-Operator-Id", String.valueOf(principal.userId()));
                if (homeTenantId != null) {
                    headers.set("X-Platform-Home-Tenant-Id", String.valueOf(homeTenantId));
                }
                if (switchId != null) {
                    headers.set("X-Tenant-Switch-Id", switchId);
                }
                headers.set("X-Read-Only-Mode", "PLATFORM_GOVERNANCE");
            }
        }).build();

        if (log.isDebugEnabled()) {
            int total = 0;
            for (var entry : mutated.getHeaders().entrySet()) {
                total += entry.getKey().length() + 2;
                for (String value : entry.getValue()) {
                    total += value.length() + 2;
                }
            }
            log.debug("downstream headers total bytes={}", total);
        }
        exchange.getAttributes().put(PRINCIPAL_ATTRIBUTE, principal);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        return header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
    }

    private boolean isPublic(String path) {
        // 开放 API 使用 AppKey/AppSecret HMAC 验签，由 openapi-service 统一校验，不依赖用户 JWT；
        // 开发者门户 /openapi/portal/** 仍然要求 JWT，以便注入租户与用户身份。
        if (path.startsWith("/openapi/") && !path.startsWith("/openapi/portal/")) {
            return true;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /** 审计不可用哨兵：仅用于把跨租户请求映射为 503。 */
    private static final class AuditUnavailableException extends RuntimeException {
        private AuditUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}
