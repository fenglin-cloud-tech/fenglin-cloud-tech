package com.fenglin.audit.web.dto;

/**
 * 审计哈希链校验结果（HUB-API 链校验扩展）。
 *
 * <p>status 三态，避免用单一布尔掩盖“未验证”：
 * <ul>
 *   <li>{@link #PASS}：链与同期审计日志一一对应，序号连续，哈希全部可复算。</li>
 *   <li>{@link #UNVERIFIABLE}：仅基线前的缺口（不可验证区间），链路自基线起可验证。</li>
 *   <li>{@link #FAIL}：基线后缺口、序号断裂、日志缺失或哈希不一致。</li>
 * </ul>
 * valid 仅在 {@link #PASS} 时为 true。
 */
public record ChainVerifyResult(
        long tenantId,
        long checked,
        long auditLogCount,
        long unchainedCount,
        boolean valid,
        String status,
        Long brokenSeq,
        Long brokenLogId,
        Long gapFromLogId,
        Long gapToLogId,
        Long baselineLogId,
        String message) {

    public static final String PASS = "PASS";
    public static final String UNVERIFIABLE = "UNVERIFIABLE";
    public static final String FAIL = "FAIL";
}
