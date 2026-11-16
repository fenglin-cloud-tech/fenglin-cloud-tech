package com.fenglin.iam.client;

import com.fenglin.common.tenant.TenantHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 重置口令后吊销目标用户的在线会话与刷新令牌：复用 auth 既有登出链路，不在 IAM 直连会话表。
 */
@Component
public class AuthSessionClient {

    private final RestClient restClient;

    public AuthSessionClient(RestClient.Builder builder,
                             @Value("${fenglin.iam.auth-service-url:http://localhost:18081}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** 吊销该用户全部会话；失败直接抛出，由重置口令事务回滚，避免"口令已改但会话未失效"。 */
    public void revokeAllSessions(long tenantId, long userId) {
        restClient.post()
                .uri("/internal/v1/auth/users/{userId}/sessions:revoke", userId)
                .header(TenantHeaders.TENANT_ID, String.valueOf(tenantId))
                .retrieve()
                .toBodilessEntity();
    }

    /** PLAT-22：递增用户令牌版本，令旧 access token 立即失效。 */
    public void bumpTokenVersion(long tenantId, long userId) {
        restClient.post()
                .uri("/internal/v1/auth/users/{userId}/token-version:bump", userId)
                .header(TenantHeaders.TENANT_ID, String.valueOf(tenantId))
                .retrieve()
                .toBodilessEntity();
    }

    /** PLAT-22：生成一次性口令重置链接（管理员线下转交）。 */
    @SuppressWarnings("unchecked")
    public PasswordResetLink createPasswordResetLink(long tenantId, long userId, long operatorId) {
        Map<String, Object> body = restClient.post()
                .uri("/internal/v1/auth/users/{userId}/password-reset-link", userId)
                .header(TenantHeaders.TENANT_ID, String.valueOf(tenantId))
                .header("X-Operator-Id", String.valueOf(operatorId))
                .retrieve()
                .body(Map.class);
        Object data = body == null ? null : body.get("data");
        if (!(data instanceof Map<?, ?> map)) {
            throw new IllegalStateException("重置链接响应为空");
        }
        return new PasswordResetLink((String) map.get("token"), (String) map.get("reset_url"),
                String.valueOf(map.get("expires_at")));
    }

    public record PasswordResetLink(String token, String resetUrl, String expiresAt) {
    }
}
