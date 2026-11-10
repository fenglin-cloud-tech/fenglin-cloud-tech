package com.fenglin.openapi.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.audit.AuditEvent;
import com.fenglin.common.audit.AuditSink;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.OpenApiErrorCode;
import com.fenglin.openapi.mapper.ApiCallLogMapper;
import com.fenglin.openapi.mapper.ApiQuotaMapper;
import com.fenglin.openapi.mapper.OpenApiScopeMapper;
import com.fenglin.openapi.mapper.OpenAppMapper;
import com.fenglin.openapi.web.dto.OpenApiDtos.AdminAppView;
import com.fenglin.openapi.web.dto.OpenApiDtos.AppCreateRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.AppCreateResponse;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalAppCreateRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalAppDetail;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalAppView;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalScopeView;
import com.fenglin.openapi.web.dto.OpenApiDtos.ReviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HUB-API-1001 应用注册、1002 应用审核、1006 开发者门户应用列表。
 */
@Service
public class OpenAppService {

    private static final Set<String> OWNER_TYPES = Set.of("ISV", "INTERNAL", "CUSTOMER");

    private final OpenAppMapper appMapper;
    private final ApiCallLogMapper callLogMapper;
    private final ApiQuotaMapper quotaMapper;
    private final OpenApiScopeMapper scopeMapper;
    private final AuditSink auditSink;

    public OpenAppService(OpenAppMapper appMapper,
                          ApiCallLogMapper callLogMapper,
                          ApiQuotaMapper quotaMapper,
                          OpenApiScopeMapper scopeMapper,
                          AuditSink auditSink) {
        this.appMapper = appMapper;
        this.callLogMapper = callLogMapper;
        this.quotaMapper = quotaMapper;
        this.scopeMapper = scopeMapper;
        this.auditSink = auditSink;
    }

    @Transactional
    public AppCreateResponse create(AppCreateRequest request, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        String name = requireText(request.name(), "name", 128);
        String ownerType = request.ownerType() == null || request.ownerType().isBlank()
                ? "INTERNAL" : request.ownerType().trim().toUpperCase();
        if (!OWNER_TYPES.contains(ownerType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "owner_type 仅支持 ISV/INTERNAL/CUSTOMER");
        }
        if (appMapper.findByTenantAndName(tenantId, name) != null) {
            throw new BizException(OpenApiErrorCode.APP_EXISTS, "应用已存在: " + name);
        }
        String code = appMapper.nextCode();
        long id = IdWorker.getId();
        appMapper.insert(id, tenantId, code, name, ownerType, operatorId,
                join(request.scopes(), 1024, "scopes"), join(request.redirectUris(), 1024, "redirect_uris"),
                operatorId);
        audit("OPENAPI_APP_CREATED", tenantId, operatorId, String.valueOf(id));
        return new AppCreateResponse(id, code, "PENDING");
    }

