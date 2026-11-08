package com.fenglin.workflow.web.dto;

import java.util.Map;

public record StartInstanceRequest(
        String processKey,
        String businessKey,
        String businessType,
        String title,
        Map<String, Object> formData) {
}
