package com.fenglin.openapi.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.openapi.service.OpenApiCallLogService;
import com.fenglin.openapi.service.OpenApiKeyService;
import com.fenglin.openapi.service.OpenApiQuotaRequestService;
import com.fenglin.openapi.service.OpenApiQuotaService;
import com.fenglin.openapi.service.OpenAppService;
import com.fenglin.openapi.web.dto.OpenApiDtos.AdminAppView;
import com.fenglin.openapi.web.dto.OpenApiDtos.AppCreateRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.AppCreateResponse;
import com.fenglin.openapi.web.dto.OpenApiDtos.CallLogView;
import com.fenglin.openapi.web.dto.OpenApiDtos.KeyRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.KeyResponse;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaRequestReview;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaRequestView;
import com.fenglin.openapi.web.dto.OpenApiDtos.QuotaView;
import com.fenglin.openapi.web.dto.OpenApiDtos.ReviewRequest;
import com.fenglin.openapi.web.dto.OpenApiDtos.ReviewResponse;
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

@RestController
public class OpenApiAdminController {

    private static final String REVIEW_PERMISSION = "platform:openapi:app:review";
    private static final String KEY_PERMISSION = "platform:openapi:key:manage";
    private static final String QUOTA_PERMISSION = "platform:openapi:quota:manage";
    private static final String QUOTA_APPROVE_PERMISSION = "platform:openapi:quota:approve";
    private static final String CALL_LOG_PERMISSION = "platform:openapi:calllog:view";

    private final OpenAppService appService;
    private final OpenApiKeyService keyService;
    private final OpenApiQuotaService quotaService;
    private final OpenApiCallLogService callLogService;
    private final OpenApiQuotaRequestService quotaRequestService;

    public OpenApiAdminController(OpenAppService appService,
                                  OpenApiKeyService keyService,
                                  OpenApiQuotaService quotaService,
                                  OpenApiCallLogService callLogService,
                                  OpenApiQuotaRequestService quotaRequestService) {
        this.appService = appService;
        this.keyService = keyService;
        this.quotaService = quotaService;
        this.callLogService = callLogService;
        this.quotaRequestService = quotaRequestService;
    }

    /** HUB-API-1001 开放应用注册。 */
    @PostMapping("/api/hub/v1/admin/openapi/apps")
    public ApiResponse<AppCreateResponse> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody AppCreateRequest request) {
        PermissionGuard.require(permissions, REVIEW_PERMISSION);
        return ApiResponse.success(appService.create(request, operator(userId)));
    }

    /** HUB-API-1002 开放应用审核。 */
    @PostMapping("/api/hub/v1/admin/openapi/apps/{id}:review")
    public ApiResponse<ReviewResponse> review(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable long id,
            @RequestBody ReviewRequest request) {
        PermissionGuard.require(permissions, REVIEW_PERMISSION);
        return ApiResponse.success(appService.review(id, request.action(), request.reason(), operator(userId)));
    }

    /** HUB-API-1003 密钥生成/轮换：AppSecret 仅本次回显。 */
    @PostMapping("/api/hub/v1/admin/openapi/apps/{id}/keys")
    public ApiResponse<KeyResponse> issueKey(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable long id,
            @RequestBody(required = false) KeyRequest request) {
        PermissionGuard.require(permissions, KEY_PERMISSION);
        return ApiResponse.success(keyService.issue(id,
                request == null ? null : request.action(),
                request == null ? null : request.graceHours(),
                operator(userId)));
    }

    /** HUB-API-1004 配额配置。 */
    @PutMapping("/api/hub/v1/admin/openapi/quotas")
    public ApiResponse<List<QuotaView>> upsertQuota(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody QuotaRequest request) {
        PermissionGuard.require(permissions, QUOTA_PERMISSION);
        return ApiResponse.success(quotaService.upsert(request, operator(userId)));
    }

    /** HUB-API-1005 调用日志查询。 */
    @GetMapping("/api/hub/v1/admin/openapi/call-logs")
    public ApiResponse<PageResult<CallLogView>> callLogs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) String apiPath,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        PermissionGuard.require(permissions, CALL_LOG_PERMISSION);
        return ApiResponse.success(callLogService.search(appId, apiPath, status, from, to, page, size));
    }

    /** 扩展：管理端开放应用列表（1006 仅定义开发者门户视角）。 */
    @GetMapping("/api/hub/v1/admin/openapi/apps")
    public ApiResponse<PageResult<AdminAppView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        PermissionGuard.require(permissions, REVIEW_PERMISSION);
        return ApiResponse.success(appService.listAdmin(status, reviewStatus, page, size));
    }

    /** OPEN-03 配额申请审批列表（待审批优先）。 */
    @GetMapping("/api/hub/v1/admin/openapi/quota-requests")
    public ApiResponse<PageResult<QuotaRequestView>> quotaRequests(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        PermissionGuard.require(permissions, QUOTA_APPROVE_PERMISSION);
        return ApiResponse.success(quotaRequestService.listForReview(status, page, size));
    }

    /** OPEN-03 配额申请审批：APPROVE 同步写入配额，REJECT 必须带意见。 */
    @PostMapping("/api/hub/v1/admin/openapi/quota-requests/{id}/review")
    public ApiResponse<QuotaRequestView> reviewQuotaRequest(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable long id,
            @RequestBody QuotaRequestReview request) {
        PermissionGuard.require(permissions, QUOTA_APPROVE_PERMISSION);
        Long operatorId = operator(userId);
        if (operatorId == null) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "缺少用户身份");
        }
        return ApiResponse.success(quotaRequestService.review(id, request, operatorId));
    }

    private Long operator(Long userId) {
        if (userId != null) {
            return userId;
        }
        TenantContext.Snapshot context = TenantContext.get();
        return context == null ? null : context.userId();
    }
}
