package com.fenglin.crm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.crm.service.PaymentService;
import com.fenglin.crm.web.dto.CrmDtos.CollectionFlagRequest;
import com.fenglin.crm.web.dto.CrmDtos.OverduePlanView;
import com.fenglin.crm.web.dto.CrmDtos.PaymentRecordView;
import com.fenglin.crm.web.dto.CrmDtos.PaymentRegisterRequest;
import com.fenglin.crm.web.dto.CrmDtos.PaymentReminderView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 回款登记 / 核销 / 逾期 / 催收接口（扩展：01_丰麟云销 文档未定义，按最小可用语义实现）。
 */
@RestController
public class CrmPaymentController {

    private final PaymentService paymentService;

    public CrmPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 扩展：登记回款并核销到指定回款计划。 */
    @PostMapping("/api/crm/v1/contracts/{contractId}/payments")
    public ApiResponse<PaymentRecordView> register(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("contractId") long contractId,
            @RequestBody PaymentRegisterRequest request) {
        PermissionGuard.require(permissions, "crm:contract:manage");
        return ApiResponse.success(paymentService.register(contractId, request));
    }

    /** 扩展：合同回款记录。 */
    @GetMapping("/api/crm/v1/contracts/{contractId}/payments")
    public ApiResponse<List<PaymentRecordView>> records(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("contractId") long contractId) {
        PermissionGuard.require(permissions, "crm:contract:view");
        return ApiResponse.success(paymentService.listRecords(contractId));
    }

    /** 扩展：逾期回款计划列表（未结清且已过到期日）。 */
    @GetMapping("/api/crm/v1/payment-plans/overdue")
    public ApiResponse<PageResult<OverduePlanView>> overdue(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, "crm:contract:view");
        return ApiResponse.success(paymentService.overdue(customerId, page, size));
    }

    /** 扩展：回款提醒（未结清且到期日不晚于未来 N 天，可按负责人过滤）。 */
    @GetMapping("/api/crm/v1/payment-plans/reminders")
    public ApiResponse<PageResult<PaymentReminderView>> reminders(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "upcomingDays", required = false) Integer upcomingDays,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, "crm:contract:view");
        return ApiResponse.success(paymentService.reminders(ownerId, upcomingDays, page, size));
    }

    /** 扩展：设置催收标记。 */
    @PatchMapping("/api/crm/v1/payment-plans/{id}/collection")
    public ApiResponse<Void> markCollection(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody CollectionFlagRequest request) {
        PermissionGuard.require(permissions, "crm:contract:manage");
        paymentService.markCollection(id, request);
        return ApiResponse.success(null);
    }
}
