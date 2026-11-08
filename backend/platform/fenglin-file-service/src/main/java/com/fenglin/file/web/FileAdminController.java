package com.fenglin.file.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.file.service.FilePolicyService;
import com.fenglin.file.web.dto.FilePolicyRequest;
import com.fenglin.file.web.dto.FilePolicyView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileAdminController {

    private static final String POLICY_PERMISSION = "tenant:file:policy:manage";

    private final FilePolicyService policyService;

    public FileAdminController(FilePolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/api/hub/v1/admin/file/policies")
    public ApiResponse<FilePolicyView> get(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, POLICY_PERMISSION);
        return ApiResponse.success(policyService.view(TenantContext.requireTenantId()));
    }

    @PutMapping("/api/hub/v1/admin/file/policies")
    public ApiResponse<FilePolicyView> update(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody FilePolicyRequest request) {
        PermissionGuard.require(permissions, POLICY_PERMISSION);
        return ApiResponse.success(policyService.save(TenantContext.requireTenantId(), request));
    }
}
