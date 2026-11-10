package com.fenglin.tenant.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.tenant.service.PlatformAdminService;
import com.fenglin.tenant.web.dto.GrantPlatformAdminRequest;
import com.fenglin.tenant.web.dto.PlatformAdminView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台管理员分配/回收/查询。
 *
 * <p>只有已持有平台管理权限（platform:tenant:manage）的账号可调用；
 * 目标用户取当前租户（网关 X-Tenant-Id），不允许跨租户操作。</p>
 */
@RestController
@RequestMapping("/api/hub/v1/admin/platform-admins")
public class PlatformAdminController {

    private static final String PERMISSION = "platform:tenant:manage";

    private final PlatformAdminService platformAdminService;

    public PlatformAdminController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping("/{userId}")
    public ApiResponse<PlatformAdminView> status(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long userId) {
        PermissionGuard.require(permissions, PERMISSION);
        return ApiResponse.success(platformAdminService.status(userId));
    }

    @PostMapping
    public ApiResponse<PlatformAdminView> grant(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @Valid @RequestBody GrantPlatformAdminRequest request) {
        PermissionGuard.require(permissions, PERMISSION);
        return ApiResponse.success(platformAdminService.grant(request.userId()));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<PlatformAdminView> revoke(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long userId) {
        PermissionGuard.require(permissions, PERMISSION);
        return ApiResponse.success(platformAdminService.revoke(userId));
    }
}
