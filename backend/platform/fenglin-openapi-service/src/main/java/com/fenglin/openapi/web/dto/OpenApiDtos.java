package com.fenglin.openapi.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * 开放平台接口出入参（HUB-API-1001~1006）。
 * 字段使用 camelCase，序列化由全局 SNAKE_CASE 策略输出为 snake_case。
 */
public final class OpenApiDtos {

    private OpenApiDtos() {
    }

    public record AppCreateRequest(String name, String ownerType, List<String> scopes, List<String> redirectUris) {
    }

    public record AppCreateResponse(long appId, String code, String reviewStatus) {
    }

    public record ReviewRequest(String action, String reason) {
    }

    public record ReviewResponse(long appId, String reviewStatus, String status) {
    }

    public record KeyRequest(String action, Integer graceHours) {
    }

    public record KeyResponse(String appKey, String appSecret, Instant issuedAt, String secretFingerprint) {
    }

    public record QuotaItem(String apiPath, Integer qpsLimit, Long dailyLimit) {
    }

    public record QuotaRequest(Long appId, List<QuotaItem> items) {
    }

    public record QuotaView(long appId, String apiPath, Integer qpsLimit, Long dailyLimit, long usedToday) {
    }

    public record CallLogView(long id, long appId, String appKey, String apiPath, String method,
                              int httpStatus, String bizCode, Integer latencyMs, String requestId,
                              Instant calledAt) {
    }

    public record PortalAppView(long appId, String code, String status, long callToday, Long quotaDaily) {
    }
}
