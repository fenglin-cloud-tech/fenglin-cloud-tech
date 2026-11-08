package com.fenglin.workflow.web.dto;

import java.time.Instant;

public record NodeView(
        String nodeKey,
        String nodeName,
        String status,
        Long handlerId,
        String handler,
        String comment,
        Instant handledAt) {
}
