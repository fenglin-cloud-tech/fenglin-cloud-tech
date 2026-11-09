package com.fenglin.crm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.crm.mapper.ContractMapper;
import com.fenglin.crm.support.CrmAudit;
import com.fenglin.crm.support.CrmContext;
import com.fenglin.crm.support.Rows;
import com.fenglin.crm.web.dto.CrmDtos.CollectionFlagRequest;
import com.fenglin.crm.web.dto.CrmDtos.OverduePlanView;
import com.fenglin.crm.web.dto.CrmDtos.PaymentRecordView;
import com.fenglin.crm.web.dto.CrmDtos.PaymentRegisterRequest;
import com.fenglin.crm.web.dto.CrmDtos.PaymentReminderView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 回款登记 / 核销 / 逾期识别 / 催收标记。
 * 扩展：01_丰麟云销 文档未定义回款接口与回款记录表，按最小可用语义实现并标注。
 */
@Service
public class PaymentService {

    private static final Set<String> METHODS = Set.of("BANK", "TRANSFER", "ONLINE", "CASH", "OTHER");
    private static final Set<String> COLLECTION_FLAGS = Set.of("NONE", "REMINDED", "PROMISED", "ESCALATED");

    private final ContractMapper contractMapper;
    private final CrmAudit audit;

    public PaymentService(ContractMapper contractMapper, CrmAudit audit) {
        this.contractMapper = contractMapper;
        this.audit = audit;
    }

