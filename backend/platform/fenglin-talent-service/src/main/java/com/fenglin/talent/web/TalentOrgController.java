package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.OrgService;
import com.fenglin.talent.web.dto.TalentDtos.OrgCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.OrgCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.OrgDeleteResult;
import com.fenglin.talent.web.dto.TalentDtos.OrgDetail;
import com.fenglin.talent.web.dto.TalentDtos.OrgMoveRequest;
import com.fenglin.talent.web.dto.TalentDtos.OrgMoveResult;
import com.fenglin.talent.web.dto.TalentDtos.OrgNode;
import com.fenglin.talent.web.dto.TalentDtos.OrgUpdateRequest;
import com.fenglin.talent.web.dto.TalentDtos.OrgUpdateResult;
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

/**
 * 组织架构接口（API-TALENT-ORG-001 ~ 006）。
 */
@RestController
@RequestMapping("/api/talent/v1/orgs")
public class TalentOrgController {

    private static final String TREE = "talent:org:tree";
    private static final String VIEW = "talent:org:view";
    private static final String CREATE = "talent:admin:org:create";
    private static final String UPDATE = "talent:admin:org:update";
    private static final String MOVE = "talent:admin:org:move";
    private static final String DELETE = "talent:admin:org:delete";

    private final OrgService orgService;

    public TalentOrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<OrgNode>> tree(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "rootId", required = false) Long rootId,
            @RequestParam(value = "depth", required = false) Integer depth,
            @RequestParam(value = "includeDisabled", required = false) Boolean includeDisabled) {
        PermissionGuard.require(permissions, TREE);
        return ApiResponse.success(orgService.tree(rootId, depth, includeDisabled));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrgDetail> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(orgService.detail(id));
    }

    @PostMapping
    public ApiResponse<OrgCreateResult> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody OrgCreateRequest request) {
        PermissionGuard.require(permissions, CREATE);
        return ApiResponse.success(orgService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OrgUpdateResult> update(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody OrgUpdateRequest request) {
        PermissionGuard.require(permissions, UPDATE);
        return ApiResponse.success(orgService.update(id, request));
    }

    @PostMapping("/{id}/move")
    public ApiResponse<OrgMoveResult> move(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody OrgMoveRequest request) {
        PermissionGuard.require(permissions, MOVE);
        return ApiResponse.success(orgService.move(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<OrgDeleteResult> delete(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, DELETE);
        return ApiResponse.success(orgService.delete(id));
    }
}
