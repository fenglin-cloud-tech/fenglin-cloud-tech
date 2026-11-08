package com.fenglin.tenant.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.RequestContext;
import com.fenglin.common.security.PasswordPolicy;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.tenant.mapper.TenantMapper;
import com.fenglin.tenant.web.dto.CreateTenantRequest;
import com.fenglin.tenant.web.dto.CreateTenantResult;
import com.fenglin.tenant.web.dto.TenantView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TenantService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String ADMIN_ROLE_CODE = "TENANT_ADMIN";

    private final TenantMapper tenantMapper;
    private final AuditSink auditSink;

    public TenantService(TenantMapper tenantMapper, AuditSink auditSink) {
        this.tenantMapper = tenantMapper;
        this.auditSink = auditSink;
    }

    /**
     * HUB-API-201：租户开通 + 初始管理员一次成型，期间临时把租户上下文切到新租户以满足 RLS WITH CHECK。
     */
    @Transactional
    public CreateTenantResult create(CreateTenantRequest request) {
        String code = request.code() == null || request.code().isBlank()
                ? generateTenantCode() : request.code();
        if (tenantMapper.countByCode(code) > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "租户编码已存在: " + code);
        }

        long tenantId = IdWorker.getId();
        Instant now = Instant.now();
        Instant expireAt = resolveExpireAt(request, now);
        String hashSaltEnc = "v1:" + UUID.randomUUID().toString().replace("-", "");
        tenantMapper.insert(tenantId, code, request.name(), STATUS_ACTIVE,
                request.contactName(), hashSaltEnc, expireAt);

        if (request.packageCode() != null && !request.packageCode().isBlank()) {
            Map<String, Object> pkg = tenantMapper.findActivePackageByCode(request.packageCode());
            if (pkg == null) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "套餐不可用: " + request.packageCode());
            }
            long packageId = ((Number) pkg.get("id")).longValue();
            int trial = request.trialDays() != null && request.trialDays() > 0 ? 1 : 0;
            withTenantContext(tenantId,
                    () -> tenantMapper.insertSubscription(IdWorker.getId(), tenantId, packageId, trial, now, expireAt));
        }

        String initialPassword = request.adminPassword() == null || request.adminPassword().isBlank()
                ? PasswordPolicy.generate() : request.adminPassword();
        String passwordHash = PasswordPolicy.encode(initialPassword);
        withTenantContext(tenantId,
                () -> bootstrapAdmin(tenantId, request.adminUsername(), passwordHash));

        audit(tenantId, "TENANT_CREATED", code);
        return new CreateTenantResult(tenantId, code, STATUS_ACTIVE, initialPassword);
    }

    /**
     * 在临时租户上下文内写入 subscription/user/account/role 等受 RLS 约束的数据，结束后恢复原快照。
     */
    private void withTenantContext(long tenantId, Runnable action) {
        TenantContext.Snapshot previous = TenantContext.get();
        TenantContext.set(tenantId, null, "ADMIN", RequestContext.requestId(), false);
        try {
            action.run();
        } finally {
            if (previous != null) {
                TenantContext.set(previous.tenantId(), previous.userId(), previous.clientType(),
                        previous.requestId(), previous.crossTenant());
            } else {
                TenantContext.clear();
            }
        }
    }

    private void bootstrapAdmin(long tenantId, String adminUsername, String passwordHash) {
        long roleId = IdWorker.getId();
        tenantMapper.insertBuiltinRole(roleId, tenantId, ADMIN_ROLE_CODE, "租户管理员");
        for (String permissionCode : tenantMapper.listTenantGrantablePermissionCodes()) {
            tenantMapper.grantPermission(IdWorker.getId(), tenantId, roleId, permissionCode);
        }

        long userId = IdWorker.getId();
        tenantMapper.insertAdminUser(userId, tenantId, adminUsername, adminUsername);
        tenantMapper.insertAccount(IdWorker.getId(), tenantId, userId, passwordHash);
        tenantMapper.insertUserRole(IdWorker.getId(), tenantId, userId, roleId);
    }

    @Transactional(readOnly = true)
    public TenantView get(long id) {
        return toView(requireTenant(id));
    }

    @Transactional(readOnly = true)
    public TenantView current() {
        return toView(requireTenant(TenantContext.requireTenantId()));
    }

    @Transactional(readOnly = true)
    public List<TenantView> list(String status, String keyword, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        return tenantMapper.findPage(normalize(status), normalize(keyword), normalizedSize,
                        (normalizedPage - 1) * normalizedSize)
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public long count(String status, String keyword) {
        return tenantMapper.count(normalize(status), normalize(keyword));
    }

    /**
     * HUB-API-204：action SUSPEND|RESUME|CLOSE 映射为 SUSPENDED|ACTIVE|DISABLED。
     */
    @Transactional
    public TenantView updateStatus(long id, String action, String reason) {
        Map<String, Object> row = requireTenant(id);
        String current = (String) row.get("status");
        String target = switch (action == null ? "" : action.toUpperCase()) {
            case "SUSPEND" -> STATUS_SUSPENDED;
            case "RESUME" -> STATUS_ACTIVE;
            case "CLOSE" -> STATUS_DISABLED;
            default -> throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "action 仅支持 SUSPEND/RESUME/CLOSE");
        };
        if (STATUS_DISABLED.equals(current) || target.equals(current)) {
            throw new BizException(CommonErrorCode.CONFLICT, "非法状态流转: " + current + " -> " + target);
        }
        if ("RESUME".equalsIgnoreCase(action) && !STATUS_SUSPENDED.equals(current)) {
            throw new BizException(CommonErrorCode.CONFLICT, "仅已暂停租户可恢复");
        }
        tenantMapper.updateStatus(id, target);
        audit(id, "TENANT_STATUS_CHANGED", target + (reason == null || reason.isBlank() ? "" : ":" + reason));
        return toView(tenantMapper.findById(id));
    }

    private Instant resolveExpireAt(CreateTenantRequest request, Instant now) {
        if (request.expireAt() != null) {
            return request.expireAt();
        }
        if (request.trialDays() != null && request.trialDays() > 0) {
            return now.plus(request.trialDays(), ChronoUnit.DAYS);
        }
        return null;
    }

    private String generateTenantCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "T" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            if (tenantMapper.countByCode(candidate) == 0) {
                return candidate;
            }
        }
        throw new BizException(CommonErrorCode.CONFLICT, "租户编码生成冲突，请重试");
    }

    private Map<String, Object> requireTenant(long id) {
        Map<String, Object> row = tenantMapper.findById(id);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "租户不存在: " + id);
        }
        return row;
    }

    private TenantView toView(Map<String, Object> row) {
        return new TenantView(
                ((Number) row.get("id")).longValue(),
                (String) row.get("code"),
                (String) row.get("name"),
                (String) row.get("status"),
                (String) row.get("contact_name"),
                toInstant(row.get("expire_at")),
                toInstant(row.get("created_at")));
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(long tenantId, String action, String target) {
        auditSink.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                "TENANT_" + action,
                "tenant",
                action,
                "HIGH",
                tenantId,
                null,
                null,
                RequestContext.requestId(),
                "TENANT",
                target,
                Instant.now()));
    }
}
