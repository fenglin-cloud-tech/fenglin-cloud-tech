package com.fenglin.openapi;

import com.fenglin.common.core.ErrorCode;

/**
 * 开放平台领域错误码，直接使用 00 接口文档 1 章定义的 0009xxx 编码。
 */
public enum OpenApiErrorCode implements ErrorCode {

    APP_EXISTS("0009001", "应用已存在", 409),
    SIGNATURE_INVALID("0009002", "签名校验失败", 401),
    QUOTA_EXCEEDED("0009003", "应用配额超限", 429),
    QUOTA_REQUEST_EXISTS("0009004", "该接口已有待审批的配额申请", 409),
    QUOTA_REQUEST_DECIDED("0009005", "配额申请已处理，不能重复审批", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    OpenApiErrorCode(String code, String message, int httpStatus) {
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
