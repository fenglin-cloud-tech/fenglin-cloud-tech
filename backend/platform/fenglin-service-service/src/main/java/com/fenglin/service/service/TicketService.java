package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.mapper.CommonsMapper;
import com.fenglin.service.mapper.ConfigMapper;
import com.fenglin.service.mapper.TicketMapper;
import com.fenglin.service.support.Rows;
import com.fenglin.service.support.ServiceAudit;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceCrypto;
import com.fenglin.service.support.ServiceIdempotency;
import com.fenglin.service.support.ServiceMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工单主链：建单 → 分派/受理 → 处理 → 解决 → 关闭/重开，SLA 计时与升级。
 */
@Service
@Transactional
public class TicketService {

    private static final Set<String> TRANSITIONS = Set.of(
            "NEW>ASSIGNED", "NEW>CANCELLED",
            "ASSIGNED>IN_PROGRESS", "ASSIGNED>PENDING", "ASSIGNED>ESCALATED", "ASSIGNED>CANCELLED",
            "IN_PROGRESS>PENDING", "IN_PROGRESS>RESOLVED", "IN_PROGRESS>ESCALATED", "IN_PROGRESS>CANCELLED",
            "PENDING>IN_PROGRESS", "PENDING>RESOLVED", "PENDING>CANCELLED",
            "ESCALATED>IN_PROGRESS", "ESCALATED>RESOLVED",
            "RESOLVED>CLOSED", "RESOLVED>REOPENED",
            "REOPENED>ASSIGNED", "REOPENED>IN_PROGRESS",
            "CLOSED>REOPENED");

    private final TicketMapper ticketMapper;
    private final ConfigMapper configMapper;
    private final CommonsMapper commonsMapper;
    private final ServiceCrypto crypto;
    private final ServiceIdempotency idempotency;
    private final ServiceAudit audit;

    public TicketService(TicketMapper ticketMapper, ConfigMapper configMapper, CommonsMapper commonsMapper,
                         ServiceCrypto crypto, ServiceIdempotency idempotency, ServiceAudit audit) {
        this.ticketMapper = ticketMapper;
        this.configMapper = configMapper;
        this.commonsMapper = commonsMapper;
        this.crypto = crypto;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, String idemKey) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        String title = ServiceContext.requireText(ServiceContext.string(body.get("title")));
        String ticketType = body.get("ticket_type") == null ? "INCIDENT"
                : ServiceContext.string(body.get("ticket_type")).toUpperCase();
        String priority = body.get("priority") == null ? "P3" : ServiceContext.string(body.get("priority")).toUpperCase();
        String source = body.get("source") == null ? "SELF" : ServiceContext.string(body.get("source")).toUpperCase();
        Long catalogItemId = body.get("catalog_item_id") == null ? null
                : ServiceContext.requireLong(body.get("catalog_item_id"));
        Long requesterId = body.get("requester_id") == null ? userId
                : ServiceContext.requireLong(body.get("requester_id"));
        Long catalogId = null;
        Long slaPolicyId = body.get("sla_policy_id") == null ? null
                : ServiceContext.requireLong(body.get("sla_policy_id"));
        if (catalogItemId != null) {
            Map<String, Object> item = configMapper.findItem(tenantId, catalogItemId);
            if (item == null) {
                throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
            }
            if (!"ACTIVE".equals(Rows.str(item, "status"))) {
                throw new BizException(ServiceErrorCode.CATALOG_ITEM_DISABLED);
            }
            catalogId = Rows.lng(item, "catalog_id");
            if (slaPolicyId == null) {
                slaPolicyId = Rows.lng(item, "sla_policy_id");
            }
            if (ticketType.equals("INCIDENT") && item.get("ticket_type") != null) {
                ticketType = Rows.str(item, "ticket_type");
            }
        }
        Map<String, Object> policy = slaPolicyId == null
                ? ticketMapper.findApplicablePolicy(tenantId, priority, ticketType)
                : ticketMapper.findPolicy(tenantId, slaPolicyId);
        if (policy == null) {
            throw new BizException(ServiceErrorCode.TICKET_SLA_MISSING);
        }
        long policyId = Rows.lngVal(policy, "id");
        int responseMinutes = Rows.intVal(policy, "response_minutes");
        int resolveMinutes = Rows.intVal(policy, "resolve_minutes");
        Timestamp now = commonsMapper.now();
        Timestamp responseDue = Timestamp.from(now.toInstant().plus(responseMinutes, ChronoUnit.MINUTES));
        Timestamp resolveDue = Timestamp.from(now.toInstant().plus(resolveMinutes, ChronoUnit.MINUTES));

