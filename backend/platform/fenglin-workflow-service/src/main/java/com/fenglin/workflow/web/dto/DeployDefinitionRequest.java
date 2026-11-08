package com.fenglin.workflow.web.dto;

public record DeployDefinitionRequest(
        String processKey,
        String name,
        String category,
        String bpmnXml,
        String formKey) {
}
