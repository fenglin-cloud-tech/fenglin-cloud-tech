package com.fenglin.audit.web;

import com.fenglin.audit.service.AuditService;
import com.fenglin.audit.web.dto.AuditLogView;
import com.fenglin.audit.web.dto.RecordAuditRequest;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 审计：内部写入 /internal/v1/audit/logs，管理端查询 /api/hub/v1/admin/audits（HUB-API-601/602 口径）。
 */
@RestController
public class AuditController {

    private static final String READ_PERMISSION = "hub:audit:read";

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
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
}
