package com.fenglin.auth.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fenglin.auth.config.AuthErrorCode;
import com.fenglin.auth.config.AuthProperties;
import com.fenglin.auth.entity.AccountEntity;
import com.fenglin.auth.entity.LoginSessionEntity;
import com.fenglin.auth.entity.RefreshTokenEntity;
import com.fenglin.auth.entity.RevocationEntity;
import com.fenglin.auth.entity.TenantEntity;
import com.fenglin.auth.entity.UserEntity;
import com.fenglin.auth.mapper.AccountMapper;
import com.fenglin.auth.mapper.LoginSessionMapper;
import com.fenglin.auth.mapper.PermissionQueryMapper;
import com.fenglin.auth.mapper.RefreshTokenMapper;
import com.fenglin.auth.mapper.RevocationMapper;
import com.fenglin.auth.mapper.TenantMapper;
import com.fenglin.auth.mapper.UserMapper;
import com.fenglin.auth.web.dto.LoginRequest;
import com.fenglin.auth.web.dto.LogoutPayload;
import com.fenglin.auth.web.dto.PrincipalPermissionPayload;
import com.fenglin.auth.web.dto.TokenPayload;
import com.fenglin.auth.web.dto.UserInfoPayload;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.security.PasswordPolicy;
import com.fenglin.common.tenant.TenantContext;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String SESSION_ONLINE = "ONLINE";
    private static final String SESSION_LOGOUT = "LOGOUT";
    private static final String SESSION_KICKED = "KICKED";

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final LoginSessionMapper loginSessionMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final RevocationMapper revocationMapper;
    private final PermissionQueryMapper permissionQueryMapper;
    private final JwtKeyProvider keyProvider;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties properties;
    private final AuditSink auditSink;

    public AuthService(TenantMapper tenantMapper, UserMapper userMapper, AccountMapper accountMapper,
                       LoginSessionMapper loginSessionMapper, RefreshTokenMapper refreshTokenMapper,
                       RevocationMapper revocationMapper, PermissionQueryMapper permissionQueryMapper,
                       JwtKeyProvider keyProvider,
                       JwtTokenService jwtTokenService, AuthProperties properties, AuditSink auditSink) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.loginSessionMapper = loginSessionMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.revocationMapper = revocationMapper;
        this.permissionQueryMapper = permissionQueryMapper;
        this.keyProvider = keyProvider;
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
        this.auditSink = auditSink;
    }

    /**
     * 口令登录。失败计数/锁定需要落库，因此 BizException 不触发回滚。
     */
    @Transactional(noRollbackFor = BizException.class)
    public TokenPayload login(LoginRequest request) {
        String clientType = request.clientType() == null || request.clientType().isBlank()
                ? "PORTAL" : request.clientType().toUpperCase();
        TenantEntity tenant = tenantMapper.selectOne(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getCode, request.tenantCode())
                .eq(TenantEntity::getDeleted, 0));
        if (tenant == null || !STATUS_ACTIVE.equals(tenant.getStatus())) {
            throw new BizException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        TenantContext.set(tenant.getId(), null, clientType, null, false);

        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getTenantId, tenant.getId())
                .eq(UserEntity::getUsername, request.username())
                .eq(UserEntity::getDeleted, 0));
        if (user == null) {
            audit(tenant.getId(), null, clientType, "LOGIN_FAILED", "USER_NOT_FOUND");
            throw new BizException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        AccountEntity account = accountMapper.selectOne(Wrappers.<AccountEntity>lambdaQuery()
                .eq(AccountEntity::getTenantId, tenant.getId())
                .eq(AccountEntity::getUserId, user.getId())
                .eq(AccountEntity::getDeleted, 0));
        if (account == null) {
            audit(tenant.getId(), user.getId(), clientType, "LOGIN_FAILED", "ACCOUNT_NOT_FOUND");
            throw new BizException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Instant now = Instant.now();
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(now)) {
            audit(tenant.getId(), user.getId(), clientType, "LOGIN_BLOCKED", "LOCKED");
            throw new BizException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            audit(tenant.getId(), user.getId(), clientType, "LOGIN_BLOCKED", "USER_" + user.getStatus());
            throw new BizException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        if (!PasswordPolicy.matches(request.password(), account.getPasswordHash())) {
            int failCount = (account.getFailCount() == null ? 0 : account.getFailCount()) + 1;
            if (failCount >= properties.getMaxFailCount()) {
                accountMapper.updateFailState(tenant.getId(), user.getId(), failCount,
                        now.plus(properties.getLockDuration()));
                audit(tenant.getId(), user.getId(), clientType, "LOGIN_FAILED", "LOCKED_AFTER_MAX_FAILURES");
                throw new BizException(AuthErrorCode.ACCOUNT_LOCKED);
            }
            accountMapper.updateFailState(tenant.getId(), user.getId(), failCount, null);
            audit(tenant.getId(), user.getId(), clientType, "LOGIN_FAILED", "BAD_PASSWORD");
            throw new BizException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        accountMapper.resetFailState(tenant.getId(), user.getId());
        userMapper.touchLastLogin(tenant.getId(), user.getId());

        String sessionId = newSession(tenant, user, clientType, request.deviceId());
        JwtTokenService.IssuedToken access = issueAccessToken(tenant, user, clientType, sessionId, now);
        JwtTokenService.IssuedToken refresh = jwtTokenService.refreshToken(tenant, user, clientType, sessionId, now);
        JwtTokenService.IssuedToken idToken = jwtTokenService.idToken(tenant, user, clientType, sessionId, now);
        storeRefreshToken(tenant, user, sessionId, clientType, refresh, now);

        boolean pwdResetRequired = isPasswordResetRequired(account, now);
        audit(tenant.getId(), user.getId(), clientType, "LOGIN_SUCCESS", "PASSWORD");
        return new TokenPayload(access.token(), "Bearer", properties.getAccessTokenTtl().toSeconds(),
                refresh.token(), idToken.token(), "openid profile", sessionId, pwdResetRequired);
    }

    @Transactional(noRollbackFor = BizException.class)
    public TokenPayload refresh(String rawRefreshToken) {
        JWTClaimsSet claims = verifyRefresh(rawRefreshToken);
        Long tenantId = longClaim(claims, "tenant_id");
        Long userId = Long.valueOf(claims.getSubject());
        String sessionId = (String) claims.getClaim("sid");
        String clientType = (String) claims.getClaim("client_type");
        TenantContext.set(tenantId, userId, clientType, null, false);

        String tokenHash = sha256(rawRefreshToken);
        RefreshTokenEntity stored = refreshTokenMapper.selectOne(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getTenantId, tenantId)
                .eq(RefreshTokenEntity::getTokenHash, tokenHash)
                .eq(RefreshTokenEntity::getDeleted, 0));
        if (stored == null) {
            throw new BizException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        Instant now = Instant.now();
        if (stored.getRevoked() != null && stored.getRevoked() == 1) {
            // 宽限期内被轮换视为并发刷新（多端同时到达），仅拒绝本次请求，不终止会话
            if (stored.getRotatedAt() != null
                    && stored.getRotatedAt().isAfter(now.minus(properties.getRefreshGrace()))) {
                throw new BizException(AuthErrorCode.REFRESH_TOKEN_INVALID, "refresh token 已被并发轮换");
            }
            // 超过宽限期再次使用 refresh token 视为泄露：终止整个会话
            loginSessionMapper.closeBySession(tenantId, sessionId, SESSION_KICKED);
            refreshTokenMapper.revokeBySession(tenantId, sessionId);
            throw new BizException(AuthErrorCode.REFRESH_TOKEN_INVALID, "refresh token 已被使用，会话已终止");
        }
        if (stored.getExpiresAt() == null || stored.getExpiresAt().isBefore(now)) {
            throw new BizException(AuthErrorCode.REFRESH_TOKEN_INVALID, "refresh token 已过期");
        }
        LoginSessionEntity session = loginSessionMapper.selectOne(Wrappers.<LoginSessionEntity>lambdaQuery()
                .eq(LoginSessionEntity::getTenantId, tenantId)
                .eq(LoginSessionEntity::getSessionId, sessionId)
                .eq(LoginSessionEntity::getDeleted, 0));
        if (session == null || !SESSION_ONLINE.equals(session.getStatus())
                || session.getExpireAt() == null || session.getExpireAt().isBefore(now)) {
            throw new BizException(AuthErrorCode.REFRESH_TOKEN_INVALID, "会话已失效");
        }
        UserEntity user = userMapper.selectById(userId);
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (user == null || tenant == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BizException(AuthErrorCode.REFRESH_TOKEN_INVALID, "用户状态不可用");
        }

        if (refreshTokenMapper.revokeByHash(tenantId, tokenHash) != 1) {
            throw new BizException(AuthErrorCode.REFRESH_TOKEN_INVALID, "refresh token 轮换冲突");
        }
        JwtTokenService.IssuedToken access = issueAccessToken(tenant, user, clientType, sessionId, now);
        JwtTokenService.IssuedToken refresh = jwtTokenService.refreshToken(tenant, user, clientType, sessionId, now);
        JwtTokenService.IssuedToken idToken = jwtTokenService.idToken(tenant, user, clientType, sessionId, now);
        storeRefreshToken(tenant, user, sessionId, clientType, refresh, now);
        extendSession(session, now);
        audit(tenantId, userId, clientType, "TOKEN_REFRESH", "REFRESH_OK");
        return new TokenPayload(access.token(), "Bearer", properties.getAccessTokenTtl().toSeconds(),
                refresh.token(), idToken.token(), "openid profile", sessionId, false);
    }

    @Transactional
    public boolean revoke(String token, String tokenTypeHint) {
        JWTClaimsSet claims = keyProvider.verifySignature(token);
        Long tenantId = longClaim(claims, "tenant_id");
        Long userId = claims.getSubject() == null ? null : Long.valueOf(claims.getSubject());
        String clientType = (String) claims.getClaim("client_type");
        String tokenUse = (String) claims.getClaim("token_use");
        if (tenantId == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "令牌缺少租户信息");
        }
        TenantContext.set(tenantId, userId, clientType, null, false);
        if (claims.getExpirationTime() != null && claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
            return true;
        }
        if (JwtTokenService.USE_REFRESH.equals(tokenUse)) {
            refreshTokenMapper.revokeByHash(tenantId, sha256(token));
        }
        insertRevocation(tenantId, claims.getJWTID(), userId, tokenUse, "REVOKE");
        audit(tenantId, userId, clientType, "TOKEN_REVOKED", tokenTypeHint == null ? tokenUse : tokenTypeHint);
        return true;
    }

    @Transactional
    public LogoutPayload logout(String idTokenHint, boolean allSessions) {
        JWTClaimsSet claims = keyProvider.verifySignature(idTokenHint);
        Long tenantId = longClaim(claims, "tenant_id");
        Long userId = Long.valueOf(claims.getSubject());
        String sessionId = (String) claims.getClaim("sid");
        String clientType = (String) claims.getClaim("client_type");
        TenantContext.set(tenantId, userId, clientType, null, false);

        List<LoginSessionEntity> online = loginSessionMapper.selectList(Wrappers.<LoginSessionEntity>lambdaQuery()
                .eq(LoginSessionEntity::getTenantId, tenantId)
                .eq(LoginSessionEntity::getUserId, userId)
                .eq(LoginSessionEntity::getStatus, SESSION_ONLINE)
                .eq(LoginSessionEntity::getDeleted, 0));
        List<LogoutPayload.ClientSession> updated = new ArrayList<>();
        for (LoginSessionEntity session : online) {
            if (allSessions || session.getSessionId().equals(sessionId)) {
                updated.add(new LogoutPayload.ClientSession(session.getClientType(), SESSION_LOGOUT));
            }
        }
        if (allSessions) {
            loginSessionMapper.closeAllByUser(tenantId, userId, SESSION_LOGOUT);
            refreshTokenMapper.revokeAllByUser(tenantId, userId);
        } else {
            loginSessionMapper.closeBySession(tenantId, sessionId, SESSION_LOGOUT);
            refreshTokenMapper.revokeBySession(tenantId, sessionId);
        }
        insertRevocation(tenantId, claims.getJWTID(), userId, JwtTokenService.USE_ID, "LOGOUT");
        audit(tenantId, userId, clientType, "LOGOUT", allSessions ? "ALL_SESSIONS" : "CURRENT_SESSION");
        return new LogoutPayload(updated);
    }

    @Transactional(readOnly = true)
    public UserInfoPayload userinfo(String accessToken) {
        JWTClaimsSet claims = keyProvider.verify(accessToken, JwtTokenService.USE_ACCESS);
        Long tenantId = longClaim(claims, "tenant_id");
        Long userId = Long.valueOf(claims.getSubject());
        String clientType = (String) claims.getClaim("client_type");
        TenantContext.set(tenantId, userId, clientType, null, false);
        if (isRevokedJti(claims.getJWTID())) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "令牌已撤销");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "用户不可用");
        }
        return new UserInfoPayload(
                String.valueOf(user.getId()),
                user.getUsername(),
                displayName(user),
                user.getEmployeeNo(),
                tenantId,
                (String) claims.getClaim("tenant_code"),
                clientType,
                permissionQueryMapper.findRoleCodes(userId),
                permissionQueryMapper.findPermissionCodes(userId));
    }

    @Transactional(readOnly = true)
    public boolean isSessionOnline(String sessionId) {
        Long tenantId = TenantContext.requireTenantId();
        LoginSessionEntity session = loginSessionMapper.selectOne(Wrappers.<LoginSessionEntity>lambdaQuery()
                .eq(LoginSessionEntity::getTenantId, tenantId)
                .eq(LoginSessionEntity::getSessionId, sessionId)
                .eq(LoginSessionEntity::getDeleted, 0));
        return session != null
                && SESSION_ONLINE.equals(session.getStatus())
                && session.getExpireAt() != null
                && session.getExpireAt().isAfter(Instant.now());
    }

    @Transactional(readOnly = true)
    public boolean isRevokedJti(String jti) {
        Long tenantId = TenantContext.requireTenantId();
        return revocationMapper.selectCount(Wrappers.<RevocationEntity>lambdaQuery()
                .eq(RevocationEntity::getTenantId, tenantId)
                .eq(RevocationEntity::getJti, jti)
                .eq(RevocationEntity::getDeleted, 0)) > 0;
    }

    /**
     * PLAT-10：签发只带 roles + pv 的 access token。
     * include-permissions=true 时额外写入全量权限（灰度回滚路径）。
     */
    private JwtTokenService.IssuedToken issueAccessToken(TenantEntity tenant, UserEntity user,
                                                         String clientType, String sessionId, Instant now) {
        List<String> roles = permissionQueryMapper.findRoleCodes(user.getId());
        long pv = user.getPermissionVersion() == null ? 0L : user.getPermissionVersion();
        List<String> permissions = properties.getJwt().isIncludePermissions()
                ? permissionQueryMapper.findPermissionCodes(user.getId()) : null;
        return jwtTokenService.accessToken(tenant, user, clientType, sessionId, roles, pv, permissions, now);
    }

    /**
     * 网关按 (tenantId, userId, pv) 取权限的内部契约。账号非 ACTIVE 或无角色时返回空权限，
     * 由下游 PermissionGuard 产生 403，而不是在网关层伪装成故障。
     */
    @Transactional(readOnly = true)
    public PrincipalPermissionPayload permissionsOf(long userId) {
        Long tenantId = TenantContext.requireTenantId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)
                || !tenantId.equals(user.getTenantId())) {
            return new PrincipalPermissionPayload(userId, 0L, List.of(), List.of());
        }
        long pv = user.getPermissionVersion() == null ? 0L : user.getPermissionVersion();
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            return new PrincipalPermissionPayload(userId, pv, List.of(), List.of());
        }
        return new PrincipalPermissionPayload(userId, pv,
                permissionQueryMapper.findRoleCodes(userId),
                permissionQueryMapper.findPermissionCodes(userId));
    }

    private JWTClaimsSet verifyRefresh(String rawRefreshToken) {
        try {
            return keyProvider.verify(rawRefreshToken, JwtTokenService.USE_REFRESH);
        } catch (BizException e) {
            throw new BizException(AuthErrorCode.REFRESH_TOKEN_INVALID, e.getMessage());
        }
    }

    private String newSession(TenantEntity tenant, UserEntity user, String clientType, String deviceId) {
        Instant now = Instant.now();
        LoginSessionEntity session = new LoginSessionEntity();
        session.setId(IdWorker.getId());
        session.setTenantId(tenant.getId());
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(user.getId());
        session.setClientType(clientType);
        session.setLoginType("PASSWORD");
        session.setDeviceId(deviceId);
        session.setLoginAt(now);
        session.setExpireAt(now.plus(properties.getRefreshTokenTtl()));
        session.setStatus(SESSION_ONLINE);
        session.setDeleted(0);
        loginSessionMapper.insert(session);
        return session.getSessionId();
    }

    private void extendSession(LoginSessionEntity session, Instant now) {
        session.setExpireAt(now.plus(properties.getRefreshTokenTtl()));
        loginSessionMapper.updateById(session);
    }

    private void storeRefreshToken(TenantEntity tenant, UserEntity user, String sessionId,
                                   String clientType, JwtTokenService.IssuedToken refresh, Instant now) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(IdWorker.getId());
        entity.setTenantId(tenant.getId());
        entity.setUserId(user.getId());
        entity.setSessionId(sessionId);
        entity.setTokenHash(sha256(refresh.token()));
        entity.setTokenType("REFRESH");
        entity.setClientType(clientType);
        entity.setIssuedAt(now);
        entity.setExpiresAt(refresh.expiresAt());
        entity.setRevoked(0);
        entity.setDeleted(0);
        refreshTokenMapper.insert(entity);
    }

    private void insertRevocation(Long tenantId, String jti, Long userId, String tokenUse, String reason) {
        if (jti == null || isRevokedJti(jti)) {
            return;
        }
        RevocationEntity entity = new RevocationEntity();
        entity.setId(IdWorker.getId());
        entity.setTenantId(tenantId);
        entity.setJti(jti);
        entity.setUserId(userId);
        entity.setTokenType(tokenUse);
        entity.setReason(reason);
        entity.setRevokedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(properties.getRefreshTokenTtl()));
        entity.setDeleted(0);
        revocationMapper.insert(entity);
    }

    private boolean isPasswordResetRequired(AccountEntity account, Instant now) {
        if (account.getPwdResetRequired() != null && account.getPwdResetRequired() == 1) {
            return true;
        }
        if (account.getPasswordUpdatedAt() == null) {
            return true;
        }
        return account.getPasswordUpdatedAt()
                .plus(properties.getPasswordMaxAgeDays(), ChronoUnit.DAYS)
                .isBefore(now);
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

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private Long longClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getLongClaim(name);
        } catch (ParseException e) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "令牌声明不合法: " + name);
        }
    }

    private void audit(Long tenantId, Long userId, String clientType, String action, String reason) {
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                "AUTH_" + action,
                "auth",
                action,
                action.contains("FAILED") || action.contains("BLOCKED") ? "MEDIUM" : "LOW",
                tenantId,
                userId,
                clientType,
                null,
                "AUTH",
                reason,
                Instant.now()));
    }
}
