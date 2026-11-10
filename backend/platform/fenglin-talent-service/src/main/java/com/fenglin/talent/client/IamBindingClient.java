package com.fenglin.talent.client;

import com.fenglin.common.tenant.TenantHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * PLAT-08：按账号解析绑定的员工 ID。IAM 与云才分库，走 IAM 内部接口。
 *
 * <p>未绑定返回 null；IAM 明确 404 也按未绑定处理。网络/5xx 直接抛出，不静默降级。</p>
 */
@Component
public class IamBindingClient {

    private final RestClient restClient;

    public IamBindingClient(RestClient.Builder builder,
                            @Value("${fenglin.talent.iam-service-url:http://localhost:18082}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Long findEmployeeId(long tenantId, long userId) {
        try {
            Map<?, ?> body = restClient.get()
                    .uri("/internal/v1/iam/users/{id}/employee-id", userId)
                    .header(TenantHeaders.TENANT_ID, String.valueOf(tenantId))
                    .retrieve()
                    .body(Map.class);
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof Map<?, ?> map)) {
                return null;
            }
            Object value = map.get("employee_id");
            if (value == null || String.valueOf(value).isBlank()) {
                return null;
            }
            return Long.parseLong(String.valueOf(value));
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }
}
