package com.fenglin.openapi.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.service.OpenApiCallLogService;
import com.fenglin.openapi.service.OpenApiDocService;
import com.fenglin.openapi.service.OpenApiKeyService;
import com.fenglin.openapi.service.OpenApiQuotaRequestService;
import com.fenglin.openapi.service.OpenApiQuotaService;
import com.fenglin.openapi.service.OpenApiSandboxService;
import com.fenglin.openapi.service.OpenAppService;
import com.fenglin.openapi.web.dto.OpenApiDtos.AppCreateResponse;
import com.fenglin.openapi.web.dto.OpenApiDtos.CallLogView;
import com.fenglin.openapi.web.dto.OpenApiDtos.KeyRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.KeyResponse;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalApiDocView;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalAppCreateRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalAppDetail;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalAppView;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalCallbackRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalKeyView;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalQuotaView;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalScopeRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalScopeView;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaApplyRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaRequestView;
import com.fenglin.openapi.web.dto.OpenApiDtos.SandboxCallRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.SandboxCallResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 开发者门户接口：只暴露当前用户自己的应用，服务端按 owner_id 强制收敛。
 * <p>
 * 1001/1003/1004/1005 的原始接口挂在管理端（platform:openapi:*），PORTAL 客户端不可访问；
 * 这里提供门户侧等价能力，并使用 biz:openapi:* 权限码。
 */
@RestController
public class OpenApiPortalController {

    /** 契约缺口兜底：1006 未声明权限码，补充 biz:openapi:portal:view。 */
    private static final String PORTAL_VIEW = "biz:openapi:portal:view";
    private static final String APP_MANAGE = "biz:openapi:app:manage";
    private static final String KEY_MANAGE = "biz:openapi:key:manage";
    private static final String QUOTA_VIEW = "biz:openapi:quota:view";
    private static final String QUOTA_APPLY = "biz:openapi:quota:apply";
    private static final String CALL_LOG_VIEW = "biz:openapi:calllog:view";
    private static final String SANDBOX_CALL = "biz:openapi:sandbox:call";

    private final OpenAppService appService;
    private final OpenApiKeyService keyService;
    private final OpenApiQuotaService quotaService;
    private final OpenApiCallLogService callLogService;
    private final OpenApiDocService docService;
    private final OpenApiSandboxService sandboxService;
    private final OpenApiQuotaRequestService quotaRequestService;

    public OpenApiPortalController(OpenAppService appService,
                                   OpenApiKeyService keyService,
                                   OpenApiQuotaService quotaService,
                                   OpenApiCallLogService callLogService,
                                   OpenApiDocService docService,
                                   OpenApiSandboxService sandboxService,
                                   OpenApiQuotaRequestService quotaRequestService) {
        this.appService = appService;
        this.keyService = keyService;
        this.quotaService = quotaService;
        this.callLogService = callLogService;
        this.docService = docService;
        this.sandboxService = sandboxService;
        this.quotaRequestService = quotaRequestService;
    }

