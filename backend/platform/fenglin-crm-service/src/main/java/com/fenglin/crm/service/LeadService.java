package com.fenglin.crm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.crm.CrmErrorCode;
import com.fenglin.crm.mapper.ActivityMapper;
import com.fenglin.crm.mapper.LeadMapper;
import com.fenglin.crm.service.CustomerService;
import com.fenglin.crm.service.OpportunityService;
import com.fenglin.crm.support.CrmAudit;
import com.fenglin.crm.support.CrmContext;
import com.fenglin.crm.support.CrmPiiService;
import com.fenglin.crm.support.Rows;
import com.fenglin.crm.web.dto.CrmDtos.ActivityView;
import com.fenglin.crm.web.dto.CrmDtos.AssignRequest;
import com.fenglin.crm.web.dto.CrmDtos.ConvertRequest;
import com.fenglin.crm.web.dto.CrmDtos.ConvertResult;
import com.fenglin.crm.web.dto.CrmDtos.CustomerCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.DuplicateCheckRequest;
import com.fenglin.crm.web.dto.CrmDtos.DuplicateCheckResult;
import com.fenglin.crm.web.dto.CrmDtos.DuplicateMatch;
import com.fenglin.crm.web.dto.CrmDtos.LeadCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.LeadDetailView;
import com.fenglin.crm.web.dto.CrmDtos.LeadView;
import com.fenglin.crm.web.dto.CrmDtos.OpportunityCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.OpportunityView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LeadService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Set<String> SOURCES = Set.of("MANUAL", "IMPORT", "FORM", "API", "PARTNER", "OPINION");
    /** 分配后状态推进为 ASSIGNED，因此 ASSIGNED 也必须允许转客户。 */
    private static final Set<String> CONVERTIBLE = Set.of("NEW", "ASSIGNED", "FOLLOWING", "NURTURING");

    private final LeadMapper leadMapper;
    private final ActivityMapper activityMapper;
    private final CustomerService customerService;
    private final OpportunityService opportunityService;
    private final CrmPiiService pii;
    private final CrmAudit audit;

    public LeadService(LeadMapper leadMapper, ActivityMapper activityMapper, CustomerService customerService,
                       OpportunityService opportunityService, CrmPiiService pii, CrmAudit audit) {
        this.leadMapper = leadMapper;
        this.activityMapper = activityMapper;
        this.customerService = customerService;
        this.opportunityService = opportunityService;
        this.pii = pii;
        this.audit = audit;
    }

    @Transactional
    public LeadView create(LeadCreateRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        if (request.name() == null || request.name().isBlank()) {
            throw new BizException(CrmErrorCode.LEAD_REQUIRED_MISSING, "线索名称不能为空");
        }
        String source = request.source() == null || request.source().isBlank() ? "MANUAL" : request.source();
        if (!SOURCES.contains(source)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持线索来源: " + source);
        }
        String mobileHash = request.mobile() == null || request.mobile().isBlank()
                ? null : pii.mobileHash(tenantId, request.mobile());
        if (mobileHash != null && leadMapper.countByMobileHash(tenantId, mobileHash) > 0) {
            throw new BizException(CrmErrorCode.LEAD_DUPLICATE);
        }
        long id = IdWorker.getId();
        String leadNo = "CRM" + LocalDate.now().format(MONTH)
                + String.format("%06d", leadMapper.nextBizSeq());
        leadMapper.insert(id, tenantId, leadNo, request.name(), request.contactName(),
                pii.encryptMobile(tenantId, "crm_lead", request.mobile()), mobileHash,
                pii.mobileTail(request.mobile()), request.email(), request.industry(), request.regionCode(),
                source, request.grade(), request.ownerId() == null ? userId : request.ownerId(),
                null, request.remark(), userId);
        audit.publish("CRM_LEAD_CREATED", "CREATE", "crm_lead", id, "MEDIUM");
        return toView(requireLead(tenantId, id));
    }

    @Transactional(readOnly = true)
    public DuplicateCheckResult checkDuplicate(DuplicateCheckRequest request) {
        long tenantId = CrmContext.tenantId();
        List<DuplicateMatch> exact = new ArrayList<>();
        if (request.mobile() != null && !request.mobile().isBlank()) {
            for (Map<String, Object> row : leadMapper.findExactByMobileHash(
                    tenantId, pii.mobileHash(tenantId, request.mobile()))) {
                exact.add(toMatch(row));
            }
        }
        List<DuplicateMatch> fuzzy = new ArrayList<>();
        if ((request.name() != null && !request.name().isBlank())
                || (request.email() != null && !request.email().isBlank())) {
            for (Map<String, Object> row : leadMapper.findFuzzy(tenantId, request.name(), request.email())) {
                fuzzy.add(toMatch(row));
            }
        }
        return new DuplicateCheckResult(exact, fuzzy);
    }

    @Transactional(readOnly = true)
    public LeadDetailView detail(long id) {
        long tenantId = CrmContext.tenantId();
        LeadView view = toView(requireLead(tenantId, id));
        return new LeadDetailView(view, activities(tenantId, "LEAD", id));
    }

    @Transactional(readOnly = true)
    public LeadDetailView detail(long id, long ownerId) {
        LeadDetailView detail = detail(id);
        if (detail.lead().ownerId() == null || detail.lead().ownerId() != ownerId) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "线索不存在");
        }
        return detail;
    }

    @Transactional(readOnly = true)
    public PageResult<LeadView> list(String status, Long ownerId, String keyword, Integer page, Integer size) {
        long tenantId = CrmContext.tenantId();
        int safePage = CrmContext.page(page);
        int safeSize = CrmContext.size(size);
        List<LeadView> records = leadMapper
                .list(tenantId, blankToNull(status), ownerId, blankToNull(keyword),
                        (safePage - 1) * safeSize, safeSize)
                .stream().map(this::toView).toList();
        long total = leadMapper.count(tenantId, blankToNull(status), ownerId, blankToNull(keyword));
        return PageResult.of(records, total, safePage, safeSize);
    }

    @Transactional
    public LeadView assign(long id, AssignRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        requireLead(tenantId, id);
        if (request.ownerId() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "owner_id 不能为空");
        }
        leadMapper.assign(tenantId, id, request.ownerId(), request.deptId(), userId);
        audit.publish("CRM_LEAD_ASSIGNED", "ASSIGN", "crm_lead", id, "MEDIUM");
        return toView(requireLead(tenantId, id));
    }

    /**
     * 线索转客户：同一事务内建客户（可选联系人/商机），并回写线索状态为 CONVERTED。
     */
    @Transactional
    public ConvertResult convert(long id, ConvertRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        Map<String, Object> row = requireLead(tenantId, id);
        String status = Rows.str(row, "status");
        if (!CONVERTIBLE.contains(status)) {
            throw new BizException(CrmErrorCode.LEAD_STATUS_INVALID);
        }
        String name = Rows.str(row, "name");
        // 仅在同一事务内解密用于建档，明文不会返回给调用方
        String mobileEnc = Rows.str(row, "mobileEnc");
        String mobile = mobileEnc == null ? null : pii.decryptMobile(tenantId, "crm_lead", mobileEnc);
        CustomerCreateRequest customerRequest = new CustomerCreateRequest(
                name, null, null, Rows.str(row, "industry"), null, Rows.str(row, "regionCode"),
                Rows.str(row, "grade"), null, Rows.lng(row, "ownerId"), null, mobile,
                null, null, "由线索 " + Rows.str(row, "leadNo") + " 转换", id);
        var customer = customerService.create(customerRequest);

        Long contactId = null;
        if (request == null || !Boolean.FALSE.equals(request.createContact())) {
            String contactName = Rows.str(row, "contactName");
            if (contactName != null && !contactName.isBlank()) {
                contactId = customerService.addContact(customer.customerId(), contactName,
                        Rows.str(row, "email"), mobile, userId);
            }
        }

        Long opportunityId = null;
        String opportunityNo = null;
        if (request != null && Boolean.TRUE.equals(request.createOpportunity())) {
            OpportunityCreateRequest opportunityRequest = new OpportunityCreateRequest(
                    request.opportunityName() == null || request.opportunityName().isBlank()
                            ? name + "商机" : request.opportunityName(),
                    customer.customerId(), request.amount(), request.stage(),
                    LocalDate.now().plusMonths(3), "LEAD", null, null, null);
            OpportunityView opportunity = opportunityService.create(opportunityRequest);
            opportunityId = opportunity.opportunityId();
            opportunityNo = opportunity.opportunityNo();
        }

        leadMapper.markConverted(tenantId, id, customer.customerId(), userId);
        audit.publish("CRM_LEAD_CONVERTED", "CONVERT", "crm_lead", id, "HIGH");
        return new ConvertResult(id, customer.customerId(), contactId, opportunityId, opportunityNo);
    }

    private List<ActivityView> activities(long tenantId, String bizType, long bizId) {
        return activityMapper.listByBiz(tenantId, bizType, bizId, 20).stream()
                .map(r -> new ActivityView(Rows.lngVal(r, "activityId"), Rows.str(r, "bizType"),
                        Rows.lngVal(r, "bizId"), Rows.str(r, "activityType"), Rows.str(r, "subject"),
                        Rows.str(r, "content"), Rows.intg(r, "durationMin"), Rows.str(r, "result"),
                        Rows.str(r, "nextPlan"), Rows.instant(r, "occurredMs"), Rows.lng(r, "ownerId")))
                .toList();
    }

    private Map<String, Object> requireLead(long tenantId, long id) {
        Map<String, Object> row = leadMapper.findById(tenantId, id);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "线索不存在");
        }
        return row;
    }

    private LeadView toView(Map<String, Object> row) {
        return new LeadView(
                Rows.lngVal(row, "leadId"), Rows.str(row, "leadNo"), Rows.str(row, "name"),
                Rows.str(row, "contactName"), pii.maskByTail(Rows.str(row, "mobileTail")),
                Rows.str(row, "email"), Rows.str(row, "industry"), Rows.str(row, "regionCode"),
                Rows.str(row, "source"), Rows.intVal(row, "score"), Rows.str(row, "grade"),
                Rows.str(row, "status"), Rows.lng(row, "ownerId"), Rows.lng(row, "deptId"),
                Rows.instant(row, "lastActivityMs"), Rows.lng(row, "convertedCustomerId"),
                Rows.instant(row, "createdMs"));
    }

    private DuplicateMatch toMatch(Map<String, Object> row) {
        return new DuplicateMatch(Rows.lngVal(row, "id"), Rows.str(row, "no"), Rows.str(row, "name"),
                pii.maskByTail(Rows.str(row, "mobileTail")));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
