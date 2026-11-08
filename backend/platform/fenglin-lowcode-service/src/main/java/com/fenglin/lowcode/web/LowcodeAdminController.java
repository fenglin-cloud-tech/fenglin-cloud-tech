package com.fenglin.lowcode.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.lowcode.service.LowcodeAppService;
import com.fenglin.lowcode.web.dto.LowcodeDtos.AppCreateRequest;
import com.fenglin.lowcode.web.dto.LowcodeDtos.AppView;
import com.fenglin.lowcode.web.dto.LowcodeDtos.PublishResult;
import com.fenglin.lowcode.web.dto.LowcodeDtos.RollbackRequest;
import com.fenglin.lowcode.web.dto.LowcodeDtos.VersionView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LowcodeAdminController {

    private static final String DESIGN_PERMISSION = "tenant:lowcode:app:design";
    private static final String PUBLISH_PERMISSION = "tenant:lowcode:app:publish";

    private final LowcodeAppService appService;

    public LowcodeAdminController(LowcodeAppService appService) {
        this.appService = appService;
    }

    /** HUB-API-1201 低代码应用创建。 */
    @PostMapping("/api/hub/v1/admin/lowcode/apps")
    public ApiResponse<AppView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody AppCreateRequest request) {
        PermissionGuard.require(permissions, DESIGN_PERMISSION);
        return ApiResponse.success(appService.create(request, operator(userId)));
    }

    /** HUB-API-1202 低代码应用发布（重复发布幂等返回当前版本）。 */
    @PostMapping("/api/hub/v1/admin/lowcode/apps/{id}:publish")
    public ApiResponse<PublishResult> publish(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable long id) {
        PermissionGuard.require(permissions, PUBLISH_PERMISSION);
        return ApiResponse.success(appService.publish(id, operator(userId)));
    }

    /** HUB-API-1203 低代码应用列表（总纲标注无权限码）。 */
    @GetMapping("/api/hub/v1/admin/lowcode/apps")
    public ApiResponse<PageResult<AppView>> list(
            @RequestParam(required = false) String env,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(appService.list(env, status, page, size));
    }

    /** 扩展接口：版本列表（验收 3“版本回滚”前置）。 */
    @GetMapping("/api/hub/v1/admin/lowcode/apps/{id}/versions")
    public ApiResponse<List<VersionView>> versions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, DESIGN_PERMISSION);
        return ApiResponse.success(appService.versions(id));
    }

    /** 扩展接口：版本回滚（验收 3 要求，总纲无接口编号）。 */
    @PostMapping("/api/hub/v1/admin/lowcode/apps/{id}:rollback")
    public ApiResponse<AppView> rollback(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable long id,
            @RequestBody RollbackRequest request) {
        PermissionGuard.require(permissions, PUBLISH_PERMISSION);
        return ApiResponse.success(appService.rollback(id, request == null ? null : request.versionNo(),
                operator(userId)));
    }

    private Long operator(Long userId) {
        if (userId != null) {
            return userId;
        }
        TenantContext.Snapshot context = TenantContext.get();
        return context == null ? null : context.userId();
    }
}
