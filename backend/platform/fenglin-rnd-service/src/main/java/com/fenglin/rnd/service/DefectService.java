package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.mapper.CommonsMapper;
import com.fenglin.rnd.mapper.DefectMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.DefectCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.DefectView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * E1 缺陷：状态流转、重开次数限制、生产缺陷统计（逃逸率）。
 */
@Service
@Transactional(readOnly = true)
public class DefectService {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "new", Set.of("confirmed", "rejected"),
            "confirmed", Set.of("fixing", "rejected"),
            "fixing", Set.of("fixed"),
            "fixed", Set.of("verifying"),
            "verifying", Set.of("closed", "reopened"),
            "reopened", Set.of("fixing", "confirmed"),
            "closed", Set.of("reopened"));

    private final DefectMapper defectMapper;
    private final CommonsMapper commonsMapper;
    private final ProductService productService;
    private final RndAudit audit;

    public DefectService(DefectMapper defectMapper, CommonsMapper commonsMapper, ProductService productService,
                         RndAudit audit) {
        this.defectMapper = defectMapper;
        this.commonsMapper = commonsMapper;
        this.productService = productService;
        this.audit = audit;
    }

    @Transactional
    public DefectView create(DefectCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        productService.requireProduct(tenantId, request.productId());
        long id = IdWorker.getId();
        defectMapper.insertDefect(id, tenantId, request.productId(),
                "BUG-" + commonsMapper.nextDefectNo(), RndContext.requireText(request.title(), "title"),
                request.severity() == null ? "normal" : request.severity(),
                request.priority() == null ? "P2" : request.priority(),
                RndContext.text(request.env()),
                request.foundStage() == null ? "test" : request.foundStage(),
                request.sprintId(), request.requirementId(), request.testCaseId(), request.assigneeId(), userId,
                RndContext.text(request.reproduceSteps()), userId);
        audit.publish("DEFECT_CREATED", "create", "devops_defect", id, "medium");
        return get(id);
    }

    public DefectView get(long id) {
        Map<String, Object> row = defectMapper.findDefect(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return toView(row);
    }

    public List<DefectView> list(long productId, String status, String severity, Integer page, Integer size) {
        return defectMapper.listDefects(RndContext.tenantId(), productId, RndContext.text(status),
                        RndContext.text(severity), RndContext.size(size), RndContext.offset(page, size))
                .stream().map(DefectService::toView).toList();
    }

    @Transactional
    public DefectView transition(long id, String status) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> defect = requireDefect(tenantId, id);
        String target = RndContext.requireText(status, "status");
        Set<String> allowed = TRANSITIONS.getOrDefault(Rows.str(defect, "status"), Set.of());
        if (!allowed.contains(target)) {
            throw new BizException(RndErrorCode.DEFECT_STATE_INVALID);
        }
        if ("reopened".equals(target) && Rows.intVal(defect, "reopenCount") >= 3) {
            throw new BizException(RndErrorCode.DEFECT_REOPEN_LIMIT);
        }
        defectMapper.updateStatus(tenantId, id, target, RndContext.userId());
        audit.publish("DEFECT_TRANSITIONED", "transition", "devops_defect", id, "low");
        return get(id);
    }

    private Map<String, Object> requireDefect(long tenantId, long id) {
        Map<String, Object> defect = defectMapper.findDefect(tenantId, id);
        if (defect == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return defect;
    }

    public static DefectView toView(Map<String, Object> row) {
        return new DefectView(Rows.lng(row, "id"), Rows.lng(row, "productId"), Rows.str(row, "defectNo"),
                Rows.str(row, "title"), Rows.str(row, "severity"), Rows.str(row, "priority"),
                Rows.str(row, "status"), Rows.str(row, "env"), Rows.str(row, "foundStage"),
                Rows.lng(row, "sprintId"), Rows.lng(row, "requirementId"), Rows.lng(row, "assigneeId"),
                Rows.lng(row, "reporterId"), Rows.str(row, "reproduceSteps"), Rows.intg(row, "reopenCount"),
                Rows.str(row, "fixedAt"), Rows.str(row, "closedAt"), Rows.str(row, "createdAt"));
    }
}
