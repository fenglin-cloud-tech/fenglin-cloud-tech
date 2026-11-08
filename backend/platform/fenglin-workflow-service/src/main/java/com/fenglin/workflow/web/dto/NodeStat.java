package com.fenglin.workflow.web.dto;

public record NodeStat(
        String nodeKey,
        double avgMinutes,
        long count) {
}
