package com.fenglin.workflow.web.dto;

import java.time.Instant;

public record PendingTaskView(
        long taskId,
        long instanceId,
        String instanceNo,
        String title,
        String nodeKey,
        String nodeName,
        Long initiatorId,
        String initiator,
        Instant dueAt,
        boolean urgent) {
}
