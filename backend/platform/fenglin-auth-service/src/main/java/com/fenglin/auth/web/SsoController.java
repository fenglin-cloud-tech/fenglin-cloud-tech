package com.fenglin.auth.web;

import com.fenglin.auth.config.AuthErrorCode;
import com.fenglin.auth.service.AuthService;
import com.fenglin.auth.service.PasswordResetService;
import com.fenglin.auth.service.PlatformAccessService;
import com.fenglin.auth.web.dto.ChangePasswordRequest;
import com.fenglin.auth.web.dto.ChangePasswordResult;
import com.fenglin.auth.web.dto.ForgotPasswordRequest;
import com.fenglin.auth.web.dto.LoginRequest;
import com.fenglin.auth.web.dto.LogoutPayload;
import com.fenglin.auth.web.dto.LogoutRequest;
import com.fenglin.auth.web.dto.RefreshRequest;
import com.fenglin.auth.web.dto.ResetPasswordRequest;
import com.fenglin.auth.web.dto.RevokeRequest;
import com.fenglin.auth.web.dto.TenantSwitchRequest;
import com.fenglin.auth.web.dto.TokenPayload;
import com.fenglin.auth.web.dto.TokenRequest;
import com.fenglin.auth.web.dto.UserInfoPayload;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/hub/v1/sso")
public class SsoController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final PlatformAccessService platformAccessService;

    public SsoController(AuthService authService, PasswordResetService passwordResetService,
                         PlatformAccessService platformAccessService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.platformAccessService = platformAccessService;
    }

    /** 首方口令登录（契约缺口，见 LoginRequest 注释） */
    @PostMapping("/login")
    public ApiResponse<TokenPayload> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /** HUB-API-104 令牌端点，P1 支持 grant_type=refresh_token */
    @PostMapping("/token")
    public ApiResponse<TokenPayload> token(@RequestBody TokenRequest request) {
        String grantType = request.grantType() == null ? "refresh_token" : request.grantType();
        if (!"refresh_token".equals(grantType)) {
            throw new BizException(AuthErrorCode.INVALID_PARAMETER,
                    "P1 仅支持 refresh_token，authorization_code 待 SSO 里程碑");
        }
        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new BizException(AuthErrorCode.INVALID_PARAMETER, "refresh_token 不能为空");
        }
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    /** HUB-API-105 刷新令牌 */
    @PostMapping("/token:refresh")
    public ApiResponse<TokenPayload> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    /** HUB-API-106 撤销令牌 */
    @PostMapping("/token:revoke")
    public ApiResponse<Map<String, Boolean>> revoke(@Valid @RequestBody RevokeRequest request) {
        return ApiResponse.success(Map.of("revoked", authService.revoke(request.token(), request.tokenTypeHint())));
    }

    /** HUB-API-107 登出 */
    @PostMapping("/logout")
    public ApiResponse<LogoutPayload> logout(@Valid @RequestBody LogoutRequest request) {
        return ApiResponse.success(authService.logout(request.idTokenHint(), request.allSessions()));
    }

    /** OPEN-06 一期：平台身份显式切换到目标租户，签发 30 分钟只读令牌（不换绑 refresh）。 */
    @PostMapping("/tenant:switch")
    public ApiResponse<TokenPayload> switchTenant(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody TenantSwitchRequest request) {
        if (request.targetTenantId() == null) {
            throw new BizException(AuthErrorCode.INVALID_PARAMETER, "target_tenant_id 不能为空");
        }
        return ApiResponse.success(platformAccessService.switchTenant(
                extractBearer(authorization), request.targetTenantId(), request.reason()));
    }

    /** HUB-API-108 用户信息 */
    @GetMapping("/userinfo")
    public ApiResponse<UserInfoPayload> userinfo(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ApiResponse.success(authService.userinfo(extractBearer(authorization)));
    }

    /** 自助修改口令：以当前 access token 识别登录人，校验旧口令后更新 */
    @PostMapping("/password/change")
    public ApiResponse<ChangePasswordResult> changePassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.success(authService.changePassword(extractBearer(authorization), request));
    }

    /**
     * PLAT-22 忘记密码（匿名）：SMTP 未配置时返回 UNAVAILABLE，提示联系管理员；
     * 已配置时恒返回 EMAIL 语义，防止账号枚举。
     */
    @PostMapping("/password/forgot")
    public ApiResponse<PasswordResetService.ForgotResult> forgot(
            @RequestBody ForgotPasswordRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor) {
        if (request == null || request.tenantCode() == null || request.tenantCode().isBlank()
                || request.username() == null || request.username().isBlank()) {
            throw new BizException(AuthErrorCode.INVALID_PARAMETER, "租户编码与登录名不能为空");
        }
        String ip = forwardedFor == null ? null : forwardedFor.split(",")[0].trim();
        return ApiResponse.success(passwordResetService.forgot(request.tenantCode().trim(),
                request.username().trim(), ip));
    }

    /** PLAT-22 一次性令牌重置口令（匿名）：单次使用、30 分钟有效，成功后旧令牌全部失效。 */
    @PostMapping("/password/reset")
    public ApiResponse<Map<String, Boolean>> reset(@RequestBody ResetPasswordRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()
                || request.newPassword() == null || request.newPassword().isBlank()) {
            throw new BizException(AuthErrorCode.INVALID_PARAMETER, "令牌与新口令不能为空");
        }
        passwordResetService.reset(request.token().trim(), request.newPassword());
        return ApiResponse.success(Map.of("reset", true));
    }

    private String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BizException(AuthErrorCode.ACCESS_DENIED, "缺少 Bearer 令牌");
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }
}
