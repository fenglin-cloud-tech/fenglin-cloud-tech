package com.fenglin.intelligence.client;

import java.util.Map;

/**
 * 工作流中心客户端：知识入库审核、RAG 问答留痕、Agent 运行、洞察生成。
 */
public interface WorkflowClient {

    String start(String permissions, String requestId, String processKey, String businessKey,
                 String businessType, String title, Map<String, Object> formData);
}
