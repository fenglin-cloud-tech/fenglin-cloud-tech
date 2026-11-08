package com.fenglin.auth.config;

import com.fenglin.common.core.ErrorCode;

/**
 * 认证域错误码，按《接口文档》4.1 与错误码表实现。
 */
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS("0001001", "用户名或口令错误", 401),
    ACCOUNT_LOCKED("0001002", "账号已锁定，请稍后再试", 423),
    MFA_REQUIRED("0001003", "需要 MFA 二次验证", 401),
    SSO_CONFIG_ERROR("0001004", "SSO 配置错误", 400),
    REFRESH_TOKEN_INVALID("0000001", "refresh token 已失效", 401),
    ACCESS_DENIED("0000002", "无权访问该资源", 403),
    INVALID_PARAMETER("0000003", "请求参数不合法", 400);

    private final String code;
    private final String message;
    private final int httpStatus;

    AuthErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
