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

    public IssuedToken accessToken(TenantEntity tenant, UserEntity user, String clientType,
                                   String sessionId, List<String> permissions, Instant now) {
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());
        String jti = UUID.randomUUID().toString().replace("-", "");
        JWTClaimsSet claims = baseClaims(tenant, user, clientType, sessionId, now, expiresAt)
                .jwtID(jti)
                .claim("token_use", USE_ACCESS)
                .claim("scope", "openid profile")
                .claim("permissions", permissions)
                .build();
        return new IssuedToken(keyProvider.sign(claims), jti, expiresAt);
    }

    public IssuedToken refreshToken(TenantEntity tenant, UserEntity user, String clientType,
                                    String sessionId, Instant now) {
        Instant expiresAt = now.plus(properties.getRefreshTokenTtl());
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
