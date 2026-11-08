package com.fenglin.crm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.crm.service.QuotationService;
import com.fenglin.crm.web.dto.CrmDtos.ApprovalResultRequest;
import com.fenglin.crm.web.dto.CrmDtos.QuotationView;
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

    public CrmInternalController(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    @PostMapping("/api/crm/v1/internal/quotations/{id}/approval")
    public ApiResponse<QuotationView> approval(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody ApprovalResultRequest request) {
        PermissionGuard.require(permissions, "crm:quotation:approve");
        return ApiResponse.success(quotationService.handleApproval(id, request.result(), request.comment()));
    }
}
