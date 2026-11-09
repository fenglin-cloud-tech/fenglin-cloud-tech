package com.fenglin.rnd;

import com.fenglin.common.core.ErrorCode;

/**
 * 云研领域错误码，采用 05_丰麟云研_05 接口文档错误码分段约定（4000xxx 段顺延为 4100xxx）。
 */
public enum RndErrorCode implements ErrorCode {

    PARAM_INVALID("4100001", "云研参数校验失败", 400),
    NO_PERMISSION("4100002", "研发域无权限（功能或数据）", 403),
    RESOURCE_NOT_FOUND("4100003", "研发资源不存在", 404),
    STATE_CONFLICT("4100004", "研发对象状态冲突（并发/重复）", 409),

    PRODUCT_CODE_DUPLICATED("4101001", "产品编码已存在", 409),
    MEMBER_DUPLICATED("4101002", "成员已在产品中", 409),

    REQUIREMENT_STATUS_INVALID("4102001", "需求状态不存在或不属于该产品", 400),
    REQUIREMENT_TRANSITION_INVALID("4102002", "需求状态流转不允许", 409),
    REQUIREMENT_ALREADY_REVIEWING("4102003", "需求已在评审中", 409),

    SPRINT_DATE_INVALID("4103001", "迭代起止日期不合法", 400),
    SPRINT_STATE_INVALID("4103002", "迭代状态不允许该操作", 409),
    SPRINT_CAPACITY_EXCEEDED("4103003", "迭代容量已满", 409),

    TASK_STATE_INVALID("4104001", "任务状态流转不允许", 409),
    WORKLOG_HOURS_EXCEEDED("4104002", "单日工时超过 24 小时", 400),

    DEFECT_STATE_INVALID("4105001", "缺陷状态流转不允许", 409),
    DEFECT_REOPEN_LIMIT("4105002", "缺陷重新打开次数超限", 409),

    TEST_CASE_NOT_APPROVED("4106001", "用例未评审通过，不能加入测试计划", 409),
    TEST_EXECUTION_FINISHED("4106002", "测试执行已完成，不能重复提交", 409),

    REPO_TOKEN_INVALID("4107001", "代码库访问令牌无效", 400),
    WEBHOOK_SIGNATURE_INVALID("4107002", "Webhook 签名校验失败", 401),

    PIPELINE_STATE_INVALID("4108001", "流水线状态不允许该操作", 409),
    RELEASE_STATE_INVALID("4109001", "发布单状态不允许该操作", 409),
    RELEASE_WINDOW_INVALID("4109002", "发布时间窗不合法", 400),

    METRIC_CONFIG_MISSING("4110001", "度量口径未配置", 400),
    METRIC_DATA_INSUFFICIENT("4110002", "度量数据不足，无法计算", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    RndErrorCode(String code, String message, int httpStatus) {
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
