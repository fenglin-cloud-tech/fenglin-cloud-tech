package com.fenglin.workflow.service;

import com.fenglin.common.tenant.TenantContext;
import com.fenglin.workflow.mapper.WorkflowInstanceMapper;
import com.fenglin.workflow.mapper.WorkflowTaskMapper;
import com.fenglin.workflow.web.dto.MonitorStatsResult;
import com.fenglin.workflow.web.dto.NodeStat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowMonitorService {

    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowTaskMapper taskMapper;

    public WorkflowMonitorService(WorkflowInstanceMapper instanceMapper, WorkflowTaskMapper taskMapper) {
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
    }

    @Transactional(readOnly = true)
    public MonitorStatsResult stats(Instant from, Instant to) {
        long tenantId = TenantContext.requireTenantId();
        long running = instanceMapper.countByStatus(tenantId, "RUNNING", from, to);
        long completed = instanceMapper.countByStatus(tenantId, "COMPLETED", from, to);
        long timeout = taskMapper.countOverdue(tenantId);
        double avgMinutes = instanceMapper.avgDurationMinutes(tenantId, from, to);
        List<NodeStat> byNode = taskMapper.nodeStats(tenantId, from, to).stream()
                .map(row -> new NodeStat(
                        (String) row.get("node_key"),
                        ((Number) row.getOrDefault("avg_minutes", 0)).doubleValue(),
                        ((Number) row.getOrDefault("cnt", 0)).longValue()))
                .toList();
        return new MonitorStatsResult(running, completed, timeout, round(avgMinutes), byNode);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
