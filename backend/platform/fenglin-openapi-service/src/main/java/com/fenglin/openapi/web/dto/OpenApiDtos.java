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

    public record PortalAppView(long appId, String code, String name, String status, String reviewStatus,
                                long callToday, Long quotaDaily) {
    }

    /** 扩展：管理端应用列表（1006 仅定义开发者门户视角）。 */
    public record AdminAppView(long appId, String code, String name, String ownerType,
                               String reviewStatus, String status, Instant createdAt) {
    }

    // ---------- 开发者门户（1001/1003/1004/1005/1006 的门户侧收敛接口） ----------

    public record PortalAppCreateRequest(String name, String ownerType, List<String> scopes,
                                         List<String> redirectUris) {
    }

    public record PortalCallbackRequest(List<String> redirectUris) {
    }

    public record PortalScopeRequest(List<String> scopes) {
    }

    public record PortalAppDetail(long appId, String code, String name, String ownerType,
                                  String reviewStatus, String status, List<String> scopes,
                                  List<String> redirectUris, Instant createdAt,
                                  long callToday, Long quotaDaily) {
    }

    /** 密钥列表视图：AppSecret 只给掩码，明文仅在签发响应中出现一次。 */
    public record PortalKeyView(long id, String appKey, String secretMasked, String secretFingerprint,
                                String status, Instant expireAt, Instant createdAt) {
    }

    public record PortalQuotaView(long appId, String apiPath, Integer qpsLimit, Long dailyLimit,
                                  long usedToday, String usedDate, Integer currentQps, String limitStatus) {
    }

    // ---------- OPEN-03 配额自助申请 ----------

    public record QuotaApplyRequest(Long appId, String apiPath, Integer qpsLimit, Long dailyLimit, String reason) {
    }

    public record QuotaRequestView(long id, long appId, String apiPath, Integer requestedQps, Long requestedDaily,
                                   String reason, String status, long appliedBy, Long decidedBy, Instant decidedAt,
                                   String decisionComment, Instant createdAt) {
    }

    public record QuotaRequestReview(String action, String comment) {
    }

    public record PortalScopeView(String code, String name, String domain, boolean subscribed) {
    }

    // ---------- 接口文档与在线联调沙箱（OPEN-01） ----------

    /** 平台已发布的开放接口文档；subscribed 表示当前应用是否已订阅 required_scope。 */
    public record PortalApiDocView(String code, String method, String path, String name, String domain,
                                   String summary, String description, String requiredScope,
                                   String requestSchema, String responseSchema, String sampleRequest,
                                   String sampleResponse, boolean sandboxEnabled, boolean subscribed) {
    }

    public record SandboxCallRequest(String apiCode, String body) {
    }

    /** 沙箱结果：仅回显本次请求的签名要素与响应，不回显 AppSecret。 */
    public record SandboxCallResponse(String apiCode, String method, String path, String appKey,
                                      String signature, String nonce, long timestamp, String requestId,
                                      int httpStatus, long latencyMs, String responseBody,
                                      boolean bodyTruncated) {
    }
}
