package com.fenglin.iam.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.iam.service.DictService;
import com.fenglin.iam.web.dto.DictItemView;
import com.fenglin.iam.web.dto.DictTypeView;
import com.fenglin.iam.web.dto.UpsertDictItemRequest;
import jakarta.validation.Valid;
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
 * 字典管理：接口文档未定义该组接口（契约缺口），按数据库设计 4.23/4.24 与管理员手册第 7 章兜底。
 */
@RestController
public class DictController {

    private static final String MANAGE_PERMISSION = "tenant:iam:dict:manage";

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/api/hub/v1/admin/dicts")
    public ApiResponse<List<DictTypeView>> listTypes(@RequestParam(required = false) String scope) {
        return ApiResponse.success(dictService.listTypes(scope));
    }

    @GetMapping("/api/hub/v1/admin/dicts/{code}/items")
    public ApiResponse<List<DictItemView>> listItems(@PathVariable String code) {
        return ApiResponse.success(dictService.listItems(code));
    }

    @PutMapping("/api/hub/v1/admin/dicts/{code}/items/{value}")
    public ApiResponse<DictItemView> upsertItem(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable String code,
            @PathVariable String value,
            @Valid @RequestBody UpsertDictItemRequest request) {
        PermissionGuard.require(permissions, MANAGE_PERMISSION);
        return ApiResponse.success(dictService.upsertItem(code, value, request));
    }

    @DeleteMapping("/api/hub/v1/admin/dicts/{code}/items/{value}")
    public ApiResponse<Void> deleteItem(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable String code,
            @PathVariable String value) {
        PermissionGuard.require(permissions, MANAGE_PERMISSION);
        dictService.deleteItem(code, value);
        return ApiResponse.success();
    }
}
