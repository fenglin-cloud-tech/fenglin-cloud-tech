package com.fenglin.iam.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.iam.service.IamService;
import com.fenglin.iam.web.dto.AddOrgMemberRequest;
import com.fenglin.iam.web.dto.AssignRolesRequest;
import com.fenglin.iam.web.dto.CreateOrgRequest;
import com.fenglin.iam.web.dto.CreateRoleRequest;
import com.fenglin.iam.web.dto.CreateUserRequest;
import com.fenglin.iam.web.dto.CreateUserResult;
import com.fenglin.iam.web.dto.EmployeeBindingView;
import com.fenglin.iam.web.dto.GrantPermissionsRequest;
import com.fenglin.iam.web.dto.MenuView;
import com.fenglin.iam.web.dto.MoveOrgRequest;
import com.fenglin.iam.web.dto.OrgTreeNode;
import com.fenglin.iam.web.dto.PermissionNode;
import com.fenglin.iam.web.dto.ResetPasswordRequest;
import com.fenglin.iam.web.dto.ResetPasswordResult;
import com.fenglin.iam.web.dto.RoleView;
import com.fenglin.iam.web.dto.UpdateOrgRequest;
import com.fenglin.iam.web.dto.UpdateUserProfileRequest;
import com.fenglin.iam.web.dto.UserView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户/角色/权限 HUB-API-301/303/401/402/403/404/405 与 INT-102。
 * 写接口由网关下发 X-Permissions 后在此做二次校验。
 */
@RestController
public class IamController {

    private static final String CREATE_USER_PERMISSION = "tenant:iam:user:create";
    private static final String UPDATE_USER_PERMISSION = "tenant:iam:user:update";
    private static final String MANAGE_ROLE_PERMISSION = "tenant:iam:role:manage";
    private static final String ASSIGN_ROLE_PERMISSION = "tenant:iam:role:assign";
    private static final String MANAGE_ORG_PERMISSION = "tenant:iam:org:manage";

    private final IamService iamService;

    public IamController(IamService iamService) {
        this.iamService = iamService;
    }

    @PostMapping("/api/hub/v1/admin/users")
    public ApiResponse<CreateUserResult> createUser(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @Valid @RequestBody CreateUserRequest request) {
        PermissionGuard.require(permissions, CREATE_USER_PERMISSION);
        return ApiResponse.success(iamService.createUser(request));
    }

    @GetMapping("/api/hub/v1/admin/users")
    public ApiResponse<PageResult<UserView>> listUsers(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<UserView> records = iamService.listUsers(orgId, status, keyword, page, size);
        return ApiResponse.success(PageResult.of(records, iamService.countUsers(orgId, status, keyword),
                Math.max(page, 1), Math.min(Math.max(size, 1), 200)));
    }

    @PutMapping("/api/hub/v1/admin/users/{id}")
    public ApiResponse<UserView> updateUser(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        PermissionGuard.require(permissions, UPDATE_USER_PERMISSION);
        return ApiResponse.success(iamService.updateUserProfile(id, request));
    }

    /**
     * 管理员重置口令：body 可传 password（强度校验），不传则服务端生成并只在响应返回一次。
     */
    @PostMapping("/api/hub/v1/admin/users/{id}/reset-password")
    public ApiResponse<ResetPasswordResult> resetPassword(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody(required = false) ResetPasswordRequest request) {
        PermissionGuard.require(permissions, UPDATE_USER_PERMISSION);
        return ApiResponse.success(iamService.resetPassword(id, request));
    }

