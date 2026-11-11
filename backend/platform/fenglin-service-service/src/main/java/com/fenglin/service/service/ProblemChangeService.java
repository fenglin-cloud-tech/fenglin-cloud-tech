package com.fenglin.service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.service.ServiceErrorCode;
import com.fenglin.service.client.WorkflowClient;
import com.fenglin.service.mapper.CommonsMapper;
import com.fenglin.service.mapper.ConfigMapper;
import com.fenglin.service.mapper.TicketMapper;
import com.fenglin.service.support.Rows;
import com.fenglin.service.support.ServiceAudit;
import com.fenglin.service.support.ServiceContext;
import com.fenglin.service.support.ServiceMaps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 问题管理（多工单聚合归因）与变更请求（审批流）。
 */
@Service
@Transactional
public class ProblemChangeService {

    private final ConfigMapper configMapper;
    private final TicketMapper ticketMapper;
    private final CommonsMapper commonsMapper;
    private final WorkflowClient workflowClient;
    private final ServiceAudit audit;

    public ProblemChangeService(ConfigMapper configMapper, TicketMapper ticketMapper, CommonsMapper commonsMapper,
                                WorkflowClient workflowClient, ServiceAudit audit) {
        this.configMapper = configMapper;
        this.ticketMapper = ticketMapper;
        this.commonsMapper = commonsMapper;
        this.workflowClient = workflowClient;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> createProblem(Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        long id = IdWorker.getId();
        String no = "PRB" + String.format("%010d", commonsMapper.nextProblemNo());
        configMapper.insertProblem(id, tenantId, no,
                ServiceContext.requireText(ServiceContext.string(body.get("title"))),
                ServiceContext.string(body.get("description")),
                ServiceContext.string(body.get("root_cause")),
                ServiceContext.string(body.get("workaround")),
                Boolean.TRUE.equals(body.get("known_error")),
                body.get("status") == null ? "OPEN" : ServiceContext.string(body.get("status")),
                body.get("owner_id") == null ? userId : ServiceContext.requireLong(body.get("owner_id")), userId);
        Object ticketIds = body.get("ticket_ids");
        if (ticketIds instanceof java.util.List<?> list) {
            for (Object ticketId : list) {
                ticketMapper.insertRelation(IdWorker.getId(), tenantId, ServiceContext.requireLong(ticketId),
                        "PROBLEM", id, userId);
            }
        }
        audit.low("problem.create", "problem", id);
        return problem(id);
    }

    public Map<String, Object> problem(long id) {
        Map<String, Object> problem = configMapper.findProblem(ServiceContext.tenantId(), id);
        if (problem == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return problem;
    }

    public PageResult<Map<String, Object>> problems(String status, String keyword, Integer page, Integer size) {
        long tenantId = ServiceContext.tenantId();
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        String normalized = ServiceContext.text(status);
        String kw = ServiceContext.text(keyword);
        List<Map<String, Object>> rows = configMapper.listProblems(tenantId,
                normalized == null ? null : normalized.toUpperCase(), kw, limit, offset);
        long total = configMapper.countProblems(tenantId,
                normalized == null ? null : normalized.toUpperCase(), kw);
        return PageResult.of(rows, total, ServiceContext.page(page), limit);
    }

    @Transactional
    public Map<String, Object> updateProblem(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        Map<String, Object> problem = configMapper.findProblem(tenantId, id);
        if (problem == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        String status = body.get("status") == null ? Rows.str(problem, "status")
                : ServiceContext.string(body.get("status")).toUpperCase();
        configMapper.updateProblem(tenantId, id, ServiceContext.string(body.get("root_cause")),
                ServiceContext.string(body.get("workaround")),
                body.get("known_error") == null ? Rows.bool(problem, "known_error")
                        : Boolean.TRUE.equals(body.get("known_error")),
                status, ServiceContext.userId());
        audit.low("problem.update", "problem", id);
        return problem(id);
    }

    @Transactional
    public Map<String, Object> createChange(Map<String, Object> body, String permissions, String requestId) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        long id = IdWorker.getId();
        String no = "CHG" + String.format("%010d", commonsMapper.nextChangeNo());
        String flowKey = ServiceContext.string(body.get("approval_flow_key")) == null
                ? "svcChangeApproval" : ServiceContext.string(body.get("approval_flow_key"));
        configMapper.insertChange(id, tenantId, no,
                ServiceContext.requireText(ServiceContext.string(body.get("title"))),
                body.get("change_type") == null ? "NORMAL" : ServiceContext.string(body.get("change_type")),
                ServiceContext.string(body.get("plan")), ServiceContext.string(body.get("rollback_plan")),
                body.get("risk_level") == null ? "MEDIUM" : ServiceContext.string(body.get("risk_level")),
                timestamp(body.get("planned_start")), timestamp(body.get("planned_end")),
                flowKey, "APPROVING", body.get("owner_id") == null ? userId
                        : ServiceContext.requireLong(body.get("owner_id")), userId);
        String instance = workflowClient.startApproval(permissions, requestId, flowKey,
                String.valueOf(id), "SERVICE_CHANGE", "变更审批：" + no, Map.of("change_no", no));
        audit.high("change.create", "change", id);
        Map<String, Object> change = configMapper.findChange(tenantId, id);
        change.put("approval_instance_no", instance);
        return change;
    }

    public Map<String, Object> change(long id) {
        Map<String, Object> change = configMapper.findChange(ServiceContext.tenantId(), id);
        if (change == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        return change;
    }

    public PageResult<Map<String, Object>> changes(String status, String keyword, Integer page, Integer size) {
        long tenantId = ServiceContext.tenantId();
        int limit = ServiceContext.size(size);
        int offset = ServiceContext.offset(page, size);
        String normalized = ServiceContext.text(status);
        String kw = ServiceContext.text(keyword);
        List<Map<String, Object>> rows = configMapper.listChanges(tenantId,
                normalized == null ? null : normalized.toUpperCase(), kw, limit, offset);
        long total = configMapper.countChanges(tenantId,
                normalized == null ? null : normalized.toUpperCase(), kw);
        return PageResult.of(rows, total, ServiceContext.page(page), limit);
    }

    @Transactional
    public Map<String, Object> approveChange(long id, Map<String, Object> body) {
        long tenantId = ServiceContext.tenantId();
        long userId = ServiceContext.userId();
        Map<String, Object> change = configMapper.findChange(tenantId, id);
        if (change == null) {
            throw new BizException(ServiceErrorCode.RESOURCE_NOT_FOUND);
        }
        String current = Rows.str(change, "status");
        if (!"APPROVING".equals(current) && !"DRAFT".equals(current)) {
            throw new BizException(ServiceErrorCode.CHANGE_STATE_INVALID);
        }
        boolean approved = !Boolean.FALSE.equals(body.get("approved"));
        String status = approved ? "APPROVED" : "DRAFT";
        configMapper.updateChangeStatus(tenantId, id, status, userId);
        audit.high("change.approve", "change", id);
        return change(id);
    }

    private Timestamp timestamp(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Timestamp.from(java.time.Instant.parse(String.valueOf(value)));
        } catch (RuntimeException ex) {
            return Timestamp.valueOf(String.valueOf(value).replace("T", " ").substring(0, 19));
        }
    }
}
