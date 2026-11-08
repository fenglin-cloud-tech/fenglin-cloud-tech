package com.fenglin.crm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.crm.CrmErrorCode;
import com.fenglin.crm.mapper.ContactMapper;
import com.fenglin.crm.mapper.CustomerMapper;
import com.fenglin.crm.mapper.OpportunityMapper;
import com.fenglin.crm.mapper.QuotationMapper;
import com.fenglin.crm.support.CrmAudit;
import com.fenglin.crm.support.CrmContext;
import com.fenglin.crm.support.Rows;
import com.fenglin.crm.web.dto.CrmDtos.OpportunityCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.OpportunityView;
import com.fenglin.crm.web.dto.CrmDtos.PipelineStage;
import com.fenglin.crm.web.dto.CrmDtos.PipelineView;
import com.fenglin.crm.web.dto.CrmDtos.StageChangeRequest;
import com.fenglin.crm.web.dto.CrmDtos.StageLogView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpportunityService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final List<String> STAGE_ORDER = List.of("S1", "S2", "S3", "S4");
    private static final Map<String, BigDecimal> DEFAULT_WIN_RATE = Map.of(
            "S1", BigDecimal.valueOf(10), "S2", BigDecimal.valueOf(30), "S3", BigDecimal.valueOf(50),
            "S4", BigDecimal.valueOf(70), "WON", BigDecimal.valueOf(100), "LOST", BigDecimal.ZERO);

    private final OpportunityMapper opportunityMapper;
    private final CustomerMapper customerMapper;
    private final ContactMapper contactMapper;
    private final QuotationMapper quotationMapper;
    private final CrmAudit audit;

    public OpportunityService(OpportunityMapper opportunityMapper, CustomerMapper customerMapper,
                              ContactMapper contactMapper, QuotationMapper quotationMapper, CrmAudit audit) {
        this.opportunityMapper = opportunityMapper;
        this.customerMapper = customerMapper;
        this.contactMapper = contactMapper;
        this.quotationMapper = quotationMapper;
        this.audit = audit;
    }

    @Transactional
    public OpportunityView create(OpportunityCreateRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        if (request.name() == null || request.name().isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "商机名称不能为空");
        }
        if (customerMapper.findById(tenantId, request.customerId()) == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "客户不存在");
        }
        String stage = request.stage() == null || request.stage().isBlank() ? "S1" : request.stage();
        if (!DEFAULT_WIN_RATE.containsKey(stage) || "WON".equals(stage) || "LOST".equals(stage)) {
            throw new BizException(CrmErrorCode.OPPORTUNITY_STAGE_REJECTED, "新建商机阶段必须为 S1~S4");
        }
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        BigDecimal winRate = request.winRate() == null ? DEFAULT_WIN_RATE.get(stage) : request.winRate();
        long id = IdWorker.getId();
        String opportunityNo = "CRM" + LocalDate.now().format(MONTH)
                + String.format("%06d", opportunityMapper.nextBizSeq());
        opportunityMapper.insert(id, tenantId, opportunityNo, request.name(), request.customerId(), stage, winRate,
                amount, weighted(amount, winRate), request.expectedCloseDate(),
                request.ownerId() == null ? userId : request.ownerId(), null, request.source(),
                request.nextStep(), userId);
        audit.publish("CRM_OPPORTUNITY_CREATED", "CREATE", "crm_opportunity", id, "MEDIUM");
        return toView(requireOpportunity(tenantId, id));
    }

    @Transactional(readOnly = true)
    public PageResult<OpportunityView> list(String stage, String status, Long customerId, Long ownerId,
                                            Integer page, Integer size) {
        long tenantId = CrmContext.tenantId();
        int safePage = CrmContext.page(page);
        int safeSize = CrmContext.size(size);
        List<OpportunityView> records = opportunityMapper
                .list(tenantId, blankToNull(stage), blankToNull(status), customerId, ownerId,
                        (safePage - 1) * safeSize, safeSize)
                .stream().map(this::toView).toList();
        long total = opportunityMapper.count(tenantId, blankToNull(stage), blankToNull(status), customerId, ownerId);
        return PageResult.of(records, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public List<OpportunityView> listByCustomer(long customerId) {
        long tenantId = CrmContext.tenantId();
        return opportunityMapper.listByCustomer(tenantId, customerId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public OpportunityView detail(long opportunityId) {
        return toView(requireOpportunity(CrmContext.tenantId(), opportunityId));
    }

    @Transactional(readOnly = true)
    public List<StageLogView> stageLogs(long opportunityId) {
        long tenantId = CrmContext.tenantId();
        requireOpportunity(tenantId, opportunityId);
        return opportunityMapper.listStageLogs(tenantId, opportunityId).stream()
                .map(r -> new StageLogView(Rows.str(r, "fromStage"), Rows.str(r, "toStage"),
                        Rows.dec(r, "amountSnapshot"), Rows.lng(r, "changedBy"),
                        Rows.instant(r, "changedMs"), Rows.str(r, "opinion")))
                .toList();
    }

    /**
     * 阶段推进准入（01_丰麟云销_02 F-OPP-003）：
     * 仅允许向前推进；S3 需金额/预计成交日期/关键决策人；S4 需报价已审批或审批中；输单必须填原因。
     */
    @Transactional
    public OpportunityView changeStage(long opportunityId, StageChangeRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        Map<String, Object> row = requireOpportunity(tenantId, opportunityId);
        String currentStage = Rows.str(row, "stage");
        String currentStatus = Rows.str(row, "status");
        if (!"OPEN".equals(currentStatus)) {
            throw new BizException(CrmErrorCode.OPPORTUNITY_CONFLICT, "商机已结束，不可变更阶段");
        }
        String target = request.toStage();
        if (target == null || !DEFAULT_WIN_RATE.containsKey(target)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的商机阶段: " + target);
        }
        if (target.equals(currentStage)) {
            return toView(row);
        }
        boolean closing = "WON".equals(target) || "LOST".equals(target);
        if (!closing) {
            int from = STAGE_ORDER.indexOf(currentStage);
            int to = STAGE_ORDER.indexOf(target);
            if (from < 0 || to < 0 || to <= from) {
                throw new BizException(CrmErrorCode.OPPORTUNITY_STAGE_REJECTED, "商机阶段只允许向前推进");
            }
        }

        BigDecimal amount = request.amount() == null ? Rows.dec(row, "amount") : request.amount();
        LocalDate expectedCloseDate = request.expectedCloseDate() == null
                ? Rows.date(row, "expectedCloseDate") : request.expectedCloseDate();
        long customerId = Rows.lngVal(row, "customerId");

        if (("S3".equals(target) || "S4".equals(target)) && (amount == null || amount.signum() <= 0)) {
            throw new BizException(CrmErrorCode.OPPORTUNITY_STAGE_REJECTED, "进入 S3 及以后必须填写预计金额");
        }
        if ("S3".equals(target) || "S4".equals(target)) {
            if (expectedCloseDate == null) {
                throw new BizException(CrmErrorCode.OPPORTUNITY_STAGE_REJECTED, "进入 S3 及以后必须填写预计成交日期");
            }
        }
        if ("S3".equals(target) || "S4".equals(target)) {
            if (contactMapper.countKeyContacts(tenantId, customerId) == 0) {
                throw new BizException(CrmErrorCode.OPPORTUNITY_STAGE_REJECTED, "进入 S3 及以后必须标记关键决策人");
            }
        }
        if ("S4".equals(target) && quotationMapper.countApprovedOrPending(tenantId, opportunityId) == 0) {
            throw new BizException(CrmErrorCode.OPPORTUNITY_STAGE_REJECTED, "进入 S4 需先有审批中或已批准的报价单");
        }
        if ("LOST".equals(target) && (request.lostReason() == null || request.lostReason().isBlank())) {
            throw new BizException(CrmErrorCode.OPPORTUNITY_STAGE_REJECTED, "输单必须填写输单原因");
        }

        BigDecimal winRate = DEFAULT_WIN_RATE.get(target);
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        String status = closing ? target : "OPEN";
        String lostReason = "LOST".equals(target) ? request.lostReason() : null;
        opportunityMapper.updateStage(tenantId, opportunityId, target, winRate, safeAmount,
                weighted(safeAmount, winRate), status, lostReason, expectedCloseDate, request.nextStep(), userId);
        opportunityMapper.insertStageLog(IdWorker.getId(), tenantId, opportunityId, currentStage, target,
                safeAmount, userId, Instant.now(), request.opinion());
        audit.publish("CRM_OPPORTUNITY_STAGE_CHANGED", "UPDATE", "crm_opportunity", opportunityId, "HIGH");
        return toView(requireOpportunity(tenantId, opportunityId));
    }

    @Transactional(readOnly = true)
    public PipelineView pipeline() {
        long tenantId = CrmContext.tenantId();
        Map<String, PipelineStage> stages = new LinkedHashMap<>();
        for (String stage : List.of("S1", "S2", "S3", "S4")) {
            stages.put(stage, new PipelineStage(stage, 0, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        long openCount = 0;
        BigDecimal totalWeighted = BigDecimal.ZERO;
        for (Map<String, Object> row : opportunityMapper.pipeline(tenantId)) {
            String stage = Rows.str(row, "stage");
            long count = Rows.lngVal(row, "cnt");
            BigDecimal amount = Rows.dec(row, "amount");
            BigDecimal weighted = Rows.dec(row, "weightedAmount");
            if (stages.containsKey(stage)) {
                stages.put(stage, new PipelineStage(stage, count, amount, weighted));
                openCount += count;
                totalWeighted = totalWeighted.add(weighted);
            }
        }
        return new PipelineView(new ArrayList<>(stages.values()), totalWeighted, openCount);
    }

    private BigDecimal weighted(BigDecimal amount, BigDecimal winRate) {
        BigDecimal rate = winRate == null ? BigDecimal.ZERO : winRate;
        return amount.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> requireOpportunity(long tenantId, long opportunityId) {
        Map<String, Object> row = opportunityMapper.findById(tenantId, opportunityId);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "商机不存在");
        }
        return row;
    }

    private OpportunityView toView(Map<String, Object> row) {
        return new OpportunityView(
                Rows.lngVal(row, "opportunityId"), Rows.str(row, "opportunityNo"), Rows.str(row, "name"),
                Rows.lngVal(row, "customerId"), Rows.str(row, "stage"), Rows.dec(row, "winRate"),
                Rows.dec(row, "amount"), Rows.dec(row, "weightedAmount"), Rows.date(row, "expectedCloseDate"),
                Rows.lng(row, "ownerId"), Rows.str(row, "status"), Rows.str(row, "nextStep"),
                Rows.str(row, "lostReason"), Rows.instant(row, "createdMs"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
