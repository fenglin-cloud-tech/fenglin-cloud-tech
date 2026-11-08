package com.fenglin.search.web.dto;

public record IndexTaskView(
        long taskId,
        String indexName,
        String taskType,
        String status,
        Long docCount,
        String errorMsg) {
}
