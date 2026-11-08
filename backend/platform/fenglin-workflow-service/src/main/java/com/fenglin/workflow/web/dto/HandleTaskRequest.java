package com.fenglin.workflow.web.dto;

public record HandleTaskRequest(
        String action,
        String comment,
        Long assigneeId) {
}
