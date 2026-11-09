package com.fenglin.intelligence.client;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 工作流客户端；首次启动若流程未部署则幂等部署内置 BPMN 后重试。
 */
@Component
public class HttpWorkflowClient implements WorkflowClient {

    private static final String START_PERMISSION = "biz:workflow:instance:start";
    private static final String DEPLOY_PERMISSION = "platform:workflow:definition:deploy";

    private static final Map<String, String> BPMN_RESOURCES = Map.of(
            "aiKnowledgeReview", "workflow/ai_knowledge_review.bpmn20.xml",
            "aiRagQa", "workflow/ai_rag_qa.bpmn20.xml",
            "aiAgentRun", "workflow/ai_agent_run.bpmn20.xml",
            "aiInsightGenerate", "workflow/ai_insight_generate.bpmn20.xml");

    private static final Map<String, String> BPMN_NAMES = Map.of(
            "aiKnowledgeReview", "知识入库审核",
            "aiRagQa", "RAG 问答",
            "aiAgentRun", "智能体运行",
            "aiInsightGenerate", "洞察生成");

    private final RestClient restClient;
    private final String serviceUrl;
    private final boolean bootstrapEnabled;

    public HttpWorkflowClient(@Value("${fenglin.intelligence.workflow-service-url}") String serviceUrl,
                              @Value("${fenglin.intelligence.workflow-bootstrap-enabled:true}") boolean bootstrapEnabled) {
        this.serviceUrl = serviceUrl.endsWith("/") ? serviceUrl.substring(0, serviceUrl.length() - 1) : serviceUrl;
        this.bootstrapEnabled = bootstrapEnabled;
        this.restClient = RestClient.create();
    }

    @Override
    public String start(String permissions, String requestId, String processKey, String businessKey,
                        String businessType, String title, Map<String, Object> formData) {
        try {
            return doStart(permissions, requestId, processKey, businessKey, businessType, title, formData);
        } catch (RuntimeException ex) {
            if (!bootstrapEnabled || !deploy(processKey)) {
                throw ex;
            }
            return doStart(permissions, requestId, processKey, businessKey, businessType, title, formData);
        }
    }

    @SuppressWarnings("unchecked")
    private String doStart(String permissions, String requestId, String processKey, String businessKey,
                           String businessType, String title, Map<String, Object> formData) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        if (snapshot == null || snapshot.tenantId() == null || snapshot.userId() == null) {
            throw new BizException(CommonErrorCode.TENANT_REQUIRED);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("process_key", processKey);
        body.put("business_key", businessKey);
        body.put("business_type", businessType);
        body.put("title", title);
        body.put("form_data", formData == null ? Map.of() : formData);
        String merged = permissions == null || permissions.isBlank()
                ? START_PERMISSION : permissions + "," + START_PERMISSION;
        try {
            Map<String, Object> response = restClient.post()
                    .uri(serviceUrl + "/api/hub/v1/workflow/instances")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-Id", String.valueOf(snapshot.tenantId()))
                    .header("X-User-Id", String.valueOf(snapshot.userId()))
                    .header("X-Client-Type", snapshot.clientType() == null ? "ADMIN" : snapshot.clientType())
                    .header("X-Permissions", merged)
                    .header("X-Request-Id", requestId == null ? "" : requestId)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object data = response == null ? null : response.get("data");
            if (data instanceof Map<?, ?> map) {
                Object no = map.get("instance_no");
                if (no != null) {
                    return String.valueOf(no);
                }
                Object id = map.get("instance_id");
                if (id != null) {
                    return String.valueOf(id);
                }
            }
            throw new IllegalStateException("工作流响应缺少实例号");
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("提交工作流失败：" + ex.getMessage(), ex);
        }
    }

    private boolean deploy(String processKey) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        String resource = BPMN_RESOURCES.get(processKey);
        if (snapshot == null || snapshot.tenantId() == null || snapshot.userId() == null || resource == null) {
            return false;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("process_key", processKey);
            body.put("name", BPMN_NAMES.getOrDefault(processKey, "云智流程"));
            body.put("category", "INTELLIGENCE");
            body.put("bpmn_xml", read(resource));
            restClient.post()
                    .uri(serviceUrl + "/api/hub/v1/admin/workflow/definitions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-Id", String.valueOf(snapshot.tenantId()))
                    .header("X-User-Id", String.valueOf(snapshot.userId()))
                    .header("X-Client-Type", snapshot.clientType() == null ? "ADMIN" : snapshot.clientType())
                    .header("X-Permissions", DEPLOY_PERMISSION)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private String read(String resource) throws IOException {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
