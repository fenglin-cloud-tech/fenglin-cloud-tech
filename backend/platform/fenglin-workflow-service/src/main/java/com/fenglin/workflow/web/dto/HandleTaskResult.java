package com.fenglin.workflow.web.dto;

import java.util.List;

public record HandleTaskResult(
        long taskId,
        String status,
        String instanceStatus,
        List<String> nextTaskIds) {
}
