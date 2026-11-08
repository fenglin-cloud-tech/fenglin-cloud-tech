package com.fenglin.workflow.web.dto;

import java.time.Instant;

public record DefinitionView(
        long definitionId,
        String processKey,
        String name,
        String category,
        int defVersion,
        String status,
        String formKey,
        Instant createdAt) {
}
