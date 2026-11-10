package com.fenglin.collab.service;

import com.fenglin.collab.CollabErrorCode;
import com.fenglin.collab.mapper.AdminMapper;
import com.fenglin.collab.support.CollabAudit;
import com.fenglin.collab.support.CollabContext;
import com.fenglin.collab.support.Rows;
import com.fenglin.collab.web.dto.CollabDtos.CalendarCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.CalendarView;
import com.fenglin.collab.web.dto.CollabDtos.ConversationAuditPage;
import com.fenglin.collab.web.dto.CollabDtos.ConversationAuditView;
import com.fenglin.collab.web.dto.CollabDtos.DocPolicyRequest;
import com.fenglin.collab.web.dto.CollabDtos.DocPolicyView;
import com.fenglin.collab.web.dto.CollabDtos.PeriodUpsertRequest;
import com.fenglin.collab.web.dto.CollabDtos.PeriodView;
import com.fenglin.collab.web.dto.CollabDtos.RoomCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.RoomView;
import com.fenglin.collab.web.dto.CollabDtos.SensitiveWordPolicyRequest;
import com.fenglin.collab.web.dto.CollabDtos.SensitiveWordPolicyView;
import com.fenglin.collab.web.dto.CollabDtos.TemplateCreateRequest;
import com.fenglin.collab.web.dto.CollabDtos.TemplateView;
import com.fenglin.common.core.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 云协管理端（治理与配置）：敏感词策略、会话审计、文档策略、公共日历、会议室、周期与审批模板。
 * 域内写入口统一收敛到 /api/collab/v1/admin/**，与接口文档 4.8 节路径一致。
 */
@Service
public class CollabAdminService {

    private final AdminMapper adminMapper;
    private final CollabSettingService settingService;
    private final CalendarService calendarService;
    private final MeetingService meetingService;
    private final OkrService okrService;
    private final ApprovalService approvalService;
    private final CollabAudit audit;

    public CollabAdminService(AdminMapper adminMapper, CollabSettingService settingService,
                              CalendarService calendarService, MeetingService meetingService,
                              OkrService okrService, ApprovalService approvalService, CollabAudit audit) {
        this.adminMapper = adminMapper;
        this.settingService = settingService;
        this.calendarService = calendarService;
        this.meetingService = meetingService;
        this.okrService = okrService;
        this.approvalService = approvalService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SensitiveWordPolicyView sensitivePolicy() {
        return settingService.sensitivePolicyView();
    }

    @Transactional
    public SensitiveWordPolicyView saveSensitivePolicy(SensitiveWordPolicyRequest request) {
        return settingService.saveSensitivePolicy(request);
    }

    @Transactional(readOnly = true)
    public DocPolicyView docPolicy() {
        CollabSettingService.DocPolicy policy = settingService.docPolicy();
        return new DocPolicyView(policy.confidentialNoLink(), policy.confidentialNoDownload(),
                policy.watermarkTemplate(), policy.maxConcurrentEditors());
    }

    @Transactional
    public DocPolicyView saveDocPolicy(DocPolicyRequest request) {
        return settingService.saveDocPolicy(request);
    }

    /**
     * 会话审计：按标题关键字与创建时间范围过滤，返回消息量用于治理抽样。
     * 偏差：云协会话未建模所属部门，接口不接受 dept_id，部门级数据范围由平台数据权限在网关侧约束。
     */
    @Transactional(readOnly = true)
    public ConversationAuditPage auditConversations(String keyword, Long startMs, Long endMs,
                                                    Integer page, Integer size) {
        long tenantId = CollabContext.tenantId();
        int safePage = CollabContext.page(page);
        int safeSize = CollabContext.size(size);
        OffsetDateTime startAt = startMs == null ? null
                : OffsetDateTime.ofInstant(Instant.ofEpochMilli(startMs), ZoneOffset.UTC);
        OffsetDateTime endAt = endMs == null ? null
                : OffsetDateTime.ofInstant(Instant.ofEpochMilli(endMs), ZoneOffset.UTC);
        String safeKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<ConversationAuditView> records = adminMapper
                .listConversationsForAudit(tenantId, safeKeyword, startAt, endAt,
                        (safePage - 1) * safeSize, safeSize).stream()
                .map(row -> new ConversationAuditView(Rows.str(row, "convId"), Rows.str(row, "title"),
                        Rows.str(row, "convType"), Rows.intVal(row, "memberCount"),
                        Rows.lngVal(row, "msgCount"), Rows.str(row, "status"), Rows.instant(row, "createdAt")))
                .toList();
        long total = adminMapper.countConversationsForAudit(tenantId, safeKeyword, startAt, endAt);
        audit.publish("COLLAB_IM_CONVERSATION_AUDITED", "READ", "collab_conversation", null, "MEDIUM");
        return new ConversationAuditPage(records, total, safePage, safeSize);
    }

    @Transactional
    public CalendarView createPublicCalendar(CalendarCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "日历名称不能为空");
        }
        String calType = request.calType() == null ? "PUBLIC" : request.calType();
        if (!"PUBLIC".equals(calType) && !"HOLIDAY".equals(calType)) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "公共日历类型必须为 PUBLIC/HOLIDAY");
        }
        CalendarView view = calendarService.createCalendar(new CalendarCreateRequest(request.name(), calType,
                request.color(), request.visibleScope() == null ? "ALL" : request.visibleScope(), null));
        audit.publish("COLLAB_PUBLIC_CALENDAR_CREATED", "CREATE", "collab_calendar", view.calendarId(), "LOW");
        return view;
    }

    @Transactional
    public RoomView createMeetingRoom(RoomCreateRequest request) {
        return meetingService.createRoom(request);
    }

    @Transactional
    public PeriodView upsertPeriod(PeriodUpsertRequest request) {
        return okrService.upsertPeriod(request);
    }

    @Transactional
    public TemplateView createApprovalTemplate(TemplateCreateRequest request) {
        return approvalService.createTemplate(request);
    }
}
