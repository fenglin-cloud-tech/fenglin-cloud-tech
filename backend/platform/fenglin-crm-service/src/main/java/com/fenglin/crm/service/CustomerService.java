package com.fenglin.crm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.core.PageResult;
import com.fenglin.crm.mapper.ActivityMapper;
import com.fenglin.crm.mapper.ContactMapper;
import com.fenglin.crm.mapper.CustomerMapper;
import com.fenglin.crm.support.CrmAudit;
import com.fenglin.crm.support.CrmContext;
import com.fenglin.crm.support.CrmPiiService;
import com.fenglin.crm.support.Rows;
import com.fenglin.crm.web.dto.CrmDtos.ActivityView;
import com.fenglin.crm.web.dto.CrmDtos.ContactCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.ContactView;
import com.fenglin.crm.web.dto.CrmDtos.CustomerCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.CustomerDetailView;
import com.fenglin.crm.web.dto.CrmDtos.CustomerOwnerRequest;
import com.fenglin.crm.web.dto.CrmDtos.CustomerView;
import com.fenglin.crm.web.dto.CrmDtos.DuplicateCheckRequest;
import com.fenglin.crm.web.dto.CrmDtos.DuplicateCheckResult;
import com.fenglin.crm.web.dto.CrmDtos.DuplicateMatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CustomerService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Set<String> ROLE_TYPES = Set.of("DECISION", "INFLUENCER", "USER", "FINANCE");

    private final CustomerMapper customerMapper;
    private final ContactMapper contactMapper;
    private final ActivityMapper activityMapper;
    private final OpportunityService opportunityService;
    private final CrmPiiService pii;
    private final CrmAudit audit;

    public CustomerService(CustomerMapper customerMapper, ContactMapper contactMapper, ActivityMapper activityMapper,
                           OpportunityService opportunityService, CrmPiiService pii, CrmAudit audit) {
        this.customerMapper = customerMapper;
        this.contactMapper = contactMapper;
        this.activityMapper = activityMapper;
        this.opportunityService = opportunityService;
        this.pii = pii;
        this.audit = audit;
    }

    /**
     * 客户建档。强标识（手机号哈希 / 统一社会信用代码）重复直接拒绝；名称仅做提示，不阻断。
     */
    @Transactional
    public CustomerView create(CustomerCreateRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        if (request.name() == null || request.name().isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "客户名称不能为空");
        }
        String mobile = request.mobile();
        String mobileHash = mobile == null || mobile.isBlank() ? null : pii.mobileHash(tenantId, mobile);
        if (mobileHash != null && customerMapper.countByMobileHash(tenantId, mobileHash) > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "客户已存在相同手机号");
        }
        if (request.uscc() != null && !request.uscc().isBlank()
                && customerMapper.countByUscc(tenantId, request.uscc()) > 0) {
            throw new BizException(CommonErrorCode.CONFLICT, "客户已存在相同统一社会信用代码");
        }
        long id = IdWorker.getId();
        String customerNo = "CUS" + LocalDate.now().format(MONTH)
                + String.format("%06d", customerMapper.nextCustomerSeq());
        customerMapper.insert(id, tenantId, customerNo, request.name(), request.shortName(), request.uscc(),
                request.industry(), request.scale(), request.regionCode(), request.grade(), request.segment(),
                request.ownerId() == null ? userId : request.ownerId(), request.csmId(), null,
                pii.encryptMobile(tenantId, "crm_customer", mobile), mobileHash, pii.mobileTail(mobile),
                request.annualRevenue(), request.arr(), request.sourceLeadId(), request.remark(), userId);
        audit.publish("CRM_CUSTOMER_CREATED", "CREATE", "crm_customer", id, "MEDIUM");
        return toView(requireCustomer(tenantId, id));
    }

    @Transactional
    public Long addContact(long customerId, String name, String email, String mobile, long operatorId) {
        long tenantId = CrmContext.tenantId();
        if (name == null || name.isBlank()) {
            return null;
        }
        long id = IdWorker.getId();
        contactMapper.insert(id, tenantId, customerId, name, null, null,
                pii.encryptMobile(tenantId, "crm_contact", mobile),
                mobile == null || mobile.isBlank() ? null : pii.mobileHash(tenantId, mobile),
                pii.mobileTail(mobile), email, null, 0, false, operatorId);
        audit.publish("CRM_CONTACT_CREATED", "CREATE", "crm_contact", id, "MEDIUM");
        return id;
    }

    @Transactional
    public ContactView createContact(ContactCreateRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        requireCustomer(tenantId, request.customerId());
        if (request.name() == null || request.name().isBlank()) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "联系人姓名不能为空");
        }
        if (request.roleType() != null && !request.roleType().isBlank()
                && !ROLE_TYPES.contains(request.roleType())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的联系人角色: " + request.roleType());
        }
        int influence = request.influence() == null ? 0 : Math.min(Math.max(request.influence(), 0), 5);
        long id = IdWorker.getId();
        contactMapper.insert(id, tenantId, request.customerId(), request.name(), request.position(),
                blankToNull(request.roleType()),
                pii.encryptMobile(tenantId, "crm_contact", request.mobile()),
                request.mobile() == null || request.mobile().isBlank() ? null : pii.mobileHash(tenantId, request.mobile()),
                pii.mobileTail(request.mobile()), request.email(), request.wechat(), influence,
                Boolean.TRUE.equals(request.isKey()), userId);
        audit.publish("CRM_CONTACT_CREATED", "CREATE", "crm_contact", id, "MEDIUM");
        return toContactView(contactMapper.findById(tenantId, id));
    }

    @Transactional(readOnly = true)
    public PageResult<CustomerView> list(String keyword, Long ownerId, String segment, String status,
                                         Integer page, Integer size) {
        long tenantId = CrmContext.tenantId();
        int safePage = CrmContext.page(page);
        int safeSize = CrmContext.size(size);
        List<CustomerView> records = customerMapper
                .list(tenantId, blankToNull(keyword), ownerId, blankToNull(segment), blankToNull(status),
                        (safePage - 1) * safeSize, safeSize)
                .stream().map(this::toView).toList();
        long total = customerMapper.count(tenantId, blankToNull(keyword), ownerId, blankToNull(segment),
                blankToNull(status));
        return PageResult.of(records, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public CustomerDetailView detail(long customerId) {
        long tenantId = CrmContext.tenantId();
        CustomerView customer = toView(requireCustomer(tenantId, customerId));
        List<ContactView> contacts = contacts(customerId);
        List<ActivityView> activities = activityMapper.listByBiz(tenantId, "CUSTOMER", customerId, 20).stream()
                .map(r -> new ActivityView(Rows.lngVal(r, "activityId"), Rows.str(r, "bizType"),
                        Rows.lngVal(r, "bizId"), Rows.str(r, "activityType"), Rows.str(r, "subject"),
                        Rows.str(r, "content"), Rows.intg(r, "durationMin"), Rows.str(r, "result"),
                        Rows.str(r, "nextPlan"), Rows.instant(r, "occurredMs"), Rows.lng(r, "ownerId")))
                .toList();
        return new CustomerDetailView(customer, contacts,
                opportunityService.listByCustomer(customerId), activities);
    }

    @Transactional(readOnly = true)
    public List<ContactView> contacts(long customerId) {
        long tenantId = CrmContext.tenantId();
        requireCustomer(tenantId, customerId);
        return contactMapper.listByCustomer(tenantId, customerId).stream().map(this::toContactView).toList();
    }

    @Transactional(readOnly = true)
    public DuplicateCheckResult checkDuplicate(DuplicateCheckRequest request) {
        long tenantId = CrmContext.tenantId();
        String mobileHash = request.mobile() == null || request.mobile().isBlank()
                ? null : pii.mobileHash(tenantId, request.mobile());
        List<DuplicateMatch> matches = customerMapper
                .findDuplicates(tenantId, mobileHash, blankToNull(request.name()), null)
                .stream()
                .map(row -> new DuplicateMatch(Rows.lngVal(row, "customerId"), Rows.str(row, "customerNo"),
                        Rows.str(row, "name"), pii.maskByTail(Rows.str(row, "mobileTail"))))
                .toList();
        return new DuplicateCheckResult(matches, List.of());
    }

    @Transactional
    public CustomerView changeOwner(long customerId, CustomerOwnerRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        requireCustomer(tenantId, customerId);
        if (request == null || request.ownerId() == null) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "owner_id 不能为空");
        }
        customerMapper.updateOwner(tenantId, customerId, request.ownerId(), userId);
        audit.publish("CRM_CUSTOMER_OWNER_CHANGED", "UPDATE", "crm_customer", customerId, "HIGH");
        return toView(requireCustomer(tenantId, customerId));
    }

    private Map<String, Object> requireCustomer(long tenantId, long customerId) {
        Map<String, Object> row = customerMapper.findById(tenantId, customerId);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "客户不存在");
        }
        return row;
    }

    private CustomerView toView(Map<String, Object> row) {
        return new CustomerView(
                Rows.lngVal(row, "customerId"), Rows.str(row, "customerNo"), Rows.str(row, "name"),
                Rows.str(row, "shortName"), Rows.str(row, "uscc"), Rows.str(row, "industry"),
                Rows.str(row, "scale"), Rows.str(row, "regionCode"), Rows.str(row, "grade"),
                Rows.str(row, "segment"), Rows.lng(row, "ownerId"), Rows.lng(row, "csmId"),
                Rows.lng(row, "deptId"), pii.maskByTail(Rows.str(row, "mobileTail")),
                Rows.dec(row, "annualRevenue"), Rows.dec(row, "arr"), Rows.str(row, "status"),
                Rows.instant(row, "createdMs"));
    }

    private ContactView toContactView(Map<String, Object> row) {
        return new ContactView(
                Rows.lngVal(row, "contactId"), Rows.lngVal(row, "customerId"), Rows.str(row, "name"),
                Rows.str(row, "position"), Rows.str(row, "roleType"),
                pii.maskByTail(Rows.str(row, "mobileTail")), Rows.str(row, "email"), Rows.str(row, "wechat"),
                Rows.intVal(row, "influence"), Rows.bool(row, "isKey"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
