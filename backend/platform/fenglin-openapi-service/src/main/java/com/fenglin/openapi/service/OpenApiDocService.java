package com.fenglin.openapi.service;

import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.openapi.mapper.ApiDocMapper;
import com.fenglin.openapi.web.dto.OpenApiDtos.PortalApiDocView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 开放接口文档元数据：平台发布在 hub_api_doc，门户按应用标注订阅状态。
 */
@Service
public class OpenApiDocService {

    private final ApiDocMapper docMapper;

    public OpenApiDocService(ApiDocMapper docMapper) {
        this.docMapper = docMapper;
    }

    @Transactional(readOnly = true)
    public List<PortalApiDocView> listPublished(String domain, List<String> subscribedScopes) {
        Set<String> subscribed = subscribedScopes == null ? Set.of() : Set.copyOf(subscribedScopes);
        return docMapper.listPublished(blankToNull(domain)).stream()
                .map(row -> toView(row, subscribed))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> requirePublished(String code) {
        Map<String, Object> row = docMapper.findPublished(code);
        if (row == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "接口不存在或未发布: " + code);
        }
        return row;
    }

    private PortalApiDocView toView(Map<String, Object> row, Set<String> subscribed) {
        String requiredScope = (String) row.get("required_scope");
        boolean subscribedScope = requiredScope == null || requiredScope.isBlank()
                || subscribed.contains(requiredScope);
        return new PortalApiDocView(
                (String) row.get("code"),
                (String) row.get("method"),
                (String) row.get("path"),
                (String) row.get("name"),
                (String) row.get("domain"),
                (String) row.get("summary"),
                (String) row.get("description"),
                requiredScope,
                (String) row.get("request_schema"),
                (String) row.get("response_schema"),
                (String) row.get("sample_request"),
                (String) row.get("sample_response"),
                toBoolean(row.get("sandbox_enabled")),
                subscribedScope);
    }

    private boolean toBoolean(Object value) {
        return value instanceof Number number ? number.intValue() != 0 : Boolean.TRUE.equals(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
