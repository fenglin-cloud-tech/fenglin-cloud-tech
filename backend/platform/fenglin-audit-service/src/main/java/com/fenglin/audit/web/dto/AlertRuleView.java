package com.fenglin.audit.web.dto;

import java.util.List;

/**
 * 审计告警规则视图（HUB-API-804 响应未在契约中定义，返回保存后的规则集）。
 */
public record AlertRuleView(
        long id,
        String code,
        String eventType,
        int threshold,
        String receiverRole,
        List<String> channels,
        boolean enabled) {
}
