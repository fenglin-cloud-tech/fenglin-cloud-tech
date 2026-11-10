package com.fenglin.tenant.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.tenant.mapper.TenantMapper;
import com.fenglin.tenant.web.dto.PlatformAdminView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 平台管理员角色分配/回收。
 *
 * <p>PLATFORM_ADMIN 角色在租户初始化时创建但不绑定用户（见 TenantService#bootstrapAdmin），
 * 本服务提供最小可用的授予、撤销与查询能力，替代过去只能手工写 hub_user_role 的做法。</p>
 *
 * <p>安全约束：撤销时若当前租户只剩一名平台管理员则拒绝，避免把系统改成"无人可管"。</p>
 */
@Service
public class PlatformAdminService {

    private static final String PLATFORM_ADMIN_ROLE_CODE = "PLATFORM_ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final TenantMapper tenantMapper;
    private final AuditSink auditSink;

    public PlatformAdminService(TenantMapper tenantMapper, AuditSink auditSink) {
        this.tenantMapper = tenantMapper;
        this.auditSink = auditSink;
    }

    @Transactional
    public PlatformAdminView grant(long userId) {
        long tenantId = TenantContext.requireTenantId();
        requireActiveUser(tenantId, userId);
        long roleId = requirePlatformAdminRole(tenantId);

        Map<String, Object> existing = tenantMapper.findUserRole(tenantId, userId, roleId);
        if (existing == null) {
            tenantMapper.insertUserRole(IdWorker.getId(), tenantId, userId, roleId);
        } else if (((Number) existing.get("deleted")).intValue() != 0) {
            tenantMapper.restoreUserRole(((Number) existing.get("id")).longValue());
        }
        audit(tenantId, userId, "PLATFORM_ADMIN_GRANTED", "GRANT");
        return new PlatformAdminView(userId, true, roleId);
    }

    @Transactional
    public PlatformAdminView revoke(long userId) {
        long tenantId = TenantContext.requireTenantId();
        Long roleId = tenantMapper.findPlatformAdminRoleOfUser(tenantId, userId);
        if (roleId == null) {
            throw new BizException(CommonErrorCode.CONFLICT, "该用户不是平台管理员: " + userId);
        }
        if (tenantMapper.countPlatformAdmins(tenantId) <= 1) {
            throw new BizException(CommonErrorCode.CONFLICT,
                    "至少保留一名平台管理员，不能撤销最后一个: " + userId);
        }
        tenantMapper.revokeUserRole(tenantId, userId, roleId);
        audit(tenantId, userId, "PLATFORM_ADMIN_REVOKED", "REVOKE");
        return new PlatformAdminView(userId, false, roleId);
    }

    @Transactional(readOnly = true)
    public PlatformAdminView status(long userId) {
        long tenantId = TenantContext.requireTenantId();
        requireActiveUser(tenantId, userId);
        Long roleId = tenantMapper.findPlatformAdminRoleOfUser(tenantId, userId);
        return new PlatformAdminView(userId, roleId != null, roleId);
    }

    private void requireActiveUser(long tenantId, long userId) {
        Map<String, Object> user = tenantMapper.findTenantUser(tenantId, userId);
        if (user == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "用户不存在: " + userId);
        }
        if (!STATUS_ACTIVE.equals(user.get("status"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "用户未启用，不能分配平台管理员: " + userId);
        }
    }

    private long requirePlatformAdminRole(long tenantId) {
        Long roleId = tenantMapper.findPlatformAdminRoleId(tenantId);
        if (roleId == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND,
                    "当前租户缺少 " + PLATFORM_ADMIN_ROLE_CODE + " 角色，请先执行迁移 V1_0_48");
        }
        return roleId;
    }

    private void audit(long tenantId, long userId, String eventType, String action) {
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                "tenant",
                action,
                "HIGH",
                tenantId,
                null,
                null,
                RequestContext.requestId(),
                "ADMIN",
                String.valueOf(userId),
                Instant.now()));
    }
}
