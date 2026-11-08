package com.fenglin.openapi.web;

import com.fenglin.common.core.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 开放 API 验签自检端点。
 * 契约缺口兜底：总纲只定义 /openapi/{domain}/v1 统一鉴权与可开放资源示例，未提供无业务依赖的验签探针，
 * 这里补一个只回显调用方身份的 ping，用于联调与健康检查，不承载任何业务域功能。
 */
@RestController
public class OpenApiPingController {

    @GetMapping("/openapi/ping/v1/ping")
    public ApiResponse<Map<String, Object>> ping(HttpServletRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("app_id", request.getAttribute(OpenApiSignatureFilter.ATTR_APP_ID));
        data.put("tenant_id", request.getAttribute(OpenApiSignatureFilter.ATTR_TENANT_ID));
        data.put("path", "/openapi/ping/v1/ping");
        return ApiResponse.success(data);
    }
}
