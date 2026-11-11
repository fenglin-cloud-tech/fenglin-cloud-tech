package com.fenglin.legal;

import com.fenglin.common.core.ErrorCode;

/**
 * 云法领域错误码，按 07_丰麟云法_05 接口文档 70xxxxx 段约定。
 */
public enum LegalErrorCode implements ErrorCode {

    PARAM_INVALID("7000001", "云法通用参数错误", 400),
    NO_PERMISSION("7000002", "云法功能无权限", 403),
    RESOURCE_NOT_FOUND("7000004", "云法资源不存在", 404),

    CONTRACT_NO_DUP("7001001", "合同编号重复", 409),
    CONTRACT_STATE_INVALID("7001002", "合同状态不允许该操作", 400),
    REQUIRED_MISSING("7001003", "必填项/必留条款缺失", 400),
    TEMPLATE_VAR_MISSING("7001004", "模板变量未填充", 400),
    APPROVAL_CHAIN_INVALID("7001005", "审批链配置不完整", 400),
    SEAL_UNAVAILABLE("7001006", "印章不可用/已吊销/已过期", 403),
    SEAL_VERSION_MISMATCH("7001007", "用印版本与审批版本不一致", 409),
    CA_UNAVAILABLE("7001008", "CA/电子签章服务不可用", 503),
    EVIDENCE_CHAIN_INVALID("7001009", "签署证据链校验失败", 409),
    SIGNER_VERIFY_FAILED("7001010", "签署方身份核验失败", 403),
    CONTRACT_ARCHIVED("7001011", "合同已归档不可修改", 409),
    HASH_VERIFY_FAILED("7001012", "存证/完整性哈希校验失败", 409),

    RULE_EXPRESSION_INVALID("7002001", "敏感规则表达式非法", 400),
    POLICY_CONFLICT("7002002", "DLP 策略冲突（优先级/生效时间重叠）", 409),
    EVENT_DUPLICATED("7002003", "外发事件重复上报", 409),
    CHANNEL_DISABLED("7002004", "通道未启用", 400),
    WHITELIST_INVALID("7002005", "白名单规则无效/已过期", 400),
    WATERMARK_MISMATCH("7002006", "水印比对不一致", 409),
    DLP_AUDIT_CHAIN_INVALID("7002007", "DLP 审计链路校验失败", 409),
    TERMINAL_OFFLINE("7002008", "终端未注册或已离线", 403),
    REPORT_QUOTA_EXCEEDED("7002009", "批量上报超出配额", 429),
    RELEASE_APPROVAL_REQUIRED("7002010", "敏感数据外发需审批放行", 403),
    PII_REQUEST_EXISTS("7002011", "该相对方已有待审批的明文查看申请", 409),
    PII_REQUEST_DECIDED("7002012", "明文查看申请已处理，不能重复审批", 409),
    PII_NOT_APPROVED("7002013", "未获得该相对方的明文查看授权", 403);

    private final String code;
    private final String message;
    private final int httpStatus;

    LegalErrorCode(String code, String message, int httpStatus) {
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
