package com.fenglin.audit.web.dto;

/**
 * 审计哈希链校验结果（HUB-API 链校验扩展）。
 */
public record ChainVerifyResult(
        long tenantId,
        long checked,
        boolean valid,
        Long brokenSeq,
        Long brokenLogId,
        String message) {
}
