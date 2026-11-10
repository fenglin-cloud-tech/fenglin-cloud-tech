package com.fenglin.gateway.security;

/**
 * 权限解析不可用：网关按 fail-close 返回 503，禁止以空权限放行下游。
 */
public class PermissionResolutionException extends RuntimeException {

    public PermissionResolutionException(String message) {
        super(message);
    }

    public PermissionResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
