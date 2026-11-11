package com.fenglin.auth.web;

import com.fenglin.auth.config.AuthErrorCode;
import com.fenglin.auth.service.AuthService;
import com.fenglin.auth.web.dto.ChangePasswordRequest;
import com.fenglin.auth.web.dto.ChangePasswordResult;
import com.fenglin.auth.web.dto.LoginRequest;
import com.fenglin.auth.web.dto.LogoutPayload;
import com.fenglin.auth.web.dto.LogoutRequest;
import com.fenglin.auth.web.dto.RefreshRequest;
import com.fenglin.auth.web.dto.RevokeRequest;
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

    public SsoController(AuthService authService) {
        this.authService = authService;
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

    private String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BizException(AuthErrorCode.ACCESS_DENIED, "缺少 Bearer 令牌");
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }
}
