package com.fenglin.crm.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.crm.mapper.ActivityMapper;
import com.fenglin.crm.mapper.CustomerMapper;
import com.fenglin.crm.mapper.LeadMapper;
import com.fenglin.crm.mapper.OpportunityMapper;
import com.fenglin.crm.support.CrmAudit;
import com.fenglin.crm.support.CrmContext;
import com.fenglin.crm.web.dto.CrmDtos.ActivityCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.ActivityView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class ActivityService {

    private static final Set<String> BIZ_TYPES = Set.of("LEAD", "CUSTOMER", "OPPORTUNITY", "RENEWAL", "PARTNER");
    private static final Set<String> ACTIVITY_TYPES = Set.of("CALL", "EMAIL", "VISIT", "WECHAT", "MEETING");

    private final ActivityMapper activityMapper;
    private final LeadMapper leadMapper;
    private final CustomerMapper customerMapper;
    private final OpportunityMapper opportunityMapper;
    private final CrmAudit audit;

    public ActivityService(ActivityMapper activityMapper, LeadMapper leadMapper, CustomerMapper customerMapper,
                           OpportunityMapper opportunityMapper, CrmAudit audit) {
        this.activityMapper = activityMapper;
        this.leadMapper = leadMapper;
        this.customerMapper = customerMapper;
        this.opportunityMapper = opportunityMapper;
        this.audit = audit;
    }

    @Transactional
    public ActivityView create(ActivityCreateRequest request) {
        long tenantId = CrmContext.tenantId();
        long userId = CrmContext.userId();
        if (!BIZ_TYPES.contains(request.bizType())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的业务类型: " + request.bizType());
        }
        if (!ACTIVITY_TYPES.contains(request.activityType())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "不支持的活动类型: " + request.activityType());
        }
        requireBizExists(tenantId, request.bizType(), request.bizId());
        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        long id = IdWorker.getId();
        activityMapper.insert(id, tenantId, request.bizType(), request.bizId(), request.activityType(),
                request.subject(), request.content(), request.durationMin(), request.result(),
                request.nextPlan(), occurredAt, userId, userId);
        if ("LEAD".equals(request.bizType())) {
            leadMapper.touchLastActivity(tenantId, request.bizId(), occurredAt, userId);
        }
        audit.publish("CRM_ACTIVITY_CREATED", "CREATE", "crm_activity", id, "LOW");
        return new ActivityView(id, request.bizType(), request.bizId(), request.activityType(),
                request.subject(), request.content(), request.durationMin(), request.result(),
                request.nextPlan(), occurredAt, userId);
    }

    @Transactional(readOnly = true)
    public List<ActivityView> list(String bizType, long bizId, Integer limit) {
        long tenantId = CrmContext.tenantId();
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 200);
        return activityMapper.listByBiz(tenantId, bizType, bizId, safeLimit).stream()
                .map(r -> new ActivityView(
                        ((Number) r.get("activityId")).longValue(),
                        String.valueOf(r.get("bizType")),
                        ((Number) r.get("bizId")).longValue(),
                        String.valueOf(r.get("activityType")),
                        r.get("subject") == null ? null : String.valueOf(r.get("subject")),
                        r.get("content") == null ? null : String.valueOf(r.get("content")),
                        r.get("durationMin") == null ? null : ((Number) r.get("durationMin")).intValue(),
                        r.get("result") == null ? null : String.valueOf(r.get("result")),
                        r.get("nextPlan") == null ? null : String.valueOf(r.get("nextPlan")),
                        r.get("occurredMs") == null ? null
                                : Instant.ofEpochMilli(((Number) r.get("occurredMs")).longValue()),
                        r.get("ownerId") == null ? null : ((Number) r.get("ownerId")).longValue()))
                .toList();
    }

    private void requireBizExists(long tenantId, String bizType, long bizId) {
        boolean exists = switch (bizType) {
            case "LEAD" -> leadMapper.findById(tenantId, bizId) != null;
            case "CUSTOMER" -> customerMapper.findById(tenantId, bizId) != null;
            case "OPPORTUNITY" -> opportunityMapper.findById(tenantId, bizId) != null;
            default -> true;
        };
        if (!exists) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "跟进对象不存在");
        }
    }
}
