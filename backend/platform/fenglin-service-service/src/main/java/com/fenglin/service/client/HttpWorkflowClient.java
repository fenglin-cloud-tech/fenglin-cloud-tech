package com.fenglin.service.client;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.security.WorkflowPermissions;
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
 * 通过 HTTP 调用工作流中心 {@code POST /api/hub/v1/workflow/instances}。
 * 首次提交若流程未部署，自动幂等部署内置 BPMN 后重试一次（与云财/云研一致）。
 */
@Component
public class HttpWorkflowClient implements WorkflowClient {

    private static final String START_PERMISSION = WorkflowPermissions.INSTANCE_START;
    private static final String DEPLOY_PERMISSION = WorkflowPermissions.DEFINITION_DEPLOY;

    private static final Map<String, String> BPMN_RESOURCES = Map.of(
            "svcCatalogItemApproval", "workflow/svc_catalog_item_approval.bpmn20.xml",
            "svcChangeApproval", "workflow/svc_change_approval.bpmn20.xml",
            "svcLowcodeAppReleaseApproval", "workflow/svc_lowcode_release_approval.bpmn20.xml",
            "svcRpaOrchestration", "workflow/svc_rpa_orchestration.bpmn20.xml");

    private static final Map<String, String> BPMN_NAMES = Map.of(
            "svcCatalogItemApproval", "目录项申请审批",
            "svcChangeApproval", "变更审批",
            "svcLowcodeAppReleaseApproval", "低代码应用发布审批",
            "svcRpaOrchestration", "RPA 编排发布审批");

    private final RestClient restClient;
    private final String serviceUrl;
    private final boolean bootstrapEnabled;

    public HttpWorkflowClient(@Value("${fenglin.service.workflow-service-url}") String serviceUrl,
                              @Value("${fenglin.service.workflow-bootstrap-enabled:true}") boolean bootstrapEnabled) {
        this.serviceUrl = serviceUrl.endsWith("/") ? serviceUrl.substring(0, serviceUrl.length() - 1) : serviceUrl;
        this.bootstrapEnabled = bootstrapEnabled;
        this.restClient = RestClient.create();
    }

    @Override
    public String startApproval(String permissions, String requestId, String processKey, String businessKey,
                                String businessType, String title, Map<String, Object> formData) {
        try {
            return doStart(permissions, requestId, processKey, businessKey, businessType, title, formData);
        } catch (RuntimeException ex) {
            if (!bootstrapEnabled || !deployProcess(processKey)) {
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
        String mergedPermissions = permissions == null || permissions.isBlank()
                ? START_PERMISSION : permissions + "," + START_PERMISSION;
        try {
            Map<String, Object> response = restClient.post()
                    .uri(serviceUrl + "/api/hub/v1/workflow/instances")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Tenant-Id", String.valueOf(snapshot.tenantId()))
                    .header("X-User-Id", String.valueOf(snapshot.userId()))
                    .header("X-Client-Type", snapshot.clientType() == null ? "ADMIN" : snapshot.clientType())
                    .header("X-Permissions", mergedPermissions)
                    .header("X-Request-Id", requestId == null ? "" : requestId)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object data = response == null ? null : response.get("data");
            if (data instanceof Map<?, ?> map) {
                Object instanceNo = map.get("instance_no");
                if (instanceNo != null) {
                    return String.valueOf(instanceNo);
                }
                Object instanceId = map.get("instance_id");
                if (instanceId != null) {
                    return String.valueOf(instanceId);
                }
            }
            throw new IllegalStateException("工作流响应缺少实例号");
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("提交工作流审批失败：" + ex.getMessage(), ex);
        }
    }

    private boolean deployProcess(String processKey) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        String resource = BPMN_RESOURCES.get(processKey);
        if (snapshot == null || snapshot.tenantId() == null || snapshot.userId() == null || resource == null) {
            return false;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("process_key", processKey);
            body.put("name", BPMN_NAMES.getOrDefault(processKey, "云服审批"));
            body.put("category", "SERVICE");
            body.put("bpmn_xml", readBpmn(resource));
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

    private String readBpmn(String resource) throws IOException {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
