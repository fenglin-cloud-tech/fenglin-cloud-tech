package com.fenglin.auth.web;

import com.fenglin.auth.service.AuthService;
import com.fenglin.common.core.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public InternalAuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/revocations/{jti}")
    public ApiResponse<Map<String, Boolean>> revoked(@PathVariable String jti) {
        return ApiResponse.success(Map.of("revoked", authService.isRevokedJti(jti)));
    }

    @GetMapping("/sessions/{sessionId}/status")
    public ApiResponse<Map<String, Boolean>> sessionStatus(@PathVariable String sessionId) {
        return ApiResponse.success(Map.of("online", authService.isSessionOnline(sessionId)));
    }
}
