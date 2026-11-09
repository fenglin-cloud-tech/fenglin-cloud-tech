package com.fenglin.legal.client;

import java.util.Map;

/**
 * 工作流中心客户端：合同审批、用印审批、签署、DLP 事件处置、DLP 放行。
 */
public interface WorkflowClient {

    String startApproval(String permissions, String requestId, String processKey, String businessKey,
                         String businessType, String title, Map<String, Object> formData);
}
