package com.fenglin.workflow.web.dto;

import java.util.List;

public record MonitorStatsResult(
        long running,
        long completed,
        long timeout,
        double avgDurationMinutes,
        List<NodeStat> byNode) {
}
