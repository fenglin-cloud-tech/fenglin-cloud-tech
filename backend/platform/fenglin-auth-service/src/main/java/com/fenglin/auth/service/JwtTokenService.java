package com.fenglin.auth.service;

import com.fenglin.auth.config.AuthProperties;
import com.fenglin.auth.entity.TenantEntity;
import com.fenglin.auth.entity.UserEntity;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenService {

    public static final String USE_ACCESS = "access";
    public static final String USE_REFRESH = "refresh";
    public static final String USE_ID = "id";

    private final JwtKeyProvider keyProvider;
    private final AuthProperties properties;

    public JwtTokenService(JwtKeyProvider keyProvider, AuthProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties;
    }

    /**
     * PLAT-10：access token 只携带 roles + pv，不再携带全量权限点。
     * permissions 非空时才写入 claim，仅用于灰度回滚（fenglin.auth.jwt.include-permissions=true）。
     */
    public IssuedToken accessToken(TenantEntity tenant, UserEntity user, String clientType,
                                   String sessionId, List<String> roles, long permissionVersion,
                                   List<String> permissions, Instant now) {
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());
        String jti = UUID.randomUUID().toString().replace("-", "");
        JWTClaimsSet.Builder builder = baseClaims(tenant, user, clientType, sessionId, now, expiresAt)
                .jwtID(jti)
                .claim("token_use", USE_ACCESS)
                .claim("scope", "openid profile")
                .claim("roles", roles == null ? List.of() : roles)
                .claim("pv", permissionVersion)
                .claim("tv", user.getTokenVersion() == null ? 0L : user.getTokenVersion());
        if (permissions != null) {
            builder.claim("permissions", permissions);
        }
        return new IssuedToken(keyProvider.sign(builder.build()), jti, expiresAt);
    }

    /**
     * OPEN-06：平台跨租户只读切换令牌。
     * <p>tenant_id 指向目标租户；platform_ops/act_home_tenant_id/switch_id 供网关识别并写审计；
     * 不签发 refresh token，且网关只放行安全方法 + 平台治理域路径。</p>
     */
    public IssuedToken platformSwitchToken(TenantEntity targetTenant, UserEntity operator,
                                           long homeTenantId, String switchId, Instant now) {
        Instant expiresAt = now.plus(properties.getPlatformSwitch().getTtl());
        String jti = UUID.randomUUID().toString().replace("-", "");
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(operator.getId()))
                .audience(List.of("ADMIN"))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(jti)
                .claim("token_use", USE_ACCESS)
                .claim("scope", "openid profile")
                .claim("tenant_id", targetTenant.getId())
                .claim("tenant_code", targetTenant.getCode())
                .claim("client_type", "ADMIN")
                .claim("employee_no", operator.getEmployeeNo())
                .claim("roles", List.of("PLATFORM_OPS"))
                .claim("pv", operator.getPermissionVersion() == null ? 0L : operator.getPermissionVersion())
                .claim("tv", operator.getTokenVersion() == null ? 0L : operator.getTokenVersion())
                .claim("platform_ops", true)
                .claim("act_home_tenant_id", homeTenantId)
                .claim("switch_id", switchId)
                .claim("username", operator.getUsername())
                .claim("name", displayName(operator))
                .build();
        return new IssuedToken(keyProvider.sign(claims), jti, expiresAt);
    }

    public IssuedToken refreshToken(TenantEntity tenant, UserEntity user, String clientType,
                                    String sessionId, Instant now) {        Instant expiresAt = now.plus(properties.getRefreshTokenTtl());
        String jti = UUID.randomUUID().toString().replace("-", "");
        JWTClaimsSet claims = baseClaims(tenant, user, clientType, sessionId, now, expiresAt)
                .jwtID(jti)
                .claim("token_use", USE_REFRESH)
                .build();
        return new IssuedToken(keyProvider.sign(claims), jti, expiresAt);
    }

    public IssuedToken idToken(TenantEntity tenant, UserEntity user, String clientType,
                               String sessionId, Instant now) {
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());
        String jti = UUID.randomUUID().toString().replace("-", "");
        JWTClaimsSet claims = baseClaims(tenant, user, clientType, sessionId, now, expiresAt)
                .jwtID(jti)
                .claim("token_use", USE_ID)
                .claim("name", displayName(user))
                .claim("auth_time", now.getEpochSecond())
                .build();
        return new IssuedToken(keyProvider.sign(claims), jti, expiresAt);
    }

    private JWTClaimsSet.Builder baseClaims(TenantEntity tenant, UserEntity user, String clientType,
                                            String sessionId, Instant now, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .audience(List.of(clientType == null ? "PORTAL" : clientType))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("tenant_id", tenant.getId())
                .claim("tenant_code", tenant.getCode())
                .claim("client_type", clientType)
                .claim("employee_no", user.getEmployeeNo())
                .claim("sid", sessionId);
    }

    private String displayName(UserEntity user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }
}
