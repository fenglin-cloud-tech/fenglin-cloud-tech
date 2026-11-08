package com.fenglin.openapi.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.openapi.service.OpenAppService;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalAppView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OpenApiPortalController {

    /** 契约缺口兜底：1006 未声明权限码，补充 biz:openapi:portal:view。 */
    private static final String PORTAL_PERMISSION = "biz:openapi:portal:view";

    private final OpenAppService appService;

    public OpenApiPortalController(OpenAppService appService) {
        this.appService = appService;
    }

    /** HUB-API-1006 开发者门户应用列表。 */
    @GetMapping("/openapi/portal/apps")
    public ApiResponse<List<PortalAppView>> portalApps(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, PORTAL_PERMISSION);
        return ApiResponse.success(appService.listPortalApps());
    }
}
