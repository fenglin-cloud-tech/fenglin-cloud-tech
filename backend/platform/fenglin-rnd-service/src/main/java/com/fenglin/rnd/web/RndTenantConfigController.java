package com.fenglin.rnd.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.rnd.service.TenantConfigService;
import com.fenglin.rnd.web.dto.RndDtos.TenantConfigUpdateRequest;
import com.fenglin.rnd.web.dto.RndDtos.TenantConfigView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户集成配置接口（文档 API-59 管理端预留能力的落地实现）。
 */
@RestController
@RequestMapping("/api/rnd/v1/tenant-config")
public class RndTenantConfigController {

    private static final String INTEGRATION_MANAGE = "tenant:devops:integration:manage";

    private final TenantConfigService tenantConfigService;

    public RndTenantConfigController(TenantConfigService tenantConfigService) {
        this.tenantConfigService = tenantConfigService;
    }

    @GetMapping
    public ApiResponse<TenantConfigView> get(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, INTEGRATION_MANAGE);
        return ApiResponse.success(tenantConfigService.get());
    }

    @PutMapping
    public ApiResponse<TenantConfigView> update(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody TenantConfigUpdateRequest request) {
        PermissionGuard.require(permissions, INTEGRATION_MANAGE);
        return ApiResponse.success(tenantConfigService.update(request));
    }
}
