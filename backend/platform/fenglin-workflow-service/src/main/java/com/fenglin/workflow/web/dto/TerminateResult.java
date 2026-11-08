package com.fenglin.workflow.web.dto;

public record TerminateResult(
        long instanceId,
        String status,
        String result) {
}
