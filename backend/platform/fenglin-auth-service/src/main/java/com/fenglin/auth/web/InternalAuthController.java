package com.fenglin.auth.web;

import com.fenglin.auth.service.AuthService;
import com.fenglin.auth.service.PasswordResetService;
import com.fenglin.auth.web.dto.PrincipalPermissionPayload;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.tenant.TenantHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 网关使用的内部契约：令牌撤销与会话状态校验。
 * 《接口文档》未定义该内部接口（INT-1xx 未覆盖），属 P1 契约缺口，待补充。
 */
@RestController
@RequestMapping("/internal/v1/auth")
public class InternalAuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public InternalAuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/revocations/{jti}")
    public ApiResponse<Map<String, Boolean>> revoked(@PathVariable String jti) {
        return ApiResponse.success(Map.of("revoked", authService.isRevokedJti(jti)));
    }

    @GetMapping("/sessions/{sessionId}/status")
    public ApiResponse<Map<String, Boolean>> sessionStatus(@PathVariable String sessionId) {
        return ApiResponse.success(Map.of("online", authService.isSessionOnline(sessionId)));
    }

    /**
     * IAM 管理员重置口令后调用：吊销该用户全部会话与刷新令牌（复用登出链路）。
     */
    @PostMapping("/users/{userId}/sessions:revoke")
    public ApiResponse<Map<String, Integer>> revokeUserSessions(
            @RequestHeader(TenantHeaders.TENANT_ID) long tenantId,
            @PathVariable long userId) {
        return ApiResponse.success(Map.of("closed_sessions", authService.revokeAllSessions(tenantId, userId)));
    }

    /**
     * PLAT-10：网关权限解析回源。pv 为网关期望校验的版本号（提示值），
     * 响应中的 permission_version 为权威值，网关以响应为准刷新缓存。
     */
    @GetMapping("/principals/{userId}/permissions")
    public ApiResponse<PrincipalPermissionPayload> principalPermissions(
            @PathVariable long userId,
            @RequestParam(required = false) Long pv) {
        return ApiResponse.success(authService.permissionsOf(userId));
    }

    /** PLAT-22：网关比对的权威用户令牌版本（每次请求实时查询，不做缓存）。 */
    @GetMapping("/principals/{userId}/token-version")
    public ApiResponse<Map<String, Long>> tokenVersion(
            @RequestHeader(TenantHeaders.TENANT_ID) long tenantId,
            @PathVariable long userId) {
        return ApiResponse.success(Map.of("token_version", authService.tokenVersionOf(tenantId, userId)));
    }

    /** PLAT-22：管理员重置口令后递增令牌版本，令该用户旧 access token 全部失效。 */
    @PostMapping("/users/{userId}/token-version:bump")
    public ApiResponse<Map<String, Long>> bumpTokenVersion(
            @RequestHeader(TenantHeaders.TENANT_ID) long tenantId,
            @PathVariable long userId) {
        return ApiResponse.success(Map.of("token_version", authService.bumpTokenVersion(tenantId, userId)));
    }

    /** PLAT-22：管理员生成一次性重置链接（无 SMTP 时的兜底，由管理员线下转交）。 */
    @PostMapping("/users/{userId}/password-reset-link")
    public ApiResponse<PasswordResetService.AdminResetLink> createPasswordResetLink(
            @RequestHeader(TenantHeaders.TENANT_ID) long tenantId,
            @RequestHeader(value = "X-Operator-Id", required = false) Long operatorId,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @PathVariable long userId) {
        String ip = forwardedFor == null ? null : forwardedFor.split(",")[0].trim();
        return ApiResponse.success(passwordResetService.createAdminLink(tenantId, userId,
                operatorId == null ? 0L : operatorId, ip));
    }
}
