package com.fenglin.openapi.service;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.common.crypto.AesGcmCipher;
import com.fenglin.common.crypto.CryptoException;
import com.fenglin.openapi.mapper.AppKeyMapper;
import com.fenglin.openapi.mapper.OpenAppMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 沙箱调用的前置校验与密钥装载：必须在事务内完成（RLS 拦截器要求）。
 * 与真实 HTTP 调用分离，避免出站请求长期占用数据库连接。
 */
@Component
class OpenApiSandboxPlanner {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSandboxPlanner.class);
    private static final String AAD_SUFFIX = ":hub_app_key:app_secret_enc";

    private final OpenAppMapper appMapper;
    private final AppKeyMapper keyMapper;
    private final OpenApiDocService docService;
    private final AesGcmCipher cipher;

    OpenApiSandboxPlanner(OpenAppMapper appMapper,
                          AppKeyMapper keyMapper,
                          OpenApiDocService docService,
                          AesGcmCipher cipher) {
        this.appMapper = appMapper;
        this.keyMapper = keyMapper;
        this.docService = docService;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    SandboxPlan plan(long tenantId, long appId, long ownerId, String apiCode) {
        Map<String, Object> app = appMapper.findById(tenantId, appId);
        if (app == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "开放应用不存在: " + appId);
        }
        Object owner = app.get("owner_id");
        if (!(owner instanceof Number number) || number.longValue() != ownerId) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权访问该应用: " + appId);
        }
        if (!"APPROVED".equals(app.get("review_status"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "应用审核通过后才能联调");
        }
        if (!"ENABLED".equals(app.get("status"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "应用未启用，不能联调");
        }

        Map<String, Object> doc = docService.requirePublished(apiCode);
        if (!toBoolean(doc.get("sandbox_enabled"))) {
            throw new BizException(CommonErrorCode.CONFLICT, "该接口未开放联调沙箱: " + apiCode);
        }
        String requiredScope = (String) doc.get("required_scope");
        if (requiredScope != null && !requiredScope.isBlank()
                && !parseScopes((String) app.get("scopes")).contains(requiredScope)) {
            throw new BizException(CommonErrorCode.CONFLICT, "应用未订阅接口权限: " + requiredScope);
        }

        Map<String, Object> key = keyMapper.findActiveForSandbox(tenantId, appId);
        if (key == null) {
            throw new BizException(CommonErrorCode.CONFLICT, "应用暂无可用密钥，请先签发 AppKey/AppSecret");
        }
        String secret;
        try {
            secret = cipher.decrypt((String) key.get("app_secret_enc"), tenantId + AAD_SUFFIX);
        } catch (CryptoException ex) {
            log.error("沙箱解密 AppSecret 失败：appId={}", appId, ex);
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "AppSecret 不可用");
        }
        return new SandboxPlan((String) doc.get("code"), ((String) doc.get("method")).toUpperCase(),
                (String) doc.get("path"), (String) key.get("app_key"), secret);
    }

    private List<String> parseScopes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private boolean toBoolean(Object value) {
        return value instanceof Number number ? number.intValue() != 0 : Boolean.TRUE.equals(value);
    }

    record SandboxPlan(String code, String method, String path, String appKey, String secret) {
    }
}
