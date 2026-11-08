package com.fenglin.crm;

import com.fenglin.common.core.ErrorCode;

/**
 * CRM 领域错误码，采用 01_丰麟云销_05 接口文档约定的 1001xxx / 1002xxx / 1003xxx 编码。
 */
public enum CrmErrorCode implements ErrorCode {

    LEAD_DUPLICATE("1001001", "线索已存在相同手机号", 409),
    LEAD_STATUS_INVALID("1001002", "线索状态不允许该操作", 400),
    LEAD_REQUIRED_MISSING("1001003", "线索必填项缺失", 400),
    OPPORTUNITY_STAGE_REJECTED("1002001", "商机阶段推进条件不满足", 400),
    OPPORTUNITY_CONFLICT("1002002", "商机状态冲突", 409),
    QUOTATION_DISCOUNT_UNAPPROVED("1003001", "报价折扣或金额超限，必须提交审批", 400),
    QUOTATION_STATUS_INVALID("1003002", "报价单状态不允许该操作", 400);

    private final String code;
    private final String message;
    private final int httpStatus;

    CrmErrorCode(String code, String message, int httpStatus) {
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
