package com.fenglin.common.core;

public enum CommonErrorCode implements ErrorCode {

    SUCCESS("0", "OK", 200),
    BAD_REQUEST("FL-COMMON-0400", "请求参数不合法", 400),
    UNAUTHORIZED("FL-COMMON-0401", "未认证或凭证已失效", 401),
    FORBIDDEN("FL-COMMON-0403", "无权访问该资源", 403),
    NOT_FOUND("FL-COMMON-0404", "资源不存在", 404),
    CONFLICT("FL-COMMON-0409", "资源状态冲突", 409),
    TENANT_REQUIRED("FL-COMMON-0421", "缺少有效的租户上下文", 400),
    TENANT_TRANSACTION_REQUIRED("FL-COMMON-0422", "租户查询必须在事务内执行", 500),
    INTERNAL_ERROR("FL-COMMON-0500", "系统内部错误", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    CommonErrorCode(String code, String message, int httpStatus) {
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
