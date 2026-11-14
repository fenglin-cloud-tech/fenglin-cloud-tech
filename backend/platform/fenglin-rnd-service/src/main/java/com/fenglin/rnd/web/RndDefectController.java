package com.fenglin.rnd.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.rnd.service.DefectService;
import com.fenglin.rnd.web.dto.RndDtos.DefectCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.DefectTransitionRequest;
import com.fenglin.rnd.web.dto.RndDtos.DefectView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * E1 缺陷接口。
 */
@RestController
@RequestMapping("/api/rnd/v1/defects")
public class RndDefectController {

    private static final String CREATE = "biz:devops:defect:create";
    private static final String SELF_CREATE = "biz:devops:defect:self:create";
    private static final String TRANSITION = "biz:devops:defect:transition";
    private static final String VERIFY = "biz:devops:defect:verify";
    private static final String VIEW = "biz:devops:defect:view";
    private static final String SELF_VIEW = "biz:devops:defect:self:view";

    private final DefectService defectService;

    public RndDefectController(DefectService defectService) {
        this.defectService = defectService;
    }

    @PostMapping
    public ApiResponse<DefectView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody DefectCreateRequest request) {
        PermissionGuard.requireAny(permissions, CREATE, SELF_CREATE);
        return ApiResponse.success(defectService.create(request));
    }

    /**
     * 员工自助：跨产品返回本人上报或被指派的缺陷，范围由服务端按当前登录账号收敛。
     */
    @GetMapping("/my")
    public ApiResponse<List<DefectView>> myDefects(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, SELF_VIEW);
        return ApiResponse.success(defectService.listMine(scope, status, severity, page, size));
    }

    @GetMapping
    public ApiResponse<List<DefectView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(defectService.list(productId, status, severity, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<DefectView> get(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(defectService.get(id));
    }

    @PostMapping("/{id}/transition")
    public ApiResponse<DefectView> transition(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody DefectTransitionRequest request) {
        PermissionGuard.require(permissions, "closed".equalsIgnoreCase(request.status()) ? VERIFY : TRANSITION);
        return ApiResponse.success(defectService.transition(id, request.status()));
    }
}
