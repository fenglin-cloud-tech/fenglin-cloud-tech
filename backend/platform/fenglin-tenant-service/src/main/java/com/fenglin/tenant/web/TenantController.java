package com.fenglin.tenant.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.tenant.service.TenantService;
import com.fenglin.tenant.web.dto.CreateTenantRequest;
import com.fenglin.tenant.web.dto.CreateTenantResult;
import com.fenglin.tenant.web.dto.TenantView;
import com.fenglin.tenant.web.dto.UpdateTenantStatusRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户治理 HUB-API-201/202/203/204。写接口要求 X-Permissions 含 platform:tenant:manage。
 */
@RestController
@RequestMapping("/api/hub/v1/admin/tenants")
public class TenantController {

    private static final String WRITE_PERMISSION = "platform:tenant:manage";

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ApiResponse<CreateTenantResult> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @Valid @RequestBody CreateTenantRequest request) {
        PermissionGuard.require(permissions, WRITE_PERMISSION);
        return ApiResponse.success(tenantService.create(request));
    }

    /**
     * FIN-15：存量租户财务域补齐（幂等，可重复调用）。
     */
    @PostMapping("/finance-init")
    public ApiResponse<java.util.Map<String, Object>> backfillFinance(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, WRITE_PERMISSION);
        return ApiResponse.success(tenantService.backfillFinance());
    }

    @GetMapping("/{id}")
    public ApiResponse<TenantView> get(@PathVariable long id) {
        return ApiResponse.success(tenantService.get(id));
    }

    @GetMapping
    public ApiResponse<PageResult<TenantView>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        List<TenantView> records = tenantService.list(status, keyword, page, size);
        return ApiResponse.success(PageResult.of(records, tenantService.count(status, keyword),
                Math.max(page, 1), Math.min(Math.max(size, 1), 200)));
    }

    /** 租户上下文贯穿验证：网关注入 X-Tenant-Id 后，下游只能取到当前租户（契约缺口扩展） */
    @GetMapping("/current")
    public ApiResponse<TenantView> current() {
        return ApiResponse.success(tenantService.current());
    }

    @PostMapping("/{id}/status")
    public ApiResponse<TenantView> updateStatus(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody UpdateTenantStatusRequest request) {
        PermissionGuard.require(permissions, WRITE_PERMISSION);
        return ApiResponse.success(tenantService.updateStatus(id, request.action(), request.reason()));
    }
}