    @GetMapping("/api/hub/v1/admin/orgs/tree")
    public ApiResponse<List<OrgTreeNode>> orgTree(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, MANAGE_ORG_PERMISSION);
        return ApiResponse.success(iamService.orgTree());
    }

    @PostMapping("/api/hub/v1/admin/orgs")
    public ApiResponse<OrgTreeNode> createOrg(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @Valid @RequestBody CreateOrgRequest request) {
        PermissionGuard.require(permissions, MANAGE_ORG_PERMISSION);
        return ApiResponse.success(iamService.createOrg(request));
    }

    @PutMapping("/api/hub/v1/admin/orgs/{id}")
    public ApiResponse<OrgTreeNode> updateOrg(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @Valid @RequestBody UpdateOrgRequest request) {
        PermissionGuard.require(permissions, MANAGE_ORG_PERMISSION);
        return ApiResponse.success(iamService.updateOrg(id, request));
    }

    @PutMapping("/api/hub/v1/admin/orgs/{id}/move")
    public ApiResponse<OrgTreeNode> moveOrg(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @Valid @RequestBody MoveOrgRequest request) {
        PermissionGuard.require(permissions, MANAGE_ORG_PERMISSION);
        return ApiResponse.success(iamService.moveOrg(id, request.parentId()));
    }

    @DeleteMapping("/api/hub/v1/admin/orgs/{id}")
    public ApiResponse<Void> deleteOrg(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id) {
        PermissionGuard.require(permissions, MANAGE_ORG_PERMISSION);
        iamService.deleteOrg(id);
        return ApiResponse.success();
    }

    @PostMapping("/api/hub/v1/admin/orgs/{id}/members")
    public ApiResponse<Void> addOrgMember(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @Valid @RequestBody AddOrgMemberRequest request) {
        PermissionGuard.require(permissions, MANAGE_ORG_PERMISSION);
        iamService.addOrgMember(id, request.userId());
        return ApiResponse.success();
    }

    @DeleteMapping("/api/hub/v1/admin/orgs/{id}/members/{userId}")
    public ApiResponse<Void> removeOrgMember(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @PathVariable long userId) {
        PermissionGuard.require(permissions, MANAGE_ORG_PERMISSION);
        iamService.removeOrgMember(id, userId);
        return ApiResponse.success();
    }

    @PutMapping("/api/hub/v1/admin/users/{id}/roles")
    public ApiResponse<UserView> assignRoles(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody AssignRolesRequest request) {
        PermissionGuard.require(permissions, ASSIGN_ROLE_PERMISSION);
        return ApiResponse.success(iamService.assignRoles(id, request));
    }

    @PostMapping("/api/hub/v1/admin/roles")
    public ApiResponse<RoleView> createRole(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @Valid @RequestBody CreateRoleRequest request) {
        PermissionGuard.require(permissions, MANAGE_ROLE_PERMISSION);
        return ApiResponse.success(iamService.createRole(request));
    }

    @GetMapping("/api/hub/v1/admin/roles")
    public ApiResponse<PageResult<RoleView>> listRoles(
            @RequestParam(required = false) String roleType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<RoleView> records = iamService.listRoles(roleType, keyword, page, size);
        return ApiResponse.success(PageResult.of(records, iamService.countRoles(roleType, keyword),
                Math.max(page, 1), Math.min(Math.max(size, 1), 200)));
    }

    @PutMapping("/api/hub/v1/admin/roles/{id}/permissions")
    public ApiResponse<RoleView> grantPermissions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable long id,
            @RequestBody JsonNode body) {
        PermissionGuard.require(permissions, MANAGE_ROLE_PERMISSION);
        return ApiResponse.success(iamService.grantPermissions(id, GrantPermissionsRequest.parse(body)));
    }

    @GetMapping("/api/hub/v1/admin/permissions/tree")
    public ApiResponse<Map<String, List<PermissionNode>>> permissionTree(
            @RequestParam(required = false) String domain) {
        return ApiResponse.success(Map.of("nodes", iamService.permissionTree(domain)));
    }

    /** 契约缺口：文档未定义菜单接口，按平台总纲菜单模型提供扩展接口 */
    @GetMapping("/api/hub/v1/admin/menus")
    public ApiResponse<List<MenuView>> menus(
            @RequestParam(required = false) String clientType,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        return ApiResponse.success(iamService.menus(clientType, permissions));
    }

    /** HUB-INT-102 用户信息（供业务域补全用户信息） */
    @GetMapping("/internal/v1/iam/users/{id}")
    public ApiResponse<UserView> internalGetUser(@PathVariable long id) {
        return ApiResponse.success(iamService.getUser(id));
    }

    /** PLAT-08 内部接口：业务域按账号解析绑定的员工 ID（未绑定返回空字段） */
    @GetMapping("/internal/v1/iam/users/{id}/employee-id")
    public ApiResponse<EmployeeBindingView> internalEmployeeBinding(@PathVariable long id) {
        return ApiResponse.success(new EmployeeBindingView(iamService.getEmployeeId(id)));
    }
}
