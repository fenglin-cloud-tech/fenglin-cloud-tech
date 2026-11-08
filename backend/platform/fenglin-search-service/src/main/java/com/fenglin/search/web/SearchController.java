package com.fenglin.search.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.search.service.SearchService;
import com.fenglin.search.web.dto.SearchQueryRequest;
import com.fenglin.search.web.dto.SearchQueryResponse;
import com.fenglin.search.web.dto.SuggestResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private static final String QUERY_PERMISSION = "biz:search:query";

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * HUB-API-901 统一检索：强制注入租户与密级过滤。
     */
    @PostMapping("/api/hub/v1/search/query")
    public ApiResponse<SearchQueryResponse> query(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Secret-Level", required = false) Integer secretLevel,
            @RequestBody SearchQueryRequest request) {
        PermissionGuard.require(permissions, QUERY_PERMISSION);
        Long operatorId = userId != null ? userId
                : (TenantContext.get() == null ? null : TenantContext.get().userId());
        return ApiResponse.success(searchService.query(request, operatorId, secretLevel == null ? 0 : secretLevel));
    }

    /**
     * HUB-API-902 全局搜索建议。
     */
    @GetMapping("/api/hub/v1/search/suggest")
    public ApiResponse<SuggestResponse> suggest(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestHeader(value = "X-Secret-Level", required = false) Integer secretLevel) {
        return ApiResponse.success(searchService.suggest(query, limit, secretLevel == null ? 0 : secretLevel));
    }
}
