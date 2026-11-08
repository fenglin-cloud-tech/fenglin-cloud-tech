package com.fenglin.lowcode;

import com.fenglin.common.core.ErrorCode;

public enum LowcodeErrorCode implements ErrorCode {

    MODEL_CONFLICT("0011001", "低代码模型冲突", 409),
    DEPENDENCY_UNAVAILABLE("0000008", "依赖服务不可用", 503);

    private final String code;
    private final String message;
    private final int httpStatus;

    LowcodeErrorCode(String code, String message, int httpStatus) {
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
