package com.fenglin.crm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.crm.service.QuotationService;
import com.fenglin.crm.web.dto.CrmDtos.ApprovalResultRequest;
import com.fenglin.crm.web.dto.CrmDtos.QuotationView;
import com.fenglin.crm.web.dto.CrmDtos.WorkflowApprovalCallbackRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 扩展：内部审批结果回调。工作流中心审批完成后回写报价状态（下一批可改为 MQ 订阅）。
 */
@RestController
public class CrmInternalController {

    private final QuotationService quotationService;
    private final String callbackToken;

    public CrmInternalController(QuotationService quotationService,
                                 @Value("${fenglin.crm.workflow-callback-token:fenglin-dev-callback}") String callbackToken) {
        this.quotationService = quotationService;
        this.callbackToken = callbackToken;
    }

    @PostMapping("/api/crm/v1/internal/quotations/{id}/approval")
    public ApiResponse<QuotationView> approval(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody ApprovalResultRequest request) {
        PermissionGuard.require(permissions, "crm:quotation:approve");
        return ApiResponse.success(quotationService.handleApproval(id, request.result(), request.comment()));
    }

    /**
     * CRM-09 方案 A：工作流实例结束回调（hub_wf_callback 直接投递到本服务，不经网关）。
     * payload 自带 tenant_id / operator_id，用于建立租户与操作人上下文；重复投递在状态机内幂等。
     */
    @PostMapping("/api/crm/v1/internal/quotations/approval/callback")
    public ApiResponse<QuotationView> workflowCallback(
            @RequestHeader(value = "X-Callback-Token", required = false) String token,
            @RequestBody WorkflowApprovalCallbackRequest request) {
        if (token == null || !token.equals(callbackToken)) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "回调令牌无效");
        }
        if (request == null || request.tenantId() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "tenant_id 不能为空");
        }
        TenantContext.set(request.tenantId(), request.operatorId() == null ? 0L : request.operatorId(),
                "SYSTEM", null, false);
        return ApiResponse.success(quotationService.handleWorkflowCallback(request));
    }
}
