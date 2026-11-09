package com.fenglin.service.client;

import java.util.Map;

/**
 * 工作流中心客户端：目录项审批、变更审批、低代码发布审批、RPA 编排发布复用平台 BPMN。
 */
public interface WorkflowClient {

    String startApproval(String permissions, String requestId, String processKey, String businessKey,
                         String businessType, String title, Map<String, Object> formData);
}
