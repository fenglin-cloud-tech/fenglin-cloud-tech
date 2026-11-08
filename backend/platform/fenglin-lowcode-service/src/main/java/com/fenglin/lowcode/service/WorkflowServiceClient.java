package com.fenglin.lowcode.service;

import com.fenglin.common.core.BizException;
import com.fenglin.lowcode.LowcodeErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/**
 * 复用工作流中心：发布审批通过 HUB-API-701 POST /api/hub/v1/workflow/instances 发起实例。
 */
@Component
public class WorkflowServiceClient {

    private final RestClient restClient;

    public WorkflowServiceClient(
            @Value("${fenglin.lowcode.workflow-service-url:http://localhost:18084}") String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient = RestClient.builder().baseUrl(normalized).build();
    }

    /**
     * @return 工作流实例号；用于 1202 响应的 processInstanceId。
     */
    public String startPublishProcess(long tenantId, Long userId, String processKey,
                                      String businessKey, String title,
                                      Map<String, Object> formData) {
        Map<String, Object> body = new HashMap<>();
        body.put("process_key", processKey);
        body.put("business_key", businessKey);
        body.put("business_type", "LOWCODE_APP");
        body.put("title", title);
        body.put("form_data", formData);
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/hub/v1/workflow/instances")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .header("X-Client-Type", "ADMIN")
                    .header("X-Permissions", "biz:workflow:instance:start")
                    .header("X-User-Id", userId == null ? "" : String.valueOf(userId))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null || response.get("data") == null) {
                throw new BizException(LowcodeErrorCode.DEPENDENCY_UNAVAILABLE, "工作流服务返回为空");
            }
            Map<?, ?> data = (Map<?, ?>) response.get("data");
            Object instanceNo = data.get("instance_no");
            Object instanceId = data.get("instance_id");
            Object value = instanceNo != null ? instanceNo : instanceId;
            if (value == null) {
                throw new BizException(LowcodeErrorCode.DEPENDENCY_UNAVAILABLE, "工作流服务未返回实例号");
            }
            return String.valueOf(value);
        } catch (BizException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BizException(LowcodeErrorCode.DEPENDENCY_UNAVAILABLE,
                    "工作流服务不可用: " + ex.getMessage());
        }
    }
}