        String contact = ServiceContext.string(body.get("contact"));
        long id = IdWorker.getId();
        String ticketNo = "SVC" + String.format("%010d", commonsMapper.nextTicketNo());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("tenantId", tenantId);
        row.put("ticketNo", ticketNo);
        row.put("title", title);
        row.put("description", ServiceContext.string(body.get("description")));
        row.put("ticketType", ticketType);
        row.put("catalogId", catalogId);
        row.put("catalogItemId", catalogItemId);
        row.put("priority", priority);
        row.put("impact", ServiceContext.string(body.get("impact")));
        row.put("urgency", ServiceContext.string(body.get("urgency")));
        row.put("source", source);
        row.put("requesterId", requesterId);
        row.put("requesterDeptId", body.get("requester_dept_id") == null ? null
                : ServiceContext.requireLong(body.get("requester_dept_id")));
        row.put("contactEnc", crypto.encrypt(tenantId, "svc_ticket", "contact", contact));
        row.put("contactHash", crypto.hash(tenantId, contact));
        row.put("assigneeId", body.get("assignee_id") == null ? null : ServiceContext.requireLong(body.get("assignee_id")));
        row.put("assigneeGroupId", body.get("assignee_group_id") == null ? null
                : ServiceContext.requireLong(body.get("assignee_group_id")));
        row.put("status", row.get("assigneeId") == null ? "NEW" : "ASSIGNED");
        row.put("slaPolicyId", policyId);
        row.put("responseDueAt", responseDue);
        row.put("resolveDueAt", resolveDue);
        row.put("tags", ServiceMaps.json(body.get("tags")));
        row.put("extJson", ServiceMaps.json(body.get("ext")));
        row.put("aiCategory", ServiceContext.string(body.get("ai_category")));
        row.put("aiConfidence", body.get("ai_confidence"));
        row.put("userId", userId);
        ticketMapper.insert(row);
        ticketMapper.insertSlaTimer(IdWorker.getId(), tenantId, id, policyId, responseDue, resolveDue, userId);
        ticketMapper.insertFlowLog(IdWorker.getId(), tenantId, id, "CREATE", null, Rows.str(row, "status"),
                userId, null, Rows.lng(row, "assigneeId"), null, now);
        idempotency.remember(idemKey, "/api/service/v1/tickets", id);
        audit.high("ticket.create", "ticket", id);
        return detail(id, true);
    }

    public PageResult<Map<String, Object>> list(String status, String ticketType, String priority, Long assigneeId,
                                                Long requesterId, Long catalogItemId, String keyword,
                                                Integer page, Integer size) {
        long tenantId = ServiceContext.tenantId();
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = ticketMapper.list(tenantId, blank(status), blank(ticketType),
                blank(priority), assigneeId, null, requesterId, catalogItemId, blank(keyword), limit, offset);
        long total = ticketMapper.count(tenantId, blank(status), blank(ticketType), blank(priority),
                assigneeId, null, requesterId, catalogItemId, blank(keyword));
        return PageResult.of(rows, total, ServiceContext.page(page), limit);
    }

    public Map<String, Object> detail(long id, boolean includeComments) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> ticket = ticketMapper.findById(tenantId, id);
        if (ticket == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        Map<String, Object> result = ServiceMaps.parse(ticket, "tags", "ext_json");
        String contactEnc = ticketMapper.findContactEnc(tenantId, id);
        result.put("contact_masked", crypto.mask(safeDecrypt(tenantId, contactEnc)));
        if (includeComments) {
            result.put("comments", ServiceMaps.parseAll(ticketMapper.listComments(tenantId, id), "attachment_ids"));
            result.put("flow_logs", ticketMapper.listFlowLogs(tenantId, id));
            result.put("relations", ticketMapper.listRelations(tenantId, id));
        }
        return result;
    }

    public Map<String, Object> detailWithPii(long id, String permissions) {
        Map<String, Object> result = detail(id, true);
        if (permissions != null && (permissions.contains("*") || permissions.contains("service:ticket:pii:view"))) {
            result.put("contact", safeDecrypt(ServiceContext.tenantId(), ticketMapper.findContactEnc(ServiceContext.tenantId(), id)));
            audit.high("ticket.pii.view", "ticket", id);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> createWithIdempotency(Map<String, Object> body, String idemKey, String apiPath) {
        Long existing = idempotency.find(idemKey, apiPath);
        if (existing != null) {
            return detail(existing, true);
        }
        return create(body, idemKey);
    }

    @Transactional
    public Map<String, Object> assign(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        Map<String, Object> ticket = requiredTicket(tenantId, id);
        Long assigneeId = ServiceContext.requireLong(body.get("assignee_id"));
        Long groupId = body.get("assignee_group_id") == null ? null
                : ServiceContext.requireLong(body.get("assignee_group_id"));
        Long fromAssignee = Rows.lng(ticket, "assignee_id");
        ticketMapper.updateStatus(tenantId, id, "ASSIGNED", assigneeId, groupId,
                null, null, null, null, userId);
        ticketMapper.insertFlowLog(IdWorker.getId(), tenantId, id, "ASSIGN", Rows.str(ticket, "status"),
                "ASSIGNED", userId, fromAssignee, assigneeId, ServiceContext.string(body.get("reason")),
                commonsMapper.now());
        audit.low("ticket.assign", "ticket", id);
        return detail(id, false);
    }

    @Transactional
    public Map<String, Object> transition(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        Map<String, Object> ticket = requiredTicket(tenantId, id);
        String from = Rows.str(ticket, "status");
        String to = ServiceContext.requireText(ServiceContext.string(body.get("to_status"))).toUpperCase();
        if (!TRANSITIONS.contains(from + ">" + to)) {
            throw new BizException(ServiceErrorCode.TICKET_TRANSITION_INVALID);
        }
        Timestamp now = commonsMapper.now();
        Timestamp firstResponse = ("ASSIGNED".equals(from) || "NEW".equals(from)) ? now : null;
        Timestamp resolvedAt = "RESOLVED".equals(to) ? now : null;
        Timestamp closedAt = "CLOSED".equals(to) ? now : null;
        if ("REOPENED".equals(to)) {
            ticketMapper.increaseReopen(tenantId, id, userId);
        }
        ticketMapper.updateStatus(tenantId, id, to, null, null, firstResponse, resolvedAt, closedAt, null, userId);
        ticketMapper.insertFlowLog(IdWorker.getId(), tenantId, id, to, from, to, userId,
                Rows.lng(ticket, "assignee_id"), null, ServiceContext.string(body.get("reason")), now);
        refreshSlaOnTransition(tenantId, id, to, now);
        audit.low("ticket." + to.toLowerCase(), "ticket", id);
        return detail(id, false);
    }

    public Map<String, Object> comment(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        requiredTicket(tenantId, id);
        String content = ServiceContext.requireText(ServiceContext.string(body.get("content")));
        boolean internal = Boolean.TRUE.equals(body.get("internal"));
        ticketMapper.insertComment(IdWorker.getId(), tenantId, id,
                body.get("comment_type") == null ? "REPLY" : ServiceContext.string(body.get("comment_type")),
                content, userId, internal, ServiceMaps.json(body.get("attachment_ids")));
        return ServiceMaps.of("ticket_id", id, "internal", internal);
    }

    public List<Map<String, Object>> comments(long id) {
        long tenantId = ServiceContext.tenantId();
        requiredTicket(tenantId, id);
        return ServiceMaps.parseAll(ticketMapper.listComments(tenantId, id), "attachment_ids");
    }

    public List<Map<String, Object>> flowLogs(long id) {
        long tenantId = ServiceContext.tenantId();
        requiredTicket(tenantId, id);
        return ticketMapper.listFlowLogs(tenantId, id);
    }

    public Map<String, Object> addRelation(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        requiredTicket(tenantId, id);
        String relType = ServiceContext.requireText(ServiceContext.string(body.get("rel_type"))).toUpperCase();
        long relId = ServiceContext.requireLong(body.get("rel_id"));
        ticketMapper.insertRelation(IdWorker.getId(), tenantId, id, relType, relId, ServiceContext.userId());
        return ServiceMaps.of("ticket_id", id, "rel_type", relType, "rel_id", relId);
    }

    public Map<String, Object> sla(long id) {
        long tenantId = ServiceContext.tenantId();
        requiredTicket(tenantId, id);
        Map<String, Object> timer = ticketMapper.findSlaTimer(tenantId, id);
        if (timer == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return timer;
    }

    public PageResult<Map<String, Object>> breaches(String status, Integer page, Integer size) {
        long tenantId = ServiceContext.tenantId();
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        List<Map<String, Object>> rows = ticketMapper.listBreaches(tenantId, blank(status), limit, offset);
        return PageResult.of(rows, rows.size(), ServiceContext.page(page), limit);
    }

    /** SLA 巡检：对运行中的计时判断响应/解决超时并写入违约记录。 */
    @Transactional
    public Map<String, Object> tickSla() {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        List<Map<String, Object>> tickets = ticketMapper.listRunningSla(tenantId, 200);
        int breached = 0;
        Timestamp now = commonsMapper.now();
        for (Map<String, Object> row : tickets) {
            long ticketId = Rows.lngVal(row, "id");
            Timestamp responseDue = (Timestamp) row.get("sla_response_due_at");
            Timestamp resolveDue = (Timestamp) row.get("sla_resolve_due_at");
            boolean responded = row.get("first_response_at") != null;
            boolean resolved = row.get("resolved_at") != null;
            if (!responded && responseDue != null && responseDue.before(now)) {
                ticketMapper.insertBreach(IdWorker.getId(), tenantId, ticketId,
                        Rows.lngVal(row, "sla_policy_id"), "RESPONSE", responseDue,
                        (now.getTime() - responseDue.getTime()) / 1000, 1, null, userId);
                ticketMapper.updateSlaTimer(tenantId, ticketId, "BREACHED",
                        resolved ? "MET" : "BREACHED", 0, 0, userId);
                breached++;
            } else if (!resolved && resolveDue != null && resolveDue.before(now)) {
                ticketMapper.insertBreach(IdWorker.getId(), tenantId, ticketId,
                        Rows.lngVal(row, "sla_policy_id"), "RESOLVE", resolveDue,
                        (now.getTime() - resolveDue.getTime()) / 1000, 1, null, userId);
                ticketMapper.updateSlaTimer(tenantId, ticketId, responded ? "MET" : "BREACHED",
                        "BREACHED", 0, 0, userId);
                breached++;
            }
        }
        return ServiceMaps.of("scanned", tickets.size(), "breached", breached);
    }

    public PageResult<Map<String, Object>> workspace(String view, Long assigneeId, Long requesterId,
                                                     Integer page, Integer size) {
        if ("my".equalsIgnoreCase(view)) {
            return list(null, null, null, null, requesterId == null ? ServiceContext.userId() : requesterId,
                    null, null, page, size);
        }
        if ("desk".equalsIgnoreCase(view)) {
            return list(null, null, null, assigneeId == null ? ServiceContext.userId() : assigneeId,
                    null, null, null, page, size);
        }
        throw new BizException(ServiceErrorCode.PARAM_INVALID);
    }

    public Map<String, Object> ticketReport() {
        long tenantId = ServiceContext.tenantId();
        return ServiceMaps.of(
                "total", ticketMapper.count(tenantId, null, null, null, null, null, null, null, null),
                "by_status", ticketMapper.countByStatus(tenantId),
                "by_priority", ticketMapper.countByPriority(tenantId),
                "by_type", ticketMapper.countByType(tenantId));
    }

    public Map<String, Object> slaReport() {
        return ticketMapper.slaReport(ServiceContext.tenantId());
    }

    public List<Map<String, Object>> workload() {
        return ticketMapper.workload(ServiceContext.tenantId());
    }

    @Transactional
    public Map<String, Object> importBatch(List<Map<String, Object>> items, String idemKey) {
        int created = 0;
        for (Map<String, Object> item : items) {
            create(item, null);
            created++;
        }
        return ServiceMaps.of("created", created, "request_key", idemKey);
    }

    @Transactional
    public Map<String, Object> emailInbound(Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.putIfAbsent("title", ServiceContext.string(body.get("subject")));
        payload.putIfAbsent("description", ServiceContext.string(body.get("body")));
        payload.put("source", "EMAIL");
        return create(payload, null);
    }

    private void refreshSlaOnTransition(long tenantId, long id, String to, Timestamp now) {
        Map<String, Object> timer = ticketMapper.findSlaTimer(tenantId, id);
        if (timer == null) {
            return;
        }
        String responseState = Rows.str(timer, "response_state");
        String resolveState = Rows.str(timer, "resolve_state");
        long policyId = Rows.lngVal(timer, "sla_policy_id");
        if ("RESOLVED".equals(to) || "CLOSED".equals(to)) {
            Timestamp resolveDue = (Timestamp) timer.get("resolve_due_at");
            boolean met = resolveDue == null || !now.after(resolveDue);
            ticketMapper.updateSlaTimer(tenantId, id,
                    "MET".equals(responseState) ? "MET" : (met ? "MET" : "BREACHED"),
                    met ? "MET" : "BREACHED", 0, 0, ServiceContext.userId());
        } else if ("IN_PROGRESS".equals(to) && "RUNNING".equals(responseState)) {
            ticketMapper.updateSlaTimer(tenantId, id, "MET", resolveState, policyId, 0, ServiceContext.userId());
        }
    }

    private Map<String, Object> requiredTicket(long tenantId, long id) {
        Map<String, Object> ticket = ticketMapper.findById(tenantId, id);
        if (ticket == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return ticket;
    }

    private String safeDecrypt(long tenantId, String encoded) {
        try {
            return crypto.decrypt(tenantId, "svc_ticket", "contact", encoded);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
