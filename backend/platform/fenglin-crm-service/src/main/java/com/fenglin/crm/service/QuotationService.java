package com.fenglin.crm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.crm.CrmErrorCode;
import com.fenglin.crm.client.WorkflowClient;
import com.fenglin.crm.mapper.CustomerMapper;
import com.fenglin.crm.mapper.QuotationMapper;
import com.fenglin.crm.support.CrmAudit;
import com.fenglin.crm.support.CrmContext;
import com.fenglin.crm.support.Rows;
import com.fenglin.crm.web.dto.CrmDtos.QuotationCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.QuotationItemRequest;
import com.fenglin.crm.web.dto.CrmDtos.QuotationItemView;
import com.fenglin.crm.web.dto.CrmDtos.QuotationListItemView;
import com.fenglin.crm.web.dto.CrmDtos.QuotationSubmitResult;
import com.fenglin.crm.web.dto.CrmDtos.QuotationView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class QuotationService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final QuotationMapper quotationMapper;
    private final CustomerMapper customerMapper;
    private final WorkflowClient workflowClient;
    private final CrmAudit audit;
    private final BigDecimal discountThreshold;
    private final BigDecimal amountThreshold;

    public QuotationService(QuotationMapper quotationMapper, CustomerMapper customerMapper,
                            WorkflowClient workflowClient, CrmAudit audit,
                            @Value("${fenglin.crm.approval-discount-threshold}") BigDecimal discountThreshold,
                            @Value("${fenglin.crm.approval-amount-threshold}") BigDecimal amountThreshold) {
        this.quotationMapper = quotationMapper;
        this.customerMapper = customerMapper;
        this.workflowClient = workflowClient;
        this.audit = audit;
        this.discountThreshold = discountThreshold;
        this.amountThreshold = amountThreshold;
    }

    @Transactional
    public QuotationView create(QuotationCreateRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        if (customerMapper.findById(tenantId, request.customerId()) == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "客户不存在");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "报价明细不能为空");
        }
        BigDecimal originTotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (QuotationItemRequest item : request.items()) {
            if (item.productCode() == null || item.productCode().isBlank()) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "产品编码不能为空");
            }
            if (item.quantity() == null || item.quantity().signum() <= 0) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "报价数量必须大于 0");
            }
            if (item.unitPrice() == null || item.unitPrice().signum() < 0) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "单价不能为负数");
            }
            if (item.discountRate() != null && (item.discountRate().signum() < 0
                    || item.discountRate().compareTo(HUNDRED) > 0)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "行折扣必须在 0~100 之间");
            }
            originTotal = originTotal.add(lineOrigin(item));
            total = total.add(lineAmount(item));
        }
        BigDecimal discountRate = originTotal.signum() == 0 ? BigDecimal.ZERO
                : originTotal.subtract(total).multiply(HUNDRED).divide(originTotal, 2, RoundingMode.HALF_UP);
        int versionNo = request.opportunityId() == null ? 1
                : quotationMapper.maxVersion(tenantId, request.opportunityId()) + 1;
        long id = IdWorker.getId();
        String quotationNo = "QUO" + LocalDate.now().format(MONTH)
                + String.format("%06d", quotationMapper.nextQuotationSeq());
        BigDecimal taxRate = request.taxRate() == null ? new BigDecimal("6.0000") : request.taxRate();
        quotationMapper.insert(id, tenantId, quotationNo, request.opportunityId(), request.customerId(), versionNo,
                total, discountRate, taxRate, request.validUntil(), userId, request.remark(), userId);
        int lineNo = 1;
        for (QuotationItemRequest item : request.items()) {
            quotationMapper.insertItem(IdWorker.getId(), tenantId, id, item.productCode(), item.productName(),
                    item.unit(), item.quantity(), item.unitPrice(), item.discountRate(), lineAmount(item),
                    lineNo++, userId);
        }
        audit.publish("CRM_QUOTATION_CREATED", "CREATE", "crm_quotation", id, "MEDIUM");
        return detail(id);
    }

    @Transactional(readOnly = true)
    public QuotationView detail(long quotationId) {
        long tenantId = CrmContext.tenantId();
        Map<String, Object> row = requireQuotation(tenantId, quotationId);
        List<QuotationItemView> items = quotationMapper.listItems(tenantId, quotationId).stream()
                .map(r -> new QuotationItemView(Rows.intVal(r, "lineNo"), Rows.str(r, "productCode"),
                        Rows.str(r, "productName"), Rows.str(r, "unit"), Rows.dec(r, "quantity"),
                        Rows.dec(r, "unitPrice"), Rows.dec(r, "discountRate"), Rows.dec(r, "amount")))
                .toList();
        return new QuotationView(
                Rows.lngVal(row, "quotationId"), Rows.str(row, "quotationNo"), Rows.lng(row, "opportunityId"),
                Rows.lngVal(row, "customerId"), Rows.intVal(row, "versionNo"), Rows.dec(row, "totalAmount"),
                Rows.dec(row, "discountRate"), Rows.dec(row, "taxRate"), Rows.date(row, "validUntil"),
                Rows.str(row, "status"), Rows.str(row, "approveInstanceId"), items,
                Rows.instant(row, "createdMs"));
    }

    /** 扩展：报价分页列表（可按状态/负责人/客户过滤，支持员工端「我的报价」）。 */
    @Transactional(readOnly = true)
    public PageResult<QuotationListItemView> list(String status, Long ownerId, Long customerId, String keyword,
                                                  Integer page, Integer size) {
        long tenantId = CrmContext.tenantId();
        int safePage = CrmContext.page(page);
        int safeSize = CrmContext.size(size);
        List<QuotationListItemView> records = quotationMapper
                .list(tenantId, blankToNull(status), ownerId, customerId, blankToNull(keyword),
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(row -> new QuotationListItemView(
                        Rows.lngVal(row, "quotationId"), Rows.str(row, "quotationNo"), Rows.lng(row, "opportunityId"),
                        Rows.lngVal(row, "customerId"), Rows.str(row, "customerName"), Rows.intVal(row, "versionNo"),
                        Rows.dec(row, "totalAmount"), Rows.str(row, "status"), Rows.lng(row, "ownerId"),
                        Rows.instant(row, "createdMs")))
                .toList();
        long total = quotationMapper.count(tenantId, blankToNull(status), ownerId, customerId, blankToNull(keyword));
        return PageResult.of(records, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public QuotationView detail(long quotationId, long ownerId) {
        Map<String, Object> row = requireQuotation(CrmContext.tenantId(), quotationId);
        if (!Long.valueOf(ownerId).equals(Rows.lngVal(row, "ownerId"))) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "报价单不存在");
        }
        return detail(quotationId);
    }

    /**
     * 提交审批：综合折扣 > 20% 或含税总额 > 100 万走工作流中心；否则自动通过。
     */
    @Transactional
    public QuotationSubmitResult submit(long quotationId, String permissions, String requestId) {
        long tenantId = CrmContext.tenantId();
        Map<String, Object> row = requireQuotation(tenantId, quotationId);
        if (!"DRAFT".equals(Rows.str(row, "status"))) {
            throw new BizException(CrmErrorCode.QUOTATION_STATUS_INVALID, "仅草稿报价可提交审批");
        }
        BigDecimal discountRate = Rows.dec(row, "discountRate");
        BigDecimal total = Rows.dec(row, "totalAmount");
        boolean approvalRequired = (discountRate != null && discountRate.compareTo(discountThreshold) > 0)
                || (total != null && total.compareTo(amountThreshold) > 0);
        if (!approvalRequired) {
            quotationMapper.updateStatus(tenantId, quotationId, "APPROVED", null, CrmContext.userId());
            audit.publish("CRM_QUOTATION_AUTO_APPROVED", "SUBMIT", "crm_quotation", quotationId, "MEDIUM");
            return new QuotationSubmitResult(quotationId, "APPROVED", null, false);
        }
        String instanceId = workflowClient.startQuotationApproval(permissions, requestId,
                Rows.str(row, "quotationNo"), quotationId, Rows.lngVal(row, "customerId"), total, discountRate);
        quotationMapper.updateStatus(tenantId, quotationId, "PENDING", instanceId, CrmContext.userId());
        audit.publish("CRM_QUOTATION_SUBMITTED", "SUBMIT", "crm_quotation", quotationId, "HIGH");
        return new QuotationSubmitResult(quotationId, "PENDING", instanceId, true);
    }

    /**
     * 扩展：审批结果回调。工作流中心审批完成后回写报价单状态（下一步可替换为 MQ 事件订阅）。
     */
    @Transactional
    public QuotationView handleApproval(long quotationId, String result, String comment) {
        long tenantId = CrmContext.tenantId();
        Map<String, Object> row = requireQuotation(tenantId, quotationId);
        if (!"PENDING".equals(Rows.str(row, "status"))) {
            throw new BizException(CrmErrorCode.QUOTATION_STATUS_INVALID, "仅审批中的报价可回写结果");
        }
        String target = "APPROVED".equalsIgnoreCase(result) ? "APPROVED"
                : "REJECTED".equalsIgnoreCase(result) ? "REJECTED" : null;
        if (target == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "审批结果必须为 APPROVED 或 REJECTED");
        }
        quotationMapper.updateStatus(tenantId, quotationId, target, null, CrmContext.userId());
        audit.publish("CRM_QUOTATION_" + target, "APPROVE", "crm_quotation", quotationId, "HIGH");
        return detail(quotationId);
    }

    private BigDecimal lineOrigin(QuotationItemRequest item) {
        return item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal lineAmount(QuotationItemRequest item) {
        BigDecimal rate = item.discountRate() == null ? BigDecimal.ZERO : item.discountRate();
        BigDecimal factor = BigDecimal.ONE.subtract(rate.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        return item.quantity().multiply(item.unitPrice()).multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Map<String, Object> requireQuotation(long tenantId, long quotationId) {
        Map<String, Object> row = quotationMapper.findById(tenantId, quotationId);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "报价单不存在");
        }
        return row;
    }
}
