package com.fenglin.workflow.web.dto;

public record StartInstanceResult(
        long instanceId,
        String instanceNo,
        String status,
        String firstTaskId) {
}
