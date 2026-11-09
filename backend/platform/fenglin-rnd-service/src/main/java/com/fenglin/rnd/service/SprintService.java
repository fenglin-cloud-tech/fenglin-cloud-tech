package com.fenglin.rnd.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.rnd.RndErrorCode;
import com.fenglin.rnd.mapper.CommonsMapper;
import com.fenglin.rnd.mapper.SprintMapper;
import com.fenglin.rnd.mapper.TaskMapper;
import com.fenglin.rnd.support.RndAudit;
import com.fenglin.rnd.support.RndContext;
import com.fenglin.rnd.support.Rows;
import com.fenglin.rnd.web.dto.RndDtos.BurndownPoint;
import com.fenglin.rnd.web.dto.RndDtos.BurndownView;
import com.fenglin.rnd.web.dto.RndDtos.SprintCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.SprintMemberRequest;
import com.fenglin.rnd.web.dto.RndDtos.SprintView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * E1 迭代：创建、启停、燃尽图（按任务完成时间推算剩余工时）。
 */
@Service
@Transactional(readOnly = true)
public class SprintService {

    private static final int MAX_BURNDOWN_DAYS = 60;

    private final SprintMapper sprintMapper;
    private final TaskMapper taskMapper;
    private final CommonsMapper commonsMapper;
    private final ProductService productService;
    private final RndAudit audit;

    public SprintService(SprintMapper sprintMapper, TaskMapper taskMapper, CommonsMapper commonsMapper,
                         ProductService productService, RndAudit audit) {
        this.sprintMapper = sprintMapper;
        this.taskMapper = taskMapper;
        this.commonsMapper = commonsMapper;
        this.productService = productService;
        this.audit = audit;
    }

    @Transactional
    public SprintView create(SprintCreateRequest request) {
        long tenantId = RndContext.tenantId();
        long userId = RndContext.userId();
        if (request.productId() == null) {
            throw new BizException(RndErrorCode.PARAM_INVALID);
        }
        productService.requireProduct(tenantId, request.productId());
        if (request.startDate() == null || request.endDate() == null
                || request.endDate().isBefore(request.startDate())) {
            throw new BizException(RndErrorCode.SPRINT_DATE_INVALID);
        }
        long id = IdWorker.getId();
        sprintMapper.insertSprint(id, tenantId, request.productId(),
                "SPR-" + commonsMapper.nextSprintNo(), RndContext.requireText(request.name(), "name"),
                RndContext.text(request.goal()), request.startDate(), request.endDate(),
                request.capacityHours(), userId);
        if (request.members() != null) {
            BigDecimal assigned = BigDecimal.ZERO;
            for (SprintMemberRequest member : request.members()) {
                if (member.userId() == null) {
                    continue;
                }
                BigDecimal hours = member.availableHours() == null ? BigDecimal.ZERO : member.availableHours();
                assigned = assigned.add(hours);
                sprintMapper.insertMember(IdWorker.getId(), tenantId, id, member.userId(), hours, userId);
            }
            if (request.capacityHours() != null && assigned.compareTo(request.capacityHours()) > 0) {
                throw new BizException(RndErrorCode.SPRINT_CAPACITY_EXCEEDED);
            }
        }
        audit.publish("SPRINT_CREATED", "create", "devops_sprint", id, "low");
        return get(id);
    }

    public SprintView get(long id) {
        Map<String, Object> row = sprintMapper.findSprint(RndContext.tenantId(), id);
        if (row == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return toView(row);
    }

    public List<SprintView> list(long productId, String status) {
        return sprintMapper.listSprints(RndContext.tenantId(), productId, RndContext.text(status))
                .stream().map(SprintService::toView).toList();
    }

    @Transactional
    public SprintView start(long id) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> sprint = requireSprint(tenantId, id);
        if (!"planning".equals(Rows.str(sprint, "status"))) {
            throw new BizException(RndErrorCode.SPRINT_STATE_INVALID);
        }
        sprintMapper.updateStatus(tenantId, id, "active", "planning", RndContext.userId());
        audit.publish("SPRINT_STARTED", "start", "devops_sprint", id, "low");
        return get(id);
    }

    @Transactional
    public SprintView complete(long id) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> sprint = requireSprint(tenantId, id);
        if (!"active".equals(Rows.str(sprint, "status"))) {
            throw new BizException(RndErrorCode.SPRINT_STATE_INVALID);
        }
        sprintMapper.updateStatus(tenantId, id, "completed", "active", RndContext.userId());
        audit.publish("SPRINT_COMPLETED", "complete", "devops_sprint", id, "low");
        return get(id);
    }

    public BurndownView burndown(long id) {
        long tenantId = RndContext.tenantId();
        Map<String, Object> sprint = requireSprint(tenantId, id);
        Map<String, Object> summary = sprintMapper.burndownSummary(tenantId, id);
        BigDecimal totalHours = Rows.dec(summary, "totalHours");
        int totalTasks = Rows.intVal(summary, "totalTasks");
        long doneTasks = taskMapper.countDoneBySprint(tenantId, id);
        List<Map<String, Object>> tasks = sprintMapper.listSprintTasks(tenantId, id);

        LocalDate start = Rows.date(sprint, "startDate");
        LocalDate end = Rows.date(sprint, "endDate");
        long days = ChronoUnit.DAYS.between(start, end);
        if (days > MAX_BURNDOWN_DAYS) {
            end = start.plusDays(MAX_BURNDOWN_DAYS);
            days = MAX_BURNDOWN_DAYS;
        }
        if (days <= 0) {
            days = 1;
        }
        BigDecimal total = totalHours == null ? BigDecimal.ZERO : totalHours;
        List<BurndownPoint> points = new ArrayList<>();
        for (int i = 0; i <= days; i++) {
            LocalDate day = start.plusDays(i);
            BigDecimal completed = BigDecimal.ZERO;
            for (Map<String, Object> task : tasks) {
                String completedAt = Rows.str(task, "completedAt");
                if (completedAt == null || completedAt.isBlank()) {
                    continue;
                }
                LocalDate doneDate = LocalDate.parse(completedAt.substring(0, 10));
                if (!doneDate.isAfter(day)) {
                    BigDecimal estimate = Rows.dec(task, "estimateHours");
                    completed = completed.add(estimate == null ? BigDecimal.ZERO : estimate);
                }
            }
            BigDecimal remaining = total.subtract(completed).max(BigDecimal.ZERO);
            BigDecimal ideal = total.multiply(BigDecimal.valueOf(days - i))
                    .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
            points.add(new BurndownPoint(day.toString(), remaining.setScale(2, RoundingMode.HALF_UP), ideal));
        }
        return new BurndownView(id, Rows.str(sprint, "name"), totalTasks, (int) doneTasks, points);
    }

    private Map<String, Object> requireSprint(long tenantId, long id) {
        Map<String, Object> sprint = sprintMapper.findSprint(tenantId, id);
        if (sprint == null) {
            throw new BizException(RndErrorCode.RESOURCE_NOT_FOUND);
        }
        return sprint;
    }

    public static SprintView toView(Map<String, Object> row) {
        return new SprintView(Rows.lng(row, "id"), Rows.lng(row, "productId"), Rows.str(row, "sprintNo"),
                Rows.str(row, "name"), Rows.str(row, "goal"), Rows.date(row, "startDate"),
                Rows.date(row, "endDate"), Rows.dec(row, "capacityHours"), Rows.str(row, "status"),
                Rows.intg(row, "requirementCount"), Rows.intg(row, "taskCount"),
                Rows.intg(row, "doneTaskCount"), Rows.str(row, "createdAt"));
    }
}
