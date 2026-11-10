package com.fenglin.iam.client;

import com.fenglin.common.tenant.TenantHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 校验员工是否属于当前租户：IAM 与云才分库，不能直接查 talent_employee，走云才内部接口。
 */
@Component
public class TalentEmployeeClient {

    private final RestClient restClient;

    public TalentEmployeeClient(RestClient.Builder builder,
                                @Value("${fenglin.iam.talent-service-url:http://localhost:18097}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public boolean exists(long tenantId, long employeeId) {
        try {
            Map<?, ?> body = restClient.get()
                    .uri("/internal/v1/talent/employees/{id}/exists", employeeId)
                    .header(TenantHeaders.TENANT_ID, String.valueOf(tenantId))
                    .retrieve()
                    .body(Map.class);
            Object data = body == null ? null : body.get("data");
            return data instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("exists"));
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        }
    }
}
