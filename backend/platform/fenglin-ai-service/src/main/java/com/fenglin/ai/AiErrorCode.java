package com.fenglin.ai;

import com.fenglin.common.core.ErrorCode;

public enum AiErrorCode implements ErrorCode {

    CAPABILITY_DENIED("0012001", "AI 能力未授权", 403),
    GUARDRAIL_BLOCKED("0012002", "内容命中护栏规则", 400),
    MODEL_DEGRADED("0000008", "模型服务降级", 503),
    MODE_INCONSISTENT("0000005", "双模式配置不一致", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    AiErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