    /**
     * 登记回款并核销到指定回款计划：累计实收，计划自动进入 PARTIAL / PAID。
     */
    @Transactional
    public PaymentRecordView register(long contractId, PaymentRegisterRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        if (contractMapper.findById(tenantId, contractId) == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "合同不存在");
        }
        if (request.planId() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "回款计划不能为空");
        }
        Map<String, Object> plan = contractMapper.findPaymentPlan(tenantId, request.planId());
        if (plan == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "回款计划不存在");
        }
        if (Rows.lngVal(plan, "contractId") != contractId) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "回款计划不属于该合同");
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "回款金额必须大于 0");
        }
        String method = request.method() == null || request.method().isBlank() ? "BANK" : request.method();
        if (!METHODS.contains(method)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的回款方式: " + method);
        }
        BigDecimal planAmount = Rows.dec(plan, "amount");
        BigDecimal paid = Rows.dec(plan, "paidAmount") == null ? BigDecimal.ZERO : Rows.dec(plan, "paidAmount");
        BigDecimal remaining = planAmount.subtract(paid);
        if (amount.compareTo(remaining) > 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "回款金额超过计划未收余额: " + remaining.toPlainString());
        }
        BigDecimal newPaid = paid.add(amount);
        String status = newPaid.compareTo(planAmount) >= 0 ? "PAID" : "PARTIAL";
        long paymentId = IdWorker.getId();
        LocalDate receivedDate = request.receivedDate() == null ? LocalDate.now() : request.receivedDate();
        contractMapper.insertPaymentRecord(paymentId, tenantId, contractId, request.planId(), amount,
                receivedDate, method, request.voucherNo(), request.remark(), userId);
        contractMapper.updatePlanPaid(tenantId, request.planId(), newPaid, status, userId);
        audit.publish("CRM_PAYMENT_RECORDED", "CREATE", "crm_payment_record", paymentId, "HIGH");
        return listRecords(contractId).stream()
                .filter(item -> item.paymentId() == paymentId)
                .findFirst()
                .orElseThrow(() -> new BizException(CommonErrorCode.NOT_FOUND, "回款记录不存在"));
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordView> listRecords(long contractId) {
        long tenantId = CrmContext.tenantId();
        if (contractMapper.findById(tenantId, contractId) == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "合同不存在");
        }
        return contractMapper.listPaymentRecords(tenantId, contractId).stream()
                .map(row -> new PaymentRecordView(
                        Rows.lngVal(row, "paymentId"), Rows.lngVal(row, "contractId"), Rows.lngVal(row, "planId"),
                        Rows.dec(row, "amount"), Rows.date(row, "receivedDate"), Rows.str(row, "method"),
                        Rows.str(row, "voucherNo"), Rows.str(row, "remark"), Rows.str(row, "status"),
                        Rows.instant(row, "createdMs")))
                .toList();
    }

    /**
     * 逾期识别：未结清且已过到期日的计划，按到期日升序返回，同时给出逾期天数。
     */
    @Transactional(readOnly = true)
    public PageResult<OverduePlanView> overdue(Long customerId, Integer page, Integer size) {
        long tenantId = CrmContext.tenantId();
        int safePage = CrmContext.page(page);
        int safeSize = CrmContext.size(size);
        List<OverduePlanView> records = contractMapper
                .listOverduePlans(tenantId, customerId, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(row -> new OverduePlanView(
                        Rows.lngVal(row, "planId"), Rows.lngVal(row, "contractId"), Rows.str(row, "contractNo"),
                        Rows.lngVal(row, "customerId"), Rows.str(row, "customerName"), Rows.intVal(row, "periodNo"),
                        Rows.date(row, "dueDate"), Rows.dec(row, "amount"), Rows.dec(row, "paidAmount"),
                        Rows.dec(row, "remainingAmount"), Rows.intVal(row, "overdueDays"),
                        Rows.str(row, "collectionFlag"), Rows.str(row, "collectionNote")))
                .toList();
        long total = contractMapper.countOverduePlans(tenantId, customerId);
        return PageResult.of(records, total, safePage, safeSize);
    }

    /**
     * 扩展：回款提醒。未结清且到期日不晚于未来 N 天（含已逾期），可按负责人过滤。
     */
    @Transactional(readOnly = true)
    public PageResult<PaymentReminderView> reminders(Long ownerId, Integer upcomingDays, Integer page, Integer size) {
        long tenantId = CrmContext.tenantId();
        int safePage = CrmContext.page(page);
        int safeSize = CrmContext.size(size);
        int days = upcomingDays == null || upcomingDays < 0 ? 30 : upcomingDays;
        List<PaymentReminderView> records = contractMapper
                .listReminders(tenantId, ownerId, days, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(row -> {
                    int overdueDays = Rows.intVal(row, "overdueDays");
                    return new PaymentReminderView(
                            Rows.lngVal(row, "planId"), Rows.lngVal(row, "contractId"), Rows.str(row, "contractNo"),
                            Rows.lngVal(row, "customerId"), Rows.str(row, "customerName"), Rows.intVal(row, "periodNo"),
                            Rows.date(row, "dueDate"), Rows.dec(row, "amount"), Rows.dec(row, "paidAmount"),
                            Rows.dec(row, "remainingAmount"), overdueDays,
                            overdueDays > 0 ? "OVERDUE" : "DUE_SOON",
                            Rows.str(row, "collectionFlag"), Rows.str(row, "collectionNote"));
                })
                .toList();
        long total = contractMapper.countReminders(tenantId, ownerId, days);
        return PageResult.of(records, total, safePage, safeSize);
    }

    /**
     * 催收标记：仅对未结清的计划设置催收状态与备注。
     */
    @Transactional
    public void markCollection(long planId, CollectionFlagRequest request) {
        long tenantId = CrmContext.tenantId();
        Map<String, Object> plan = contractMapper.findPaymentPlan(tenantId, planId);
        if (plan == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "回款计划不存在");
        }
        String flag = request == null || request.collectionFlag() == null ? null : request.collectionFlag();
        if (flag == null || !COLLECTION_FLAGS.contains(flag)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "催收标记必须为 NONE/REMINDED/PROMISED/ESCALATED");
        }
        if ("PAID".equals(Rows.str(plan, "status"))) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "已结清的回款计划无需催收");
        }
        contractMapper.updateCollection(tenantId, planId, flag, request.note(), CrmContext.userId());
        audit.publish("CRM_COLLECTION_FLAGGED", "UPDATE", "crm_payment_plan", planId, "MEDIUM");
    }
}
