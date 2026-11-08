package com.fenglin.gateway.security;

import java.util.List;

/**
 * 网关解析出的调用主体，供后续限流/灰度/审计埋点使用。
 */
public record GatewayPrincipal(long tenantId, long userId, String tenantCode, String clientType,
                               List<String> roles, List<String> permissions) {
}
