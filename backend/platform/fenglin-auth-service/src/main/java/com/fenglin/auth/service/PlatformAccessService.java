package com.fenglin.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fenglin.auth.config.AuthErrorCode;
import com.fenglin.auth.config.AuthProperties;
import com.fenglin.auth.entity.PlatformOperatorEntity;
import com.fenglin.auth.entity.TenantEntity;
import com.fenglin.auth.entity.UserEntity;
import com.fenglin.auth.mapper.PermissionQueryMapper;
import com.fenglin.auth.mapper.PlatformOperatorMapper;
import com.fenglin.auth.mapper.TenantMapper;
import com.fenglin.auth.mapper.UserMapper;
import com.fenglin.auth.web.dto.TokenPayload;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.tenant.TenantContext;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OPEN-06 一期：平台运维身份与跨租户只读切换。
 *
 * <p>一期范围（B）：只放行平台治理域（租户、组织、用户、角色、审计、配置、订阅），
 * 八个业务域由网关按路径白名单直接拒绝；写操作全部拒绝。平台运维白名单为全局表，
 * 不自动升级存量 PLATFORM_ADMIN。</p>
 */
@Service
public class PlatformAccessService {

    public static final String SWITCH_PERMISSION = "platform:tenant:switch";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String USER_STATUS_ACTIVE = "ACTIVE";
    private static final String PLATFORM_ROLE_CODE = "PLATFORM_OPS";

    /**
     * 平台治理域权限前缀：目标租户 TENANT_ADMIN 权限中只保留这些前缀。
     * 业务域前缀（如 tenant:legal:*、tenant:service:*）不在此列，从权限面就收敛。
     */
    private static final List<String> GOVERNANCE_PERMISSION_PREFIXES = List.of(
            "platform:tenant:",
            "platform:audit:",
            "platform:billing:",
            "platform:subscription",
            "tenant:iam:",
            "hub:iam:",
            "hub:audit:"
    );

    private final PlatformOperatorMapper operatorMapper;
    private final PermissionQueryMapper permissionQueryMapper;
    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final JwtKeyProvider keyProvider;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties properties;
    private final AuditSink auditSink;

    public PlatformAccessService(PlatformOperatorMapper operatorMapper,
                                 PermissionQueryMapper permissionQueryMapper,
                                 TenantMapper tenantMapper, UserMapper userMapper,
                                 JwtKeyProvider keyProvider, JwtTokenService jwtTokenService,
                                 AuthProperties properties, AuditSink auditSink) {
        this.operatorMapper = operatorMapper;
        this.permissionQueryMapper = permissionQueryMapper;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.keyProvider = keyProvider;
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
        this.auditSink = auditSink;
    }

    /** 当前用户是否是启用中的平台运维（全局白名单，按 user_id 唯一）。 */
    @Transactional(readOnly = true)
    public boolean isActiveOperator(long userId) {
        return activeOperator(userId) != null;
    }

    @Transactional(readOnly = true)
    public PlatformOperatorEntity activeOperator(long userId) {
        return operatorMapper.selectOne(Wrappers.<PlatformOperatorEntity>lambdaQuery()
                .eq(PlatformOperatorEntity::getUserId, userId)
                .eq(PlatformOperatorEntity::getStatus, STATUS_ACTIVE)
                .eq(PlatformOperatorEntity::getDeleted, 0)
                .and(w -> w.isNull(PlatformOperatorEntity::getExpireAt)
                        .or().gt(PlatformOperatorEntity::getExpireAt, Instant.now())));
    }

    /**
     * 目标租户的治理域权限：TENANT_ADMIN 权限按治理域前缀收敛 + 切换权限。
     * RLS 由当前 X-Tenant-Id（目标租户）承担。
     */
    @Transactional(readOnly = true)
    public List<String> governancePermissions(long targetTenantId) {
        List<String> permissions = new ArrayList<>(permissionQueryMapper
                .findRolePermissionCodes(targetTenantId, "TENANT_ADMIN"));
        permissions.removeIf(code -> GOVERNANCE_PERMISSION_PREFIXES.stream()
                .noneMatch(code::startsWith) || !isReadOnlyGovernancePermission(code));
        if (!permissions.contains(SWITCH_PERMISSION)) {
            permissions.add(SWITCH_PERMISSION);
        }
        return List.copyOf(permissions);
    }

    /**
     * 治理域内仍只下发只读权限点：读/查类后缀，加上组织树 GET 所需的 {@code tenant:iam:org:manage}。
     * 其余 manage/write/create/update 一律不下发，写权限二期再开。
     */
    private boolean isReadOnlyGovernancePermission(String code) {
        if ("tenant:iam:org:manage".equals(code)) {
            return true;
        }
        return code.endsWith(":read") || code.endsWith(":view")
                || code.endsWith(":list") || code.endsWith(":query");
    }