    /** HUB-API-1006 开发者门户应用列表（仅当前用户创建的应用）。 */
    @GetMapping("/openapi/portal/apps")
    public ApiResponse<List<PortalAppView>> portalApps(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, PORTAL_VIEW);
        return ApiResponse.success(appService.listPortalApps(requireUserId()));
    }

    /** HUB-API-1001 门户侧应用注册：归属当前用户，初始待审核。 */
    @PostMapping("/openapi/portal/apps")
    public ApiResponse<AppCreateResponse> createPortalApp(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PortalAppCreateRequest request) {
        PermissionGuard.require(permissions, APP_MANAGE);
        return ApiResponse.success(appService.createPortal(request, requireUserId()));
    }

    /** 应用详情（回调地址、已订阅接口、用量概览）；仅应用归属人可查看。 */
    @GetMapping("/openapi/portal/apps/{id}")
    public ApiResponse<PortalAppDetail> portalAppDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, PORTAL_VIEW);
        return ApiResponse.success(appService.detailOwned(id, requireUserId()));
    }

    /** 回调地址配置。 */
    @PutMapping("/openapi/portal/apps/{id}/callback")
    public ApiResponse<PortalAppDetail> updateCallback(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody PortalCallbackRequest request) {
        PermissionGuard.require(permissions, APP_MANAGE);
        long userId = requireUserId();
        appService.updateCallback(id, userId, request.redirectUris());
        return ApiResponse.success(appService.detailOwned(id, userId));
    }

    /** 接口权限目录：来自 hub_permission 真实 API 权限点；传 app_id 时标注订阅状态。 */
    @GetMapping("/openapi/portal/scopes")
    public ApiResponse<List<PortalScopeView>> scopes(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(name = "domain", required = false) String domain,
            @RequestParam(name = "app_id", required = false) Long appId) {
        PermissionGuard.require(permissions, PORTAL_VIEW);
        List<String> subscribed = appId == null
                ? List.of()
                : appService.subscribedScopes(appId, requireUserId());
        return ApiResponse.success(appService.listScopes(domain, subscribed));
    }

    /** 接口权限订阅。 */
    @PutMapping("/openapi/portal/apps/{id}/scopes")
    public ApiResponse<PortalAppDetail> updateScopes(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody PortalScopeRequest request) {
        PermissionGuard.require(permissions, APP_MANAGE);
        long userId = requireUserId();
        appService.updateScopes(id, userId, request.scopes());
        return ApiResponse.success(appService.detailOwned(id, userId));
    }

    /** 接口文档元数据：返回平台已发布的开放接口清单；传 app_id 时标注订阅状态。 */
    @GetMapping("/openapi/portal/api-docs")
    public ApiResponse<List<PortalApiDocView>> apiDocs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(name = "domain", required = false) String domain,
            @RequestParam(name = "app_id", required = false) Long appId) {
        PermissionGuard.require(permissions, PORTAL_VIEW);
        List<String> subscribed = appId == null
                ? List.of()
                : appService.subscribedScopes(appId, requireUserId());
        return ApiResponse.success(docService.listPublished(domain, subscribed));
    }

    /** 在线联调沙箱：服务端用应用密钥签名后真实调用，AppSecret 不出服务端。 */
    @PostMapping("/openapi/portal/apps/{id}/sandbox")
    public ApiResponse<SandboxCallResponse> sandbox(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody SandboxCallRequest request) {
        PermissionGuard.require(permissions, SANDBOX_CALL);
        return ApiResponse.success(sandboxService.call(id, requireUserId(), request));
    }

    /** 密钥列表：AppSecret 仅返回掩码。 */
    @GetMapping("/openapi/portal/apps/{id}/keys")
    public ApiResponse<List<PortalKeyView>> keys(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, KEY_MANAGE);
        appService.requireOwnedApp(id, requireUserId());
        return ApiResponse.success(keyService.listKeys(id));
    }

    /** 签发/轮换密钥：AppSecret 明文仅本次返回，之后仅可看到掩码。 */
    @PostMapping("/openapi/portal/apps/{id}/keys")
    public ApiResponse<KeyResponse> issueKey(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody(required = false) KeyRequest request) {
        PermissionGuard.require(permissions, KEY_MANAGE);
        long userId = requireUserId();
        return ApiResponse.success(keyService.issueOwned(id,
                request == null ? null : request.action(),
                request == null ? null : request.graceHours(),
                userId));
    }

    /** 调用日志：路径内 app_id 必填，先校验归属再按应用+租户查询。 */
    @GetMapping("/openapi/portal/apps/{id}/call-logs")
    public ApiResponse<PageResult<CallLogView>> callLogs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestParam(name = "api_path", required = false) String apiPath,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size) {
        PermissionGuard.require(permissions, CALL_LOG_VIEW);
        appService.requireOwnedApp(id, requireUserId());
        return ApiResponse.success(callLogService.search(id, apiPath, status, from, to, page, size));
    }

    /** 配额、当日用量与限流状态（只读；配置仍由 1004 管理端完成）。 */
    @GetMapping("/openapi/portal/apps/{id}/quotas")
    public ApiResponse<List<PortalQuotaView>> quotas(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, QUOTA_VIEW);
        appService.requireOwnedApp(id, requireUserId());
        return ApiResponse.success(quotaService.listOwned(id));
    }

    /** OPEN-03 配额自助申请（提交后由平台管理端审批）。 */
    @PostMapping("/openapi/portal/quotas/requests")
    public ApiResponse<QuotaRequestView> applyQuota(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody QuotaApplyRequest request) {
        PermissionGuard.require(permissions, QUOTA_APPLY);
        return ApiResponse.success(quotaRequestService.apply(request, requireUserId()));
    }

    /** OPEN-03 我的配额申请列表。 */
    @GetMapping("/openapi/portal/quotas/requests")
    public ApiResponse<PageResult<QuotaRequestView>> myQuotaRequests(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        PermissionGuard.require(permissions, QUOTA_APPLY);
        return ApiResponse.success(quotaRequestService.listMine(requireUserId(), page, size));
    }

    private long requireUserId() {
        TenantContext.Snapshot context = TenantContext.get();
        if (context == null || context.userId() == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "缺少用户身份");
        }
        return context.userId();
    }
}
