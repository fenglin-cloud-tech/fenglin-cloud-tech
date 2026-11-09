package com.fenglin.service.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.service.service.AdminConfigService;
import com.fenglin.service.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端：工单类型、处理组、值班、自动分派、资产提醒、RPA 重试策略、目录与 SLA。
 */
@RestController
@RequestMapping("/api/service/v1/admin")
public class AdminConfigController {

    private static final String CONFIG_VIEW = "tenant:service:config:view";
    private static final String DESK_CONFIG = "tenant:service:desk:config";
    private static final String CATALOG_CONFIG = "tenant:service:catalog:config";
    private static final String SLA_CONFIG = "tenant:service:sla:config";
    private static final String ASSET_CONFIG = "tenant:service:asset:config";
    private static final String RPA_CONFIG = "tenant:service:rpa:config";
    private static final String REPORT_CONFIG = "tenant:service:report:config";

    private final AdminConfigService adminConfigService;
    private final CatalogService catalogService;

    public AdminConfigController(AdminConfigService adminConfigService, CatalogService catalogService) {
        this.adminConfigService = adminConfigService;
        this.catalogService = catalogService;
    }

    @PutMapping("/config/ticket-types")
    public ApiResponse<Map<String, Object>> ticketTypes(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, DESK_CONFIG);
        return ApiResponse.success(adminConfigService.save("TICKET_TYPE", "default", body));
    }

    @PutMapping("/config/groups")
    public ApiResponse<Map<String, Object>> groups(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, DESK_CONFIG);
        return ApiResponse.success(adminConfigService.save("HANDLE_GROUP", "default", body));
    }

    @PutMapping("/config/duty")
    public ApiResponse<Map<String, Object>> duty(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, DESK_CONFIG);
        return ApiResponse.success(adminConfigService.save("DUTY_ROSTER", "default", body));
    }

    @PutMapping("/config/dispatch-rule")
    public ApiResponse<Map<String, Object>> dispatchRule(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, DESK_CONFIG);
        return ApiResponse.success(adminConfigService.save("DISPATCH_RULE", "default", body));
    }

    @PutMapping("/config/asset-reminder")
    public ApiResponse<Map<String, Object>> assetReminder(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, ASSET_CONFIG);
        return ApiResponse.success(adminConfigService.save("ASSET_REMINDER", "default", body));
    }

    @PutMapping("/config/retry-policy")
    public ApiResponse<Map<String, Object>> retryPolicy(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, RPA_CONFIG);
        return ApiResponse.success(adminConfigService.save("RPA_RETRY_POLICY", "default", body));
    }

    @PutMapping("/config/service-report")
    public ApiResponse<Map<String, Object>> serviceReport(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, REPORT_CONFIG);
        return ApiResponse.success(adminConfigService.save("SERVICE_REPORT", "default", body));
    }

    @GetMapping("/config/{configType}")
    public ApiResponse<List<Map<String, Object>>> configs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("configType") String configType) {
        PermissionGuard.require(permissions, CONFIG_VIEW);
        return ApiResponse.success(adminConfigService.list(configType.toUpperCase()));
    }

    @PostMapping("/catalog")
    public ApiResponse<Map<String, Object>> saveCatalog(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, CATALOG_CONFIG);
        return ApiResponse.success(catalogService.saveCatalog(body));
    }

    @GetMapping("/sla-policies")
    public ApiResponse<List<Map<String, Object>>> policies(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, SLA_CONFIG);
        return ApiResponse.success(catalogService.listPolicies());
    }

    @PostMapping("/sla-policies")
    public ApiResponse<Map<String, Object>> savePolicy(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody Map<String, Object> body) {
        PermissionGuard.require(permissions, SLA_CONFIG);
        return ApiResponse.success(catalogService.savePolicy(body));
    }
}
