package com.fenglin.collab;

import com.fenglin.common.core.ErrorCode;

/**
 * 云协领域错误码，采用 02_丰麟云协_05 接口文档 1.3 节约定编码。
 */
public enum CollabErrorCode implements ErrorCode {

    PARAM_INVALID("2000001", "云协参数校验失败", 400),
    NO_PERMISSION("2000002", "协作域无权限（功能或数据）", 403),
    RESOURCE_NOT_FOUND("2000003", "协作资源不存在", 404),
    STATE_CONFLICT("2000004", "协作状态冲突（并发/重复）", 409),

    MESSAGE_SENSITIVE("2001007", "消息命中敏感词策略", 400),
    MEMBER_LIMIT("2001008", "会话成员数超上限", 400),
    CONVERSATION_NOT_FOUND("2001010", "会话不存在或已解散", 404),
    NOT_MEMBER("2001011", "非会话成员", 403),

    DOC_NO_PERMISSION("2002002", "文档无权限", 403),
    DOC_LINK_EXPIRED("2002009", "分享链接已过期", 410),
    DOC_CONFIDENTIAL_FORBIDDEN("2002010", "机密文档禁止外链/下载", 403),

    EVENT_TIME_CONFLICT("2003001", "日程时段冲突", 409),
    RRULE_INVALID("2003002", "RRULE 规则非法", 400),

    OKR_ALIGN_CYCLE("2005001", "OKR 对齐形成循环", 400),
    OKR_PERIOD_CLOSED("2005002", "OKR 周期已关闭", 409),
    KR_PROGRESS_OUT_OF_RANGE("2005003", "KR 进度越界", 400),

    ROOM_OCCUPIED("2006005", "会议室时段已被占用", 409),
    ROOM_DUPLICATE("2006006", "重复预定（幂等冲突）", 409),
    ROOM_MAINTENANCE("2006011", "会议室维护中不可预定", 409),
    EXTERNAL_NOT_ALLOWED("2006012", "未允许外部参与人", 403),

    APV_TEMPLATE_NOT_FOUND("2007001", "审批模板不存在或未启用", 404),
    APV_TASK_HANDLED("2007002", "审批任务已被处理", 409),
    APV_NOT_ASSIGNEE("2007003", "非当前审批处理人", 403),
    APV_OPERATION_UNSUPPORTED("2007004", "审批不支持该操作", 409),

    WIDGET_NOT_FOUND("2008001", "工作台组件不存在", 404),
    TODO_SOURCE_DUPLICATE("2008002", "待办来源重复", 409),

    ONA_SAMPLE_INSUFFICIENT("2009001", "ONA 数据样本不足", 403),
    ONA_PROFILE_FORBIDDEN("2009002", "ONA 越权访问画像", 403);

    private final String code;
    private final String message;
    private final int httpStatus;

    CollabErrorCode(String code, String message, int httpStatus) {
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
