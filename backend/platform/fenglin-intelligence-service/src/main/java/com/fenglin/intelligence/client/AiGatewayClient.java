package com.fenglin.intelligence.client;

import java.util.List;
import java.util.Map;

/**
 * 平台 AI 中台客户端（唯一模型网关/护栏/计量/授权入口）。
 * 云智只做知识检索与业务编排，不在此实现模型调用与计费逻辑。
 */
public interface AiGatewayClient {

    ChatResult chat(String content, Double temperature, Integer secretLevel);

    Map<String, Object> grants(String hostProduct);

    Map<String, Object> metering(String mode, String hostProduct, String from, String to);

    Map<String, Object> validateMode();

    record ChatResult(String answer, int tokenIn, int tokenOut, long ttftMs, boolean degraded,
                      List<Map<String, Object>> guardrailHits) {
    }
}
