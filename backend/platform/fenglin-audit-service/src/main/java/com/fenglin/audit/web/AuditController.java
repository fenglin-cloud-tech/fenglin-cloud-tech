package com.fenglin.audit.web;

import com.fenglin.audit.service.AuditAlertRuleService;
import com.fenglin.audit.service.AuditExportService;
import com.fenglin.audit.service.AuditService;
import com.fenglin.audit.web.dto.AlertRuleUpdateRequest;
import com.fenglin.audit.web.dto.AlertRuleView;
import com.fenglin.audit.web.dto.AuditExportTaskView;
import com.fenglin.audit.web.dto.AuditLogView;
import com.fenglin.audit.web.dto.ChainVerifyResult;
import com.fenglin.audit.web.dto.ExportAuditRequest;
import com.fenglin.audit.web.dto.RecordAuditRequest;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 审计：内部写入 /internal/v1/audit/logs，管理端查询 /api/hub/v1/admin/audits（HUB-API-601/602 口径）。
 */
@RestController
public class AuditController {

    private static final String READ_PERMISSION = "hub:audit:read";
    private static final String EXPORT_PERMISSION = "platform:audit:export";
    private static final String ALERT_PERMISSION = "platform:audit:alert:manage";

    private final AuditService auditService;
    private final AuditExportService auditExportService;
    private final AuditAlertRuleService auditAlertRuleService;

    public AuditController(AuditService auditService, AuditExportService auditExportService,
                           AuditAlertRuleService auditAlertRuleService) {
        this.auditService = auditService;
        this.auditExportService = auditExportService;
        this.auditAlertRuleService = auditAlertRuleService;
    }

    @PostMapping("/internal/v1/audit/logs")
    public ApiResponse<Map<String, Object>> record(@Valid @RequestBody RecordAuditRequest request) {
        return ApiResponse.success(auditService.record(request));
    }

    @GetMapping("/api/hub/v1/admin/audits")
    public ApiResponse<PageResult<AuditLogView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PermissionGuard.require(permissions, READ_PERMISSION);
        List<AuditLogView> records = auditService.list(eventType, operatorId, targetType, targetId,
                requestId, result, from, to, page, size);
        return ApiResponse.success(PageResult.of(records,
                auditService.count(eventType, operatorId, targetType, targetId, requestId, result, from, to),
                Math.max(page, 1), Math.min(Math.max(size, 1), 200)));
    }

    @GetMapping("/api/hub/v1/admin/audits/{id}")
    public ApiResponse<AuditLogView> get(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, READ_PERMISSION);
        return ApiResponse.success(auditService.get(id));
    }

    /**
     * 哈希链校验：租户缺省取上下文租户，跨租户查询由 RLS 拦截（结果为 checked=0）。
     */
    @GetMapping("/api/hub/v1/admin/audits/chain/verify")
    public ApiResponse<ChainVerifyResult> verifyChain(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        PermissionGuard.require(permissions, READ_PERMISSION);
        Long contextTenantId = TenantContext.getTenantId();
        long resolvedTenantId = tenantId != null ? tenantId : (contextTenantId == null ? 0L : contextTenantId);
        return ApiResponse.success(auditService.verifyChain(resolvedTenantId, from, to));
    }

    /**
     * HUB-API-802 审计日志导出：异步任务，返回 taskId/status。
     */
    @PostMapping("/api/hub/v1/admin/audit/logs:export")
    public ApiResponse<AuditExportTaskView> export(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody(required = false) ExportAuditRequest request) {
        PermissionGuard.require(permissions, EXPORT_PERMISSION);
        ExportAuditRequest normalized = request == null
                ? new ExportAuditRequest(null, null, null, null) : request;
        Long operatorId = userId != null ? userId
                : (TenantContext.get() == null ? null : TenantContext.get().userId());
        return ApiResponse.success(auditExportService.create(normalized, operatorId));
    }

    /**
     * 导出任务查询（契约缺口：802 仅定义创建，补充查询用于获取任务状态）。
     */
    @GetMapping("/api/hub/v1/admin/audit/export-tasks/{taskId}")
    public ApiResponse<AuditExportTaskView> exportTask(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long taskId) {
        PermissionGuard.require(permissions, EXPORT_PERMISSION);
        return ApiResponse.success(auditExportService.get(taskId));
    }

    /**
     * 导出文件下载（契约缺口：802 未定义下载入口，补充 export-tasks 下载端点）。
     */
    @GetMapping("/api/hub/v1/admin/audit/export-tasks/{taskId}/download")
    public ResponseEntity<byte[]> downloadExport(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long taskId) {
        PermissionGuard.require(permissions, EXPORT_PERMISSION);
        AuditExportService.ExportFile file = auditExportService.download(taskId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    /**
     * HUB-API-805 我的操作记录：仅返回当前登录用户 operator_id 匹配的记录（租户由 RLS 约束）。
     */
    @GetMapping("/api/hub/v1/audit/my-records")
    public ApiResponse<PageResult<AuditLogView>> myRecords(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long resolvedUserId = userId != null ? userId
                : (TenantContext.get() == null ? null : TenantContext.get().userId());
        if (resolvedUserId == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "缺少当前用户身份");
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        List<AuditLogView> records = auditService.list(null, resolvedUserId, null, null, null, null,
                from, to, normalizedPage, normalizedSize);
        return ApiResponse.success(PageResult.of(records,
                auditService.count(null, resolvedUserId, null, null, null, null, from, to),
                normalizedPage, normalizedSize));
    }

    /**
     * HUB-API-804 审计告警配置：items 全量替换，强制幂等。
     */
    @PutMapping("/api/hub/v1/admin/audit/alert-rules")
    public ApiResponse<List<AlertRuleView>> updateAlertRules(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody AlertRuleUpdateRequest request) {
        PermissionGuard.require(permissions, ALERT_PERMISSION);
        Long operatorId = userId != null ? userId
                : (TenantContext.get() == null ? null : TenantContext.get().userId());
        return ApiResponse.success(auditAlertRuleService.replace(request, operatorId));
    }

    /**
     * 告警规则查询（契约缺口：804 仅定义 PUT，补充 GET 便于回显）。
     */
    @GetMapping("/api/hub/v1/admin/audit/alert-rules")
    public ApiResponse<List<AlertRuleView>> listAlertRules(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, ALERT_PERMISSION);
        return ApiResponse.success(auditAlertRuleService.list());
    }
}
