package com.fenglin.iam;

import com.fenglin.common.core.ErrorCode;

/**
 * IAM 领域错误码，沿用平台总纲 00_丰麟云枢_05 接口文档 1 章编码段。
 */
public enum IamErrorCode implements ErrorCode {

    IAM_ORG_HAS_CHILDREN("0003001", "组织下存在子组织，不能删除", 409),
    IAM_ORG_NOT_FOUND("0003003", "组织不存在或不属于当前租户", 404),
    IAM_ORG_HAS_MEMBERS("0003004", "组织下存在成员，不能删除", 409),
    IAM_ORG_MOVE_CYCLE("0003005", "不能移动到自身或自己的下级组织", 400),
    IAM_ORG_VERSION_CONFLICT("0003006", "组织信息已被修改，请刷新后重试", 409),

    IAM_USER_NOT_FOUND("0003011", "用户不存在或不属于当前租户", 404),
    IAM_USER_MOBILE_DUPLICATED("0003012", "手机号已被使用", 409),
    IAM_USER_EMPLOYEE_NO_DUPLICATED("0003013", "工号已存在", 409),
    IAM_USER_EMAIL_DUPLICATED("0003014", "邮箱已被使用", 409),
    IAM_USER_VERSION_CONFLICT("0003015", "用户信息已被修改，请刷新后重试", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    IamErrorCode(String code, String message, int httpStatus) {
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
