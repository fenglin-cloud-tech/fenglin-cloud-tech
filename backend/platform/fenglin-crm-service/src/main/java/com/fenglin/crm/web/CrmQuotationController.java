package com.fenglin.crm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.crm.service.QuotationService;
import com.fenglin.crm.web.dto.CrmDtos.QuotationCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.QuotationSubmitRequest;
import com.fenglin.crm.web.dto.CrmDtos.QuotationSubmitResult;
import com.fenglin.crm.web.dto.CrmDtos.QuotationView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报价接口（01_丰麟云销_05 CRM-API-030 ~ 032）。
 */
@RestController
public class CrmQuotationController {

    private final QuotationService quotationService;

    public CrmQuotationController(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    @PostMapping("/api/crm/v1/quotations")
    public ApiResponse<QuotationView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody QuotationCreateRequest request) {
        PermissionGuard.require(permissions, "crm:quotation:create");
        return ApiResponse.success(quotationService.create(request));
    }

    @GetMapping("/api/crm/v1/quotations/{id}")
    public ApiResponse<QuotationView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:quotation:view");
        return ApiResponse.success(quotationService.detail(id));
    }

    @PostMapping("/api/crm/v1/quotations/{id}/submit")
    public ApiResponse<QuotationSubmitResult> submit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable("id") long id,
            @RequestBody(required = false) QuotationSubmitRequest request) {
        PermissionGuard.require(permissions, "crm:quotation:submit");
        return ApiResponse.success(quotationService.submit(id, permissions, requestId));
    }
}
