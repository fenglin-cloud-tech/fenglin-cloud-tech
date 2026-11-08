package com.fenglin.iam.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.iam.service.ParamService;
import com.fenglin.iam.web.dto.ParamView;
import com.fenglin.iam.web.dto.UpsertParamRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 参数配置：接口文档未定义该组接口（契约缺口），按数据库设计 4.25 与管理员手册第 7 章兜底。
 */
@RestController
public class ParamController {

    private static final String MANAGE_PERMISSION = "tenant:iam:param:manage";

    private final ParamService paramService;

    public ParamController(ParamService paramService) {
        this.paramService = paramService;
    }

    @GetMapping("/api/hub/v1/admin/params")
    public ApiResponse<List<ParamView>> list(@RequestParam(required = false) String scope,
                                             @RequestParam(required = false) String productCode) {
        return ApiResponse.success(paramService.list(scope, productCode));
    }

    @GetMapping("/api/hub/v1/admin/params/{key}")
    public ApiResponse<ParamView> get(@PathVariable String key) {
        return ApiResponse.success(paramService.get(key));
    }

    @PutMapping("/api/hub/v1/admin/params/{key}")
    public ApiResponse<ParamView> upsert(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable String key,
            @RequestBody UpsertParamRequest request) {
        PermissionGuard.require(permissions, MANAGE_PERMISSION);
        return ApiResponse.success(paramService.upsert(key, request));
    }

    @DeleteMapping("/api/hub/v1/admin/params/{key}")
    public ApiResponse<Void> delete(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable String key) {
        PermissionGuard.require(permissions, MANAGE_PERMISSION);
        paramService.delete(key);
        return ApiResponse.success();
    }
}
