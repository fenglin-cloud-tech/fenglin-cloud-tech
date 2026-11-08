package com.fenglin.workflow.web.dto;

import java.time.Instant;
import java.util.List;

public record InstanceDetailResult(
        long instanceId,
        String instanceNo,
        String processKey,
        int defVersion,
        String status,
        String result,
        String title,
        String businessKey,
        String businessType,
        Long initiatorId,
        Instant startedAt,
        Instant endedAt,
        List<NodeView> nodes) {
}
