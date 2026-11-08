package com.fenglin.crm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.crm.CrmErrorCode;
import com.fenglin.crm.mapper.ContractMapper;
import com.fenglin.crm.mapper.CustomerMapper;
import com.fenglin.crm.mapper.QuotationMapper;
import com.fenglin.crm.support.CrmAudit;
import com.fenglin.crm.support.CrmContext;
import com.fenglin.crm.support.Rows;
import com.fenglin.crm.web.dto.CrmDtos.ContractAmendRequest;
import com.fenglin.crm.web.dto.CrmDtos.ContractChangeView;
import com.fenglin.crm.web.dto.CrmDtos.ContractCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.ContractRenewRequest;
import com.fenglin.crm.web.dto.CrmDtos.ContractView;
import com.fenglin.crm.web.dto.CrmDtos.PaymentPlanRequest;
import com.fenglin.crm.web.dto.CrmDtos.PaymentPlanView;
import com.fenglin.crm.web.dto.CrmDtos.QuotationConvertRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContractService {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Set<String> SLA_LEVELS = Set.of("P1", "P2", "P3");

    private final ContractMapper contractMapper;
    private final CustomerMapper customerMapper;
    private final QuotationMapper quotationMapper;
    private final CrmAudit audit;

    public ContractService(ContractMapper contractMapper, CustomerMapper customerMapper,
                           QuotationMapper quotationMapper, CrmAudit audit) {
        this.contractMapper = contractMapper;
        this.customerMapper = customerMapper;
        this.quotationMapper = quotationMapper;
        this.audit = audit;
    }

    @Transactional
    public ContractView create(ContractCreateRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        if (customerMapper.findById(tenantId, request.customerId()) == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "客户不存在");
        }
        if (request.startDate() == null || request.endDate() == null
                || request.endDate().isBefore(request.startDate())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "合同起止日期不合法");
        }
        validateSla(request.slaLevel());
        long id = IdWorker.getId();
        String contractNo = nextContractNo();
        contractMapper.insert(id, tenantId, contractNo, request.customerId(), request.slaLevel(),
                request.servicePackage(), request.amount(), request.startDate(), request.endDate(), userId,
                null, null, userId);
        insertPlans(tenantId, id, request.paymentPlans(), userId);
        audit.publish("CRM_CONTRACT_CREATED", "CREATE", "crm_service_contract", id, "HIGH");
        return detail(id);
    }

    @Transactional(readOnly = true)
    public ContractView detail(long contractId) {
        long tenantId = CrmContext.tenantId();
        Map<String, Object> row = requireContract(tenantId, contractId);
        List<PaymentPlanView> plans = contractMapper.listPaymentPlans(tenantId, contractId).stream()
                .map(this::toPlanView)
                .toList();
        return new ContractView(
                Rows.lngVal(row, "contractId"), Rows.str(row, "contractNo"), Rows.lngVal(row, "customerId"),
                Rows.str(row, "slaLevel"), Rows.str(row, "servicePackage"), Rows.dec(row, "amount"),
                Rows.date(row, "startDate"), Rows.date(row, "endDate"), Rows.str(row, "status"), plans,
                Rows.instant(row, "createdMs"));
    }

    @Transactional(readOnly = true)
    public List<ContractView> listByCustomer(long customerId) {
        long tenantId = CrmContext.tenantId();
        return contractMapper.listByCustomer(tenantId, customerId).stream()
                .map(row -> detail(Rows.lngVal(row, "contractId")))
                .toList();
    }

    /**
     * 合同变更：更新金额/服务等级/服务包/到期日，并用新回款计划替换未回款的旧计划。
     * 变更单保留变更前后快照；已发生回款的期次不会被覆盖。
     */
    @Transactional
    public ContractView amend(long contractId, ContractAmendRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        Map<String, Object> before = requireContract(tenantId, contractId);
        if (!"ACTIVE".equals(Rows.str(before, "status"))) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "仅生效中的合同可变更");
        }
        validateSla(request.slaLevel());
        BigDecimal afterAmount = request.amount() == null ? Rows.dec(before, "amount") : request.amount();
        LocalDate afterEnd = request.endDate() == null ? Rows.date(before, "endDate") : request.endDate();
        LocalDate startDate = Rows.date(before, "startDate");
        if (afterAmount != null && afterAmount.signum() < 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "合同金额不能为负数");
        }
        if (afterEnd.isBefore(startDate)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "合同到期日不能早于开始日");
        }
        if (request.paymentPlans() != null && !request.paymentPlans().isEmpty()) {
            BigDecimal paidTotal = paidTotal(tenantId, contractId);
            BigDecimal expected = afterAmount == null ? BigDecimal.ZERO : afterAmount.subtract(paidTotal);
            validatePlans(request.paymentPlans(), expected);
            contractMapper.removeUnpaidPlans(tenantId, contractId, userId);
            insertPlans(tenantId, contractId, request.paymentPlans(), userId);
        }
        contractMapper.updateContract(tenantId, contractId, request.amount(), request.slaLevel(),
                request.servicePackage(), request.endDate(), userId);
        recordChange(tenantId, contractId, "AMEND", before, afterAmount, startDate, afterEnd,
                request.reason(), null, userId);
        audit.publish("CRM_CONTRACT_CHANGED", "UPDATE", "crm_service_contract", contractId, "HIGH");
        return detail(contractId);
    }

    /**
     * 合同续签：以原合同为父合同创建新合同，并记录续签变更单；原合同保持不变。
     */
    @Transactional
    public ContractView renew(long contractId, ContractRenewRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        Map<String, Object> parent = requireContract(tenantId, contractId);
        if ("TERMINATED".equals(Rows.str(parent, "status"))) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "已终止的合同不可续签");
        }
        if (request.startDate() == null || request.endDate() == null
                || request.endDate().isBefore(request.startDate())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "续签起止日期不合法");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "续签金额必须大于 0");
        }
        validateSla(request.slaLevel());
        long newId = IdWorker.getId();
        String contractNo = nextContractNo();
        contractMapper.insert(newId, tenantId, contractNo, Rows.lngVal(parent, "customerId"),
                request.slaLevel() == null ? Rows.str(parent, "slaLevel") : request.slaLevel(),
                request.servicePackage() == null ? Rows.str(parent, "servicePackage") : request.servicePackage(),
                request.amount(), request.startDate(), request.endDate(), userId, contractId, null, userId);
        insertPlans(tenantId, newId, request.paymentPlans(), userId);
        recordChange(tenantId, contractId, "RENEW", parent, request.amount(), request.startDate(),
                request.endDate(), request.reason(), newId, userId);
        audit.publish("CRM_CONTRACT_RENEWED", "CREATE", "crm_service_contract", newId, "HIGH");
        return detail(newId);
    }

    @Transactional(readOnly = true)
    public List<ContractChangeView> changes(long contractId) {
        long tenantId = CrmContext.tenantId();
        requireContract(tenantId, contractId);
        return contractMapper.listChanges(tenantId, contractId).stream()
                .map(row -> new ContractChangeView(
                        Rows.lngVal(row, "changeId"), Rows.str(row, "changeNo"), Rows.lngVal(row, "contractId"),
                        Rows.str(row, "changeType"), Rows.dec(row, "beforeAmount"), Rows.dec(row, "afterAmount"),
                        Rows.date(row, "beforeStartDate"), Rows.date(row, "beforeEndDate"),
                        Rows.date(row, "afterStartDate"), Rows.date(row, "afterEndDate"), Rows.str(row, "reason"),
                        Rows.lng(row, "newContractId"), Rows.str(row, "status"), Rows.instant(row, "createdMs")))
                .toList();
    }

    /**
     * 报价转合同：仅已审批通过的报价可转，合同金额取报价总额，报价状态置为 CONVERTED。
     * 未显式给出回款计划时，按合同到期日生成单期计划。
     */
    @Transactional
    public ContractView convertQuotation(long quotationId, QuotationConvertRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        Map<String, Object> quotation = quotationMapper.findById(tenantId, quotationId);
        if (quotation == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "报价单不存在");
        }
        String status = Rows.str(quotation, "status");
        if ("CONVERTED".equals(status)) {
            throw new BizException(CrmErrorCode.QUOTATION_STATUS_INVALID, "报价单已转合同");
        }
        if (!"APPROVED".equals(status)) {
            throw new BizException(CrmErrorCode.QUOTATION_STATUS_INVALID, "仅审批通过的报价可转合同");
        }
        if (request.startDate() == null || request.endDate() == null
                || request.endDate().isBefore(request.startDate())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "合同起止日期不合法");
        }
        validateSla(request.slaLevel());
        BigDecimal amount = Rows.dec(quotation, "totalAmount");
        long contractId = IdWorker.getId();
        String contractNo = nextContractNo();
        contractMapper.insert(contractId, tenantId, contractNo, Rows.lngVal(quotation, "customerId"),
                request.slaLevel(), request.servicePackage(), amount, request.startDate(), request.endDate(),
                userId, null, quotationId, userId);
        if (request.paymentPlans() != null && !request.paymentPlans().isEmpty()) {
            validatePlans(request.paymentPlans(), amount);
            insertPlans(tenantId, contractId, request.paymentPlans(), userId);
        } else {
            contractMapper.insertPaymentPlan(IdWorker.getId(), tenantId, contractId, 1, request.endDate(),
                    amount, userId);
        }
        quotationMapper.updateStatus(tenantId, quotationId, "CONVERTED", null, userId);
        audit.publish("CRM_QUOTATION_CONVERTED", "CONVERT", "crm_quotation", quotationId, "HIGH");
        audit.publish("CRM_CONTRACT_CREATED", "CREATE", "crm_service_contract", contractId, "HIGH");
        return detail(contractId);
    }

    private void insertPlans(long tenantId, long contractId, List<PaymentPlanRequest> plans, long userId) {
        if (plans == null) {
            return;
        }
        Set<Integer> periods = new HashSet<>();
        int index = 1;
        for (PaymentPlanRequest plan : plans) {
            int periodNo = plan.periodNo() == null ? index : plan.periodNo();
            if (!periods.add(periodNo)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "回款计划期次重复: " + periodNo);
            }
            if (plan.dueDate() == null || plan.amount() == null || plan.amount().signum() <= 0) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "回款计划日期与金额必须合法");
            }
            contractMapper.insertPaymentPlan(IdWorker.getId(), tenantId, contractId, periodNo, plan.dueDate(),
                    plan.amount(), userId);
            index++;
        }
    }

    private void validatePlans(List<PaymentPlanRequest> plans, BigDecimal expectedAmount) {
        BigDecimal sum = BigDecimal.ZERO;
        Set<Integer> periods = new HashSet<>();
        int index = 1;
        for (PaymentPlanRequest plan : plans) {
            int periodNo = plan.periodNo() == null ? index : plan.periodNo();
            if (!periods.add(periodNo)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "回款计划期次重复: " + periodNo);
            }
            if (plan.dueDate() == null || plan.amount() == null || plan.amount().signum() <= 0) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "回款计划日期与金额必须合法");
            }
            sum = sum.add(plan.amount());
            index++;
        }
        if (expectedAmount != null && sum.compareTo(expectedAmount) != 0) {
            throw new BizException(CommonErrorCode.BAD_REQUEST,
                    "回款计划合计与合同待收金额不一致: 计划 " + sum.toPlainString()
                            + "，待收 " + expectedAmount.toPlainString());
        }
    }

    private BigDecimal paidTotal(long tenantId, long contractId) {
        return contractMapper.listPaymentPlans(tenantId, contractId).stream()
                .map(row -> Rows.dec(row, "paidAmount"))
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void recordChange(long tenantId, long contractId, String changeType, Map<String, Object> before,
                              BigDecimal afterAmount, LocalDate afterStart, LocalDate afterEnd,
                              String reason, Long newContractId, long userId) {
        String changeNo = "CH" + LocalDate.now().format(MONTH)
                + String.format("%06d", contractMapper.nextChangeSeq());
        contractMapper.insertChange(IdWorker.getId(), tenantId, contractId, changeNo, changeType,
                Rows.dec(before, "amount"), afterAmount, Rows.date(before, "startDate"),
                Rows.date(before, "endDate"), afterStart, afterEnd, reason, newContractId, userId);
    }

    private PaymentPlanView toPlanView(Map<String, Object> row) {
        return new PaymentPlanView(Rows.lngVal(row, "planId"), Rows.intVal(row, "periodNo"),
                Rows.date(row, "dueDate"), Rows.dec(row, "amount"), Rows.dec(row, "paidAmount"),
                Rows.dec(row, "remainingAmount"), Rows.str(row, "status"), Rows.intVal(row, "overdueDays"),
                Rows.str(row, "collectionFlag"), Rows.str(row, "collectionNote"));
    }

    private String nextContractNo() {
        return "CT" + LocalDate.now().format(YEAR) + String.format("%06d", contractMapper.nextContractSeq());
    }

    private void validateSla(String slaLevel) {
        if (slaLevel != null && !slaLevel.isBlank() && !SLA_LEVELS.contains(slaLevel)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的 SLA 等级: " + slaLevel);
        }
    }

    private Map<String, Object> requireContract(long tenantId, long contractId) {
        Map<String, Object> row = contractMapper.findById(tenantId, contractId);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "合同不存在");
        }
        return row;
    }
}