    @Transactional
    public ReviewResponse review(long appId, String action, String reason, Long operatorId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> app = requireApp(tenantId, appId);
        String normalized = action == null ? "" : action.trim().toUpperCase();
        if (!"APPROVE".equals(normalized) && !"REJECT".equals(normalized)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "action 仅支持 APPROVE/REJECT");
        }
        String reviewStatus = "APPROVE".equals(normalized) ? "APPROVED" : "REJECTED";
        String status = "APPROVE".equals(normalized) ? "ENABLED" : "DISABLED";
        int updated = appMapper.updateReview(tenantId, appId, reviewStatus, status, operatorId);
        if (updated == 0) {
            throw new BizException(CommonErrorCode.CONFLICT,
                    "应用当前状态不可审核: " + app.get("review_status"));
        }
        audit("APPROVE".equals(normalized) ? "OPENAPI_APP_APPROVED" : "OPENAPI_APP_REJECTED",
                tenantId, operatorId, String.valueOf(appId));
        return new ReviewResponse(appId, reviewStatus, status);
    }

    @Transactional(readOnly = true)
    public List<PortalAppView> listPortalApps(long ownerId) {
        long tenantId = TenantContext.requireTenantId();
        return appMapper.listByOwner(tenantId, ownerId).stream()
                .map(row -> {
                    long appId = ((Number) row.get("id")).longValue();
                    return new PortalAppView(
                            appId,
                            (String) row.get("code"),
                            (String) row.get("name"),
                            (String) row.get("status"),
                            (String) row.get("review_status"),
                            callLogMapper.countToday(tenantId, appId),
                            quotaMapper.maxDailyLimit(tenantId, appId));
                })
                .toList();
    }

    /** 开发者门户创建应用：归属当前用户，初始待审核/禁用，审核由管理端 1002 完成。 */
    @Transactional
    public AppCreateResponse createPortal(PortalAppCreateRequest request, long ownerId) {
        long tenantId = TenantContext.requireTenantId();
        String name = requireText(request.name(), "name", 128);
        String ownerType = request.ownerType() == null || request.ownerType().isBlank()
                ? "INTERNAL" : request.ownerType().trim().toUpperCase();
        if (!OWNER_TYPES.contains(ownerType)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "owner_type 仅支持 ISV/INTERNAL/CUSTOMER");
        }
        if (appMapper.findByTenantAndName(tenantId, name) != null) {
            throw new BizException(OpenApiErrorCode.APP_EXISTS, "应用已存在: " + name);
        }
        List<String> scopes = normalizeScopes(request.scopes());
        List<String> redirectUris = normalizeRedirectUris(request.redirectUris());
        String code = appMapper.nextCode();
        long id = IdWorker.getId();
        appMapper.insert(id, tenantId, code, name, ownerType, ownerId,
                join(scopes, 1024, "scopes"), join(redirectUris, 1024, "redirect_uris"), ownerId);
        audit("OPENAPI_PORTAL_APP_CREATED", tenantId, ownerId, String.valueOf(id));
        return new AppCreateResponse(id, code, "PENDING");
    }

    @Transactional(readOnly = true)
    public PortalAppDetail detailOwned(long appId, long ownerId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> app = requireOwnedApp(tenantId, appId, ownerId);
        return new PortalAppDetail(
                ((Number) app.get("id")).longValue(),
                (String) app.get("code"),
                (String) app.get("name"),
                (String) app.get("owner_type"),
                (String) app.get("review_status"),
                (String) app.get("status"),
                parseList((String) app.get("scopes")),
                parseList((String) app.get("redirect_uris")),
                toInstant(app.get("created_at")),
                callLogMapper.countToday(tenantId, appId),
                quotaMapper.maxDailyLimit(tenantId, appId));
    }

    @Transactional
    public void updateCallback(long appId, long ownerId, List<String> redirectUris) {
        long tenantId = TenantContext.requireTenantId();
        requireOwnedApp(tenantId, appId, ownerId);
        String joined = join(normalizeRedirectUris(redirectUris), 1024, "redirect_uris");
        if (appMapper.updateCallback(tenantId, appId, ownerId, joined) == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "开放应用不存在: " + appId);
        }
        audit("OPENAPI_PORTAL_CALLBACK_UPDATED", tenantId, ownerId, String.valueOf(appId));
    }

    @Transactional
    public void updateScopes(long appId, long ownerId, List<String> scopes) {
        long tenantId = TenantContext.requireTenantId();
        requireOwnedApp(tenantId, appId, ownerId);
        String joined = join(normalizeScopes(scopes), 1024, "scopes");
        if (appMapper.updateScopes(tenantId, appId, ownerId, joined) == 0) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "开放应用不存在: " + appId);
        }
        audit("OPENAPI_PORTAL_SCOPES_UPDATED", tenantId, ownerId, String.valueOf(appId));
    }

    /** 接口权限目录：来源为 hub_permission 真实 API 权限点，并标注当前应用是否已订阅。 */
    @Transactional(readOnly = true)
    public List<PortalScopeView> listScopes(String domain, List<String> subscribedScopes) {
        Set<String> subscribed = subscribedScopes == null ? Set.of() : Set.copyOf(subscribedScopes);
        return scopeMapper.listOpenableScopes(blankToNull(domain)).stream()
                .map(row -> {
                    String code = (String) row.get("code");
                    return new PortalScopeView(code, (String) row.get("name"),
                            (String) row.get("domain"), subscribed.contains(code));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> subscribedScopes(long appId, long ownerId) {
        long tenantId = TenantContext.requireTenantId();
        Map<String, Object> app = requireOwnedApp(tenantId, appId, ownerId);
        return parseList((String) app.get("scopes"));
    }

    /** 开发者侧强制收敛：应用必须存在且 owner_id 等于当前用户，否则 403。 */
    @Transactional(readOnly = true)
    public Map<String, Object> requireOwnedApp(long appId, long ownerId) {
        return requireOwnedApp(TenantContext.requireTenantId(), appId, ownerId);
    }

    /** 扩展：管理端应用列表（按状态/审核状态过滤，租户隔离）。 */
    @Transactional(readOnly = true)
    public PageResult<AdminAppView> listAdmin(String status, String reviewStatus,
                                              long page, long size) {
        long tenantId = TenantContext.requireTenantId();
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        String statusFilter = blankToNull(status);
        String reviewFilter = blankToNull(reviewStatus);
        List<AdminAppView> records = appMapper.listAdmin(tenantId, statusFilter, reviewFilter,
                        (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> new AdminAppView(
                        ((Number) row.get("id")).longValue(),
                        (String) row.get("code"),
                        (String) row.get("name"),
                        (String) row.get("owner_type"),
                        (String) row.get("review_status"),
                        (String) row.get("status"),
                        toInstant(row.get("created_at"))))
                .toList();
        return PageResult.of(records, appMapper.countAdmin(tenantId, statusFilter, reviewFilter),
                safePage, safeSize);
    }

    Map<String, Object> requireApp(long tenantId, long appId) {
        Map<String, Object> app = appMapper.findById(tenantId, appId);
        if (app == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "开放应用不存在: " + appId);
        }
        return app;
    }

    private Map<String, Object> requireOwnedApp(long tenantId, long appId, long ownerId) {
        Map<String, Object> app = requireApp(tenantId, appId);
        Object owner = app.get("owner_id");
        long actualOwner = owner instanceof Number number ? number.longValue() : -1L;
        if (actualOwner != ownerId) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权访问该应用: " + appId);
        }
        return app;
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (scope != null && !scope.isBlank()) {
                values.add(scope.trim());
            }
        }
        if (values.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = new LinkedHashSet<>(scopeMapper.listOpenableScopes(null).stream()
                .map(row -> (String) row.get("code"))
                .toList());
        for (String scope : values) {
            if (!allowed.contains(scope)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的接口权限: " + scope);
            }
        }
        return List.copyOf(values);
    }

    private List<String> normalizeRedirectUris(List<String> redirectUris) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String uri : redirectUris) {
            if (uri == null || uri.isBlank()) {
                continue;
            }
            String trimmed = uri.trim();
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "回调地址必须以 http:// 或 https:// 开头: " + trimmed);
            }
            if (trimmed.length() > 512) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "单个回调地址长度不能超过 512");
            }
            values.add(trimmed);
        }
        if (values.size() > 10) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "回调地址不能超过 10 个");
        }
        return List.copyOf(values);
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private void audit(String eventType, long tenantId, Long operatorId, String resourceId) {
        TenantContext.Snapshot context = TenantContext.get();
        auditSink.publish(new AuditEvent(UUID.randomUUID().toString(), eventType, "openapi", "WRITE", "MEDIUM",
                tenantId, operatorId, context == null ? null : context.clientType(),
                context == null ? null : context.requestId(), "OPEN_APP", resourceId, Instant.now()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return null;
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    private String join(List<String> values, int maxLength, String field) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = String.join(",", values.stream().map(String::trim).filter(v -> !v.isEmpty()).toList());
        if (joined.length() > maxLength) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 长度不能超过 " + maxLength);
        }
        return joined;
    }
}
