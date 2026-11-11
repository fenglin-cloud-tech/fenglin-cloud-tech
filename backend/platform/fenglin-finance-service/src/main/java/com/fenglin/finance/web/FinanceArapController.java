package com.fenglin.finance.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.finance.service.ArapService;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.web.dto.FinanceDtos.AgingReport;
import com.fenglin.finance.web.dto.FinanceDtos.PayableView;
import com.fenglin.finance.web.dto.FinanceDtos.PaymentCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.PaymentView;
import com.fenglin.finance.web.dto.FinanceDtos.ReceiptCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.ReceiptView;
import com.fenglin.finance.web.dto.FinanceDtos.ReceivableView;
import com.fenglin.finance.web.dto.FinanceDtos.SettlementDocResult;
import com.fenglin.finance.web.dto.FinanceDtos.WriteOffRequest;
import com.fenglin.finance.web.dto.FinanceDtos.WriteOffResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应收应付、收付款、核销与账龄分析接口。
 */
@RestController
@RequestMapping("/api/finance/v1")
public class FinanceArapController {

    private static final String AR_VIEW = "biz:finance:ar:view";
    private static final String AP_VIEW = "biz:finance:ap:view";
    private static final String RECEIPT = "biz:finance:receipt:create";
    private static final String PAYMENT = "biz:finance:payment:create";
    private static final String WRITE_OFF = "biz:finance:arap:writeoff";
    private static final String AGING = "biz:finance:aging:view";

    private final ArapService arapService;

    public FinanceArapController(ArapService arapService) {
        this.arapService = arapService;
    }

    @GetMapping("/receivables")
    public ApiResponse<List<ReceivableView>> receivables(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, AR_VIEW);
        return ApiResponse.success(arapService.listReceivables(status, FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }

    @GetMapping("/payables")
    public ApiResponse<List<PayableView>> payables(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, AP_VIEW);
        return ApiResponse.success(arapService.listPayables(status, FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }

    @GetMapping("/receipts")
    public ApiResponse<List<ReceiptView>> receipts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "limit", required = false) Integer limit) {
        PermissionGuard.require(permissions, AR_VIEW);
        return ApiResponse.success(arapService.receipts(status, from, to, limit));
    }

    @GetMapping("/payments")
    public ApiResponse<List<PaymentView>> payments(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "limit", required = false) Integer limit) {
        PermissionGuard.require(permissions, AP_VIEW);
        return ApiResponse.success(arapService.payments(status, from, to, limit));
    }

    @PostMapping("/receipts")
    public ApiResponse<SettlementDocResult> receive(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ReceiptCreateRequest request) {
        PermissionGuard.require(permissions, RECEIPT);
        return ApiResponse.success(arapService.receive(request, idempotencyKey));
    }

    @PostMapping("/payments")
    public ApiResponse<SettlementDocResult> pay(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody PaymentCreateRequest request) {
        PermissionGuard.require(permissions, PAYMENT);
        return ApiResponse.success(arapService.pay(request, idempotencyKey));
    }

    @PostMapping("/arap/write-off")
    public ApiResponse<WriteOffResult> writeOff(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("bizType") String bizType, @RequestParam("sourceId") long sourceId,
            @RequestBody WriteOffRequest request) {
        PermissionGuard.require(permissions, WRITE_OFF);
        return ApiResponse.success(arapService.writeOff(bizType, sourceId, request.amount(),
                request.writeOffDate()));
    }

    @GetMapping("/arap/aging")
    public ApiResponse<AgingReport> aging(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "type", required = false, defaultValue = "AR") String type) {
        PermissionGuard.require(permissions, AGING);
        return ApiResponse.success(arapService.aging(type));
    }
}
