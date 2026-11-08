package com.fenglin.finance;

import com.fenglin.common.core.ErrorCode;

/**
 * 云财领域错误码，采用 04_丰麟云财_05 接口文档 1.3 节约定编码。
 */
public enum FinanceErrorCode implements ErrorCode {

    PARAM_INVALID("4000001", "云财参数校验失败", 400),
    NO_PERMISSION("4000002", "财务域无权限（功能或数据）", 403),
    RESOURCE_NOT_FOUND("4000003", "财务资源不存在", 404),
    STATE_CONFLICT("4000004", "财务状态冲突（并发/重复）", 409),
    IDEMPOTENCY_CONFLICT("4000005", "幂等键冲突", 409),

    VOUCHER_UNBALANCED("4001001", "凭证借贷不平衡", 400),
    BUDGET_INSUFFICIENT("4001002", "预算不足", 409),
    PERIOD_CLOSED("4001003", "会计期间已结账", 409),
    VOUCHER_POSTED_IMMUTABLE("4001004", "凭证已过账不可修改", 409),
    AUDITOR_SAME_AS_MAKER("4001005", "制单人与审核人不得为同一人", 400),
    VOUCHER_ALREADY_POSTED("4001006", "凭证已过账，无需重复过账", 409),
    UNPOSTED_VOUCHER_EXISTS("4001007", "存在未过账凭证，不能结账", 409),
    TRIAL_BALANCE_UNBALANCED("4001008", "试算不平衡", 400),
    HASH_CHAIN_BROKEN("4001009", "凭证哈希链校验失败", 409),
    CLOSE_CHECK_FAILED("4001010", "结账校验未通过", 409),
    SUBJECT_NOT_LEAF("4001011", "科目非末级，不能记账", 400),

    PROJECT_STATE_INVALID("4002001", "项目状态不允许该操作", 409),
    TIMESHEET_HOURS_EXCEEDED("4002002", "单日工时超过 24 小时", 400),
    SETTLEMENT_AMOUNT_EXCEEDED("4002003", "结算金额超过合同未结算金额", 400),

    DEPRECIATION_DUPLICATED("4003001", "本期已计提折旧", 409),
    ASSET_STATE_INVALID("4003002", "资产状态不允许该操作", 409),

    FACTOR_MISSING("4004001", "碳活动缺少匹配的排放因子", 400),
    ACTIVITY_LOCKED("4004002", "碳活动已锁定，不可修改", 409),

    ESG_REPORT_STATE_INVALID("4005001", "ESG 报告状态不允许该操作", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    FinanceErrorCode(String code, String message, int httpStatus) {
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