    /** 网关权限解析的平台分支：白名单 + 目标租户 ACTIVE 才返回治理域权限。 */
    @Transactional(readOnly = true)
    public List<String> permissionsForTarget(long userId, long targetTenantId) {
        if (!isActiveOperator(userId)) {
            return List.of();
        }
        TenantEntity target = tenantMapper.selectById(targetTenantId);
        if (target == null || (target.getDeleted() != null && target.getDeleted() == 1)
                || !STATUS_ACTIVE.equals(target.getStatus())) {
            return List.of();
        }
        return governancePermissions(targetTenantId);
    }

    public List<String> roleCodes() {
        return List.of(PLATFORM_ROLE_CODE);
    }

    /**
     * 显式租户切换：校验 home 租户令牌 + 平台白名单 + 目标租户 ACTIVE，
     * 签发短时只读令牌（不换绑 refresh，不允许再次切换），并写切换审计。
     */
    @Transactional(noRollbackFor = BizException.class)
    public TokenPayload switchTenant(String rawAccessToken, long targetTenantId, String reason) {
        JWTClaimsSet claims = keyProvider.verify(rawAccessToken, JwtTokenService.USE_ACCESS);
        Object tenantClaim = claims.getClaim("tenant_id");
        Long claimTenantId = tenantClaim instanceof Number number ? number.longValue() : null;
        String subject = claims.getSubject();
        if (claimTenantId == null || subject == null) {
            throw new BizException(AuthErrorCode.INVALID_CREDENTIALS, "令牌缺少身份信息");
        }
        long homeTenantId = claimTenantId;
        long userId = Long.parseLong(subject);
        Long contextTenantId = TenantContext.getTenantId();
        if (contextTenantId != null && contextTenantId != homeTenantId) {
            throw new BizException(AuthErrorCode.ACCESS_DENIED, "令牌租户与请求上下文不一致");
        }
        String clientType = (String) claims.getClaim("client_type");
        if (!"ADMIN".equals(clientType)) {
            throw new BizException(AuthErrorCode.ACCESS_DENIED, "仅管理端可发起跨租户只读切换");
        }
        if (claims.getClaim("platform_ops") != null) {
            throw new BizException(AuthErrorCode.INVALID_PARAMETER, "切换令牌不允许再次切换");
        }
        PlatformOperatorEntity operator = activeOperator(userId);
        if (operator == null || operator.getHomeTenantId() == null
                || operator.getHomeTenantId() != homeTenantId) {
            throw new BizException(AuthErrorCode.ACCESS_DENIED, "当前账号没有平台运维身份");
        }
        if (targetTenantId == homeTenantId) {
            throw new BizException(AuthErrorCode.INVALID_PARAMETER, "目标租户不能与当前租户相同");
        }
        TenantEntity target = tenantMapper.selectById(targetTenantId);
        if (target == null || (target.getDeleted() != null && target.getDeleted() == 1)) {
            throw new BizException(AuthErrorCode.INVALID_PARAMETER, "目标租户不存在");
        }
        if (!STATUS_ACTIVE.equals(target.getStatus())) {
            throw new BizException(AuthErrorCode.ACCESS_DENIED, "目标租户未启用，一期仅允许切入 ACTIVE 租户");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !USER_STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BizException(AuthErrorCode.INVALID_CREDENTIALS, "平台运维账号不可用");
        }

        Instant now = Instant.now();
        String switchId = UUID.randomUUID().toString().replace("-", "");
        JwtTokenService.IssuedToken token = jwtTokenService.platformSwitchToken(
                target, user, homeTenantId, switchId, now);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("home_tenant_id", homeTenantId);
        detail.put("target_tenant_id", targetTenantId);
        detail.put("switch_id", switchId);
        detail.put("operator_username", user.getUsername());
        detail.put("reason", normalizeReason(reason));
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                "PLATFORM_TENANT_SWITCH",
                "platform",
                "SWITCH",
                "HIGH",
                targetTenantId,
                userId,
                "ADMIN",
                RequestContext.requestId(),
                "TENANT",
                String.valueOf(targetTenantId),
                now,
                detail));

        return new TokenPayload(token.token(), "Bearer",
                properties.getPlatformSwitch().getTtl().toSeconds(),
                null, null, "openid profile", null, false);
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "平台运维跨租户只读查看" : reason.trim();
    }
}
