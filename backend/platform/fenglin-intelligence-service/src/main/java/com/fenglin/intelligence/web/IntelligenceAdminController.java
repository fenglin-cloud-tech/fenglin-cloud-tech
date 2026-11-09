package com.fenglin.intelligence.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.intelligence.service.IntelligenceAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 云智运营/双模式接口：能力授权与计量只读代理平台 AI 中台（hub_ai_* 为唯一源）。
 */
@RestController
@RequestMapping("/api/intelligence/v1")
public class IntelligenceAdminController {

    private final IntelligenceAdminService service;

    public IntelligenceAdminController(IntelligenceAdminService service) {
        this.service = service;
    }

    @GetMapping("/admin/ai/capability-grants")
    public ApiResponse<Map<String, Object>> grants(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(name = "host_product", required = false) String hostProduct) {
        PermissionGuard.require(permissions, "tenant:ai:grant:manage");
        return ApiResponse.success(service.capabilityGrants(hostProduct));
    }

    @GetMapping("/admin/ai/metering")
    public ApiResponse<Map<String, Object>> metering(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(required = false) String mode,
            @RequestParam(name = "host_product", required = false) String hostProduct,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        PermissionGuard.require(permissions, "tenant:ai:usage:view");
        return ApiResponse.success(service.usage(mode, hostProduct, from, to));
    }

    @PostMapping("/admin/ai/mode:validate")
    public ApiResponse<Map<String, Object>> validateMode(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, "tenant:ai:usage:view");
        return ApiResponse.success(service.validateMode());
    }

    @GetMapping("/admin/ai/mode")
    public ApiResponse<Map<String, Object>> modeSummary(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, "tenant:ai:usage:view");
        return ApiResponse.success(service.modeSummary());
    }
}
