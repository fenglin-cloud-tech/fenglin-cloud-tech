package com.fenglin.finance.client;

import java.util.Map;

/**
 * 工作流中心客户端：云财审批统一复用平台 BPMN 能力。
 */
public interface WorkflowClient {

    String startApproval(String permissions, String requestId, String processKey, String businessKey,
                         String businessType, String title, Map<String, Object> formData);
}
