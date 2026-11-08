package com.fenglin.collab.client;

import java.util.Map;

/**
 * 工作流中心客户端（复用平台工作流能力，不重复实现流程引擎）。
 */
public interface WorkflowClient {

    /**
     * 提交审批实例，返回工作流实例号。
     */
    String startApproval(String permissions, String requestId, String processKey, String businessKey,
                         String businessType, String title, Map<String, Object> formData);
}
