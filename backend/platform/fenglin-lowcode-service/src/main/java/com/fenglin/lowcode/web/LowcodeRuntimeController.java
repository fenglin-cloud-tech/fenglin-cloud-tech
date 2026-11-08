package com.fenglin.lowcode.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.idempotent.IdempotencyHeaders;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.common.tenant.TenantContext;
import com.fenglin.lowcode.service.LowcodeDataService;
import com.fenglin.lowcode.web.dto.LowcodeDtos.RecordView;
import com.fenglin.lowcode.web.dto.LowcodeDtos.SubmitRequest;
import com.fenglin.lowcode.web.dto.LowcodeDtos.SubmitResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LowcodeRuntimeController {

    private static final String SUBMIT_PERMISSION = "biz:lowcode:app:use";
    private static final String QUERY_PERMISSION = "biz:lowcode:app:data:view";

    private final LowcodeDataService dataService;

    public LowcodeRuntimeController(LowcodeDataService dataService) {
        this.dataService = dataService;
    }

    /** HUB-API-1204 表单数据提交（Idempotency-Key 幂等）。 */
    @PostMapping("/api/hub/v1/lowcode/apps/{code}/data")
    public ApiResponse<SubmitResult> submit(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = IdempotencyHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable String code,
            @RequestBody SubmitRequest request) {
        PermissionGuard.require(permissions, SUBMIT_PERMISSION);
        return ApiResponse.success(dataService.submit(code, request == null ? null : request.data(),
                idempotencyKey, operator(userId)));
    }

    /** 扩展接口：运行时数据查询（验收 3 要求，总纲无接口编号）。 */
    @GetMapping("/api/hub/v1/lowcode/apps/{code}/data")
    public ApiResponse<PageResult<RecordView>> records(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable String code,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        PermissionGuard.require(permissions, QUERY_PERMISSION);
        return ApiResponse.success(dataService.records(code, page, size));
    }

    private Long operator(Long userId) {
        if (userId != null) {
            return userId;
        }
        TenantContext.Snapshot context = TenantContext.get();
        return context == null ? null : context.userId();
    }
}
