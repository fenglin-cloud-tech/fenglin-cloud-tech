package com.fenglin.iam.client;

import com.fenglin.common.tenant.TenantHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

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
}
