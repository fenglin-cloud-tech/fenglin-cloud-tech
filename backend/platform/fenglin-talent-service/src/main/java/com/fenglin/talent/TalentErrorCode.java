package com.fenglin.talent;

import com.fenglin.common.core.ErrorCode;

/**
 * 云才领域错误码，采用 03_丰麟云才_05 接口文档 1.3 节约定编码。
 */
public enum TalentErrorCode implements ErrorCode {

    PARAM_INVALID("3000001", "云才参数校验失败", 400),
    NO_PERMISSION("3000002", "人才域无权限（功能或数据）", 403),
    RESOURCE_NOT_FOUND("3000003", "人才资源不存在", 404),
    STATE_CONFLICT("3000004", "人才状态冲突（并发/重复）", 409),

    PII_DUPLICATED("3001001", "手机号或身份证号已存在", 409),
    ORG_STRUCTURE_INVALID("3001005", "组织编码非法或移动形成环", 400),
    ORG_NOT_EMPTY("3001006", "组织下存在在职员工/下级组织/在招需求", 409),
    HEADCOUNT_EXCEEDED("3001020", "招聘需求人数超出编制余额", 409),
    TRANSFER_EFFECTIVE_INVALID("3001090", "异动生效日非法（不得早于今天）", 400),
    SALARY_OUT_OF_BAND("3001095", "薪资超出职级薪酬带宽", 400),

    SESSION_FULL("3002005", "班次已满，已进入候补", 409),
    ENROLL_DEADLINE_PASSED("3002006", "报名已截止", 400),

    PROBATION_FLOW_FORBIDDEN("3003005", "试用期员工不可申请活水", 403),
    FLOW_IN_PROGRESS("3003006", "存在进行中的活水申请", 409),

    SURVEY_SAMPLE_INSUFFICIENT("3004005", "部门样本不足，结果不可见", 403),

    OKR_ALIGNMENT_CYCLE("3005005", "OKR 对齐形成环", 400),
    OKR_ALIGNMENT_CROSS_PERIOD("3005006", "跨周期对齐禁止", 400),
    PROPOSAL_SELF_VOTE_FORBIDDEN("3005015", "提案人不可自我投票", 403);

    private final String code;
    private final String message;
    private final int httpStatus;

    TalentErrorCode(String code, String message, int httpStatus) {
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
