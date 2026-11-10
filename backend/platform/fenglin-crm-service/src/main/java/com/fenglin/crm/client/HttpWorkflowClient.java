package com.fenglin.crm.client;

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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过 HTTP 调用工作流中心 HUB-API-501 {@code POST /api/hub/v1/workflow/instances}。
 * 服务间调用沿用网关注入的可信头，并补充 workflow 启动所需权限。
 */
@Component
public class HttpWorkflowClient implements WorkflowClient {

    private static final String START_PERMISSION = WorkflowPermissions.INSTANCE_START;
    private static final String DEPLOY_PERMISSION = WorkflowPermissions.DEFINITION_DEPLOY;
    private static final String BPMN_RESOURCE = "workflow/crm_quotation_approval.bpmn20.xml";

    private final RestClient restClient;
    private final String serviceUrl;
    private final String processKey;
    private final boolean bootstrapEnabled;

    public HttpWorkflowClient(@Value("${fenglin.crm.workflow-service-url}") String serviceUrl,
                              @Value("${fenglin.crm.quotation-process-key}") String processKey,
                              @Value("${fenglin.crm.workflow-bootstrap-enabled:true}") boolean bootstrapEnabled) {
        this.serviceUrl = serviceUrl.endsWith("/") ? serviceUrl.substring(0, serviceUrl.length() - 1) : serviceUrl;
        this.processKey = processKey;
        this.bootstrapEnabled = bootstrapEnabled;
        this.restClient = RestClient.create();
    }

    @Override
    public String startQuotationApproval(String permissions, String requestId,
                                         String quotationNo, long quotationId, long customerId,
                                         BigDecimal totalAmount, BigDecimal discountRate) {
        try {
            return doStart(permissions, requestId, quotationNo, quotationId, customerId, totalAmount, discountRate);
        } catch (RuntimeException ex) {
            // 首次部署兜底：流程未发布时自动部署内置 BPMN，再重试一次，避免新环境缺前置流程直接失败。
            if (!bootstrapEnabled || !deployQuotationProcess()) {
                throw ex;
            }
            return doStart(permissions, requestId, quotationNo, quotationId, customerId, totalAmount, discountRate);
        }
    }

    @SuppressWarnings("unchecked")
    private String doStart(String permissions, String requestId, String quotationNo, long quotationId,
                           long customerId, BigDecimal totalAmount, BigDecimal discountRate) {
        TenantContext.Snapshot snapshot = TenantContext.get();
        if (snapshot == null || snapshot.tenantId() == null || snapshot.userId() == null) {
            throw new BizException(CommonErrorCode.TENANT_REQUIRED);
        }
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("quotation_no", quotationNo);
        formData.put("quotation_id", quotationId);
        formData.put("customer_id", customerId);
        formData.put("total_amount", totalAmount);
        formData.put("discount_rate", discountRate);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("process_key", processKey);
        body.put("business_key", "CRM_QUOTATION:" + quotationId);
        body.put("business_type", "CRM_QUOTATION");
        body.put("title", "报价审批 " + quotationNo);
        body.put("form_data", formData);

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

    /**
     * 幂等部署内置报价审批流程（工作流中心按 tenant + process_key + version 版本化，重复部署无副作用）。
     */
    private boolean deployQuotationProcess() {
        TenantContext.Snapshot snapshot = TenantContext.get();
        if (snapshot == null || snapshot.tenantId() == null || snapshot.userId() == null) {
            return false;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("process_key", processKey);
            body.put("name", "报价审批");
            body.put("category", "CRM");
            body.put("bpmn_xml", readBpmn());
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

    private String readBpmn() throws IOException {
        try (InputStream input = new ClassPathResource(BPMN_RESOURCE).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
