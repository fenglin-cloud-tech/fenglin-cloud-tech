package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.client.WorkflowClient;
import com.fenglin.rnd.mapper.CommonsMapper;
import com.fenglin.rnd.mapper.ProductMapper;
import com.fenglin.rnd.mapper.RequirementMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.RelationCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.RequirementCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.RequirementReviewResult;
import com.fenglin.rnd.web.dto.RndDtos.RequirementView;
import com.fenglin.rnd.web.dto.RndDtos.SprintAssignRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * E1 需求：创建、状态流转、评审（BPMN 审批）、关联与排期。
 */
@Service
@Transactional(readOnly = true)
public class RequirementService {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "TODO", Set.of("REVIEWING", "DOING"),
            "REVIEWING", Set.of("REVIEWED", "REJECTED"),
            "REJECTED", Set.of("REVIEWING"),
            "REVIEWED", Set.of("DOING", "CLOSED"),
            "DOING", Set.of("DONE", "CLOSED"),
            "DONE", Set.of("CLOSED"));

    private final RequirementMapper requirementMapper;
    private final ProductMapper productMapper;
    private final CommonsMapper commonsMapper;
    private final WorkflowClient workflowClient;
    private final RndAudit audit;

    public RequirementService(RequirementMapper requirementMapper, ProductMapper productMapper,
                              CommonsMapper commonsMapper, WorkflowClient workflowClient, RndAudit audit) {
        this.requirementMapper = requirementMapper;
        this.productMapper = productMapper;
        this.commonsMapper = commonsMapper;
        this.workflowClient = workflowClient;
        this.audit = audit;
    }

    @Transactional
    public RequirementView create(RequirementCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null || productMapper.countProduct(tenantId, request.productId()) == 0) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        Long todoId = commonsMapper.findTodoStatusId(tenantId, request.productId());
        if (todoId == null) {
            throw new BizException(RndErrorCode.REQUIREMENT_STATUS_INVALID);
        }
        long id = IdWorker.getId();
        String reqNo = "REQ-" + commonsMapper.nextRequirementNo();
        requirementMapper.insertRequirement(id, tenantId, request.productId(), reqNo,
                RndContext.requireText(request.title(), "title"),
                request.reqType() == null ? "user" : request.reqType(),
                request.priority() == null ? "P2" : request.priority(), todoId, "todo",
                request.sprintId(), request.parentId(), request.assigneeId(), userId,
                request.estimateHours(), RndContext.text(request.acceptanceCriteria()),
                RndContext.text(request.planStartAt()), RndContext.text(request.planEndAt()), userId);
        audit.publish("REQUIREMENT_CREATED", "create", "devops_requirement", id, "low");
        return get(id);
    }

    public RequirementView get(long id) {
        Map<String, Object> row = requirementMapper.findRequirement(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return toView(row);
    }

    public List<RequirementView> list(long productId, Long sprintId, String statusCode, Integer page, Integer size) {
        return requirementMapper.listRequirements(RndContext.tenantId(), productId, sprintId,
                        RndContext.text(statusCode), RndContext.size(size), RndContext.offset(page, size))
                .stream().map(RequirementService::toView).toList();
    }

    @Transactional
    public RequirementView transition(long id, String statusCode) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        Map<String, Object> current = requireRequirement(tenantId, id);
        String from = Rows.str(current, "statusCode");
        String target = RndContext.requireText(statusCode, "statusCode").toUpperCase();
        Set<String> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(target)) {
            throw new BizException(RndErrorCode.REQUIREMENT_TRANSITION_INVALID);
        }
        Map<String, Object> status = productMapper.findStatus(tenantId, Rows.lng(current, "productId"), target);
        if (status == null) {
            throw new BizException(RndErrorCode.REQUIREMENT_STATUS_INVALID);
        }
        requirementMapper.updateStatus(tenantId, id, Rows.lng(status, "id"), Rows.str(status, "category"), userId);
        audit.publish("REQUIREMENT_TRANSITIONED", "transition", "devops_requirement", id, "low");
        return get(id);
    }

    @Transactional
    public RequirementReviewResult submitReview(long id) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        Map<String, Object> current = requireRequirement(tenantId, id);
        if ("REVIEWING".equals(Rows.str(current, "statusCode"))) {
            throw new BizException(RndErrorCode.REQUIREMENT_ALREADY_REVIEWING);
        }
        Map<String, Object> reviewing = productMapper.findStatus(tenantId, Rows.lng(current, "productId"),
                "REVIEWING");
        if (reviewing == null) {
            throw new BizException(RndErrorCode.REQUIREMENT_STATUS_INVALID);
        }
        requirementMapper.updateStatus(tenantId, id, Rows.lng(reviewing, "id"), "todo", userId);
        String instance = workflowClient.startApproval(null, null, "rndRequirementReview",
                "requirement:" + id, "requirement", "需求评审：" + Rows.str(current, "title"),
                Map.of("requirementId", id, "reqNo", Rows.str(current, "reqNo")));
        audit.publish("REQUIREMENT_REVIEW_SUBMITTED", "submit", "devops_requirement", id, "medium");
        return new RequirementReviewResult(instance, "REVIEWING");
    }

    @Transactional
    public void addRelation(long srcId, RelationCreateRequest request) {
        long tenantId = RndContext.tenantId();
        requireRequirement(tenantId, srcId);
        if (request.dstRequirementId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        requireRequirement(tenantId, request.dstRequirementId());
        String type = request.relationType() == null ? "relate" : request.relationType();
        requirementMapper.insertRelation(IdWorker.getId(), tenantId, srcId, request.dstRequirementId(), type,
                RndContext.userId());
        audit.publish("REQUIREMENT_RELATED", "relate", "devops_requirement", srcId, "low");
    }

    @Transactional
    public RequirementView assignSprint(long id, SprintAssignRequest request) {
        long tenantId = RndContext.tenantId();
        requireRequirement(tenantId, id);
        requirementMapper.assignSprint(tenantId, id, request.sprintId(), RndContext.userId());
        audit.publish("REQUIREMENT_SPRINT_ASSIGNED", "assign", "devops_requirement", id, "low");
        return get(id);
    }

    private Map<String, Object> requireRequirement(long tenantId, long id) {
        Map<String, Object> row = requirementMapper.findRequirement(tenantId, id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    public static RequirementView toView(Map<String, Object> row) {
        return new RequirementView(Rows.lng(row, "id"), Rows.lng(row, "productId"), Rows.str(row, "reqNo"),
                Rows.str(row, "title"), Rows.str(row, "reqType"), Rows.str(row, "priority"),
                Rows.lng(row, "statusId"), Rows.str(row, "statusCode"), Rows.str(row, "statusName"),
                Rows.str(row, "statusCategory"), Rows.lng(row, "sprintId"), Rows.lng(row, "parentId"),
                Rows.lng(row, "assigneeId"), Rows.lng(row, "reporterId"), Rows.dec(row, "estimateHours"),
                Rows.str(row, "acceptanceCriteria"), Rows.str(row, "planStartAt"), Rows.str(row, "planEndAt"),
                Rows.str(row, "deliveredAt"), Rows.intg(row, "escaped"), Rows.str(row, "createdAt"));
    }
}
