package com.fenglin.mdm;

import com.fenglin.common.core.ErrorCode;

/**
 * MDM 领域错误码。
 */
public enum MdmErrorCode implements ErrorCode {

    PARAM_INVALID("4002001", "主数据参数校验失败", 400),
    NO_PERMISSION("4002002", "主数据域无权限", 403),
    RESOURCE_NOT_FOUND("4002003", "主数据资源不存在", 404),
    STATE_CONFLICT("4002004", "主数据状态冲突", 409),
    PARTNER_DUPLICATED("4002005", "同租户已存在归一化同名的往来单位", 409),
    MERGE_SELF("4002006", "不能将单位合并到自身", 400),
    MERGE_CYCLE("4002007", "合并会形成环", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    MdmErrorCode(String code, String message, int httpStatus) {
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
