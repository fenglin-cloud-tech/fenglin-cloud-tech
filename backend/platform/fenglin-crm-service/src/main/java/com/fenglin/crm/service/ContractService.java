package com.fenglin.crm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.crm.mapper.ContractMapper;
import com.fenglin.crm.mapper.CustomerMapper;
import com.fenglin.crm.support.CrmAudit;
import com.fenglin.crm.support.CrmContext;
import com.fenglin.crm.support.Rows;
import com.fenglin.crm.web.dto.CrmDtos.ContractCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.ContractView;
import com.fenglin.crm.web.dto.CrmDtos.PaymentPlanRequest;
import com.fenglin.crm.web.dto.CrmDtos.PaymentPlanView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContractService {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final Set<String> SLA_LEVELS = Set.of("P1", "P2", "P3");

    private final ContractMapper contractMapper;
    private final CustomerMapper customerMapper;
    private final CrmAudit audit;

    public ContractService(ContractMapper contractMapper, CustomerMapper customerMapper, CrmAudit audit) {
        this.contractMapper = contractMapper;
        this.customerMapper = customerMapper;
        this.audit = audit;
    }

    /**
     * 合同创建 + 回款计划（回款闭环在下一批实现，本次只落计划）。
     */
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
        if (request.slaLevel() != null && !request.slaLevel().isBlank()
                && !SLA_LEVELS.contains(request.slaLevel())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的 SLA 等级: " + request.slaLevel());
        }
        long id = IdWorker.getId();
        String contractNo = "CT" + LocalDate.now().format(YEAR)
                + String.format("%06d", contractMapper.nextContractSeq());
        contractMapper.insert(id, tenantId, contractNo, request.customerId(), request.slaLevel(),
                request.servicePackage(), request.amount(), request.startDate(), request.endDate(), userId, userId);
        if (request.paymentPlans() != null) {
            Set<Integer> periods = new HashSet<>();
            int index = 1;
            for (PaymentPlanRequest plan : request.paymentPlans()) {
                int periodNo = plan.periodNo() == null ? index : plan.periodNo();
                if (!periods.add(periodNo)) {
                    throw new BizException(CommonErrorCode.BAD_REQUEST, "回款计划期次重复: " + periodNo);
                }
                if (plan.dueDate() == null || plan.amount() == null || plan.amount().signum() <= 0) {
                    throw new BizException(CommonErrorCode.BAD_REQUEST, "回款计划日期与金额必须合法");
                }
                contractMapper.insertPaymentPlan(IdWorker.getId(), tenantId, id, periodNo, plan.dueDate(),
                        plan.amount(), userId);
                index++;
            }
        }
        audit.publish("CRM_CONTRACT_CREATED", "CREATE", "crm_service_contract", id, "HIGH");
        return detail(id);
    }

    @Transactional(readOnly = true)
    public ContractView detail(long contractId) {
        long tenantId = CrmContext.tenantId();
        Map<String, Object> row = contractMapper.findById(tenantId, contractId);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "合同不存在");
        }
        List<PaymentPlanView> plans = contractMapper.listPaymentPlans(tenantId, contractId).stream()
                .map(r -> new PaymentPlanView(Rows.intVal(r, "periodNo"), Rows.date(r, "dueDate"),
                        Rows.dec(r, "amount"), Rows.dec(r, "paidAmount"), Rows.str(r, "status")))
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
}
