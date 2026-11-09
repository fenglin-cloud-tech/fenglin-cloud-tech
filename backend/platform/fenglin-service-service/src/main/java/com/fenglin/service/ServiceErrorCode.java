package com.fenglin.service;

import com.fenglin.common.core.ErrorCode;

/**
 * 云服领域错误码，按 06_丰麟云服_05 接口文档 4200xxx 段约定。
 */
public enum ServiceErrorCode implements ErrorCode {

    PARAM_INVALID("4200001", "云服参数校验失败", 400),
    NO_PERMISSION("4200002", "服务域无权限（功能或数据）", 403),
    RESOURCE_NOT_FOUND("4200003", "服务资源不存在", 404),
    STATE_CONFLICT("4200004", "服务对象状态冲突（并发/重复）", 409),

    TICKET_TRANSITION_INVALID("4201001", "工单状态流转不允许", 409),
    TICKET_ALREADY_RATED("4201002", "该工单已提交满意度评价", 409),
    TICKET_SLA_MISSING("4201003", "匹配不到 SLA 策略", 400),

    CATALOG_ITEM_DISABLED("4202001", "目录项已停用", 409),
    CATALOG_SCOPE_DENIED("4202002", "不在目录项可见范围", 403),

    KB_NOT_PUBLISHED("4203001", "知识文章未发布", 409),
    CSAT_SCORE_INVALID("4203002", "满意度评分需在 1-5 之间", 400),

    ASSET_STATE_INVALID("4204001", "资产状态不允许该操作", 409),
    CHANGE_STATE_INVALID("4204002", "变更状态不允许该操作", 409),
    PROBLEM_STATE_INVALID("4204003", "问题状态不允许该操作", 409),

    APP_STATE_INVALID("4205001", "低代码应用状态不允许该操作", 409),
    FORM_FIELD_INVALID("4205002", "表单字段定义不合法", 400),
    FLOW_GRAPH_INVALID("4205003", "流程定义图不合法", 400),
    MODEL_COMPAT_BROKEN("4205004", "模型变更不兼容，需新版本", 409),
    RELEASE_APPROVAL_PENDING("4205005", "发布审批未通过", 409),
    SCRIPT_SCAN_BLOCKED("4205006", "脚本静态扫描存在高危项", 409),

    MINING_DATA_INSUFFICIENT("4206001", "挖掘事件不足，无法生成模型", 409),
    CANDIDATE_NOT_FOUND("4206002", "自动化候选不存在", 404),

    ROBOT_OFFLINE("4207001", "机器人不在线", 409),
    AUTOMATION_NOT_PUBLISHED("4207002", "自动化编排未发布", 409),
    CREDENTIAL_FORBIDDEN("4207003", "凭据读取需内部令牌且禁止批量导出", 403),
    TASK_ACTION_INVALID("4207004", "任务状态不允许该操作", 409),
    TARGET_NOT_ALLOWED("4207005", "目标系统不在白名单", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    ServiceErrorCode(String code, String message, int httpStatus) {
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
