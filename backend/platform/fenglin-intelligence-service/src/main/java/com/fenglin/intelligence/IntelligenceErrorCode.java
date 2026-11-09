package com.fenglin.intelligence;

import com.fenglin.common.core.ErrorCode;

/**
 * 云智领域错误码，按 08_丰麟云智_05 接口文档 80xxxxx 段约定。
 */
public enum IntelligenceErrorCode implements ErrorCode {

    PARAM_INVALID("8000001", "云智通用参数错误", 400),
    NO_PERMISSION("8000002", "云智功能无权限", 403),
    QUOTA_INSUFFICIENT("8000003", "租户配额/许可不足", 403),

    KB_NOT_FOUND("8001001", "知识库不存在", 404),
    DATA_SOURCE_CONNECT_FAILED("8001002", "数据源连接失败", 400),
    DOC_PARSE_FAILED("8001003", "文档解析失败", 400),
    INDEX_TASK_CONFLICT("8001004", "索引任务冲突", 409),
    SEARCH_FORBIDDEN("8001005", "检索越权（文档不可见）", 403),
    SEARCH_PARAM_INVALID("8001006", "检索参数非法（TopK/权重）", 400),
    REVIEW_STATE_CONFLICT("8001007", "入库审核状态冲突", 409),
    CODE_EXISTS("8001008", "编码已存在", 409),

    CONVERSATION_NOT_FOUND("8002001", "会话不存在或已归档", 400),
    MODEL_ROUTE_UNAVAILABLE("8002002", "模型路由不可用", 409),
    PROMPT_INJECTION_BLOCKED("8002003", "提示注入被拦截", 403),
    OUTPUT_COMPLIANCE_BLOCKED("8002004", "输出内容合规拦截", 400),
    TOKEN_QUOTA_EXCEEDED("8002005", "超出 Token 配额", 403),
    AGENT_STEP_EXCEEDED("8002006", "Agent 步数超限", 409),
    AGENT_WAITING_CONFIRM("8002007", "Agent 等待人工确认", 409),

    DATASET_INVALID("8003001", "数据集定义非法", 400),
    METRIC_CONFLICT("8003002", "指标口径冲突", 400),
    BI_FORBIDDEN("8003003", "BI 数据越权", 403),
    QUERY_TIMEOUT("8003004", "查询超时", 504),
    NL2SQL_UNPARSABLE("8003005", "NL2SQL 无法解析（需澄清）", 400),
    INSIGHT_DUPLICATED("8003006", "洞察任务重复触发", 409),

    CAPABILITY_NOT_GRANTED("8004001", "能力未授权（embedded）", 403),
    MODE_CONFIG_INCONSISTENT("8004002", "双模式配置不一致", 400),
    DEPENDENCY_DEGRADED("8004003", "依赖降级中", 503);

    private final String code;
    private final String message;
    private final int httpStatus;

    IntelligenceErrorCode(String code, String message, int httpStatus) {
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
