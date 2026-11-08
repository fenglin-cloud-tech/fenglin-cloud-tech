package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.AdminService;
import com.fenglin.talent.web.dto.TalentDtos.GradeView;
import com.fenglin.talent.web.dto.TalentDtos.HeadcountPlanCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.HeadcountPlanCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.PositionCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.PositionCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.PositionView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 岗位、职级与编制计划接口。
 */
@RestController
@RequestMapping("/api/talent/v1")
public class TalentAdminController {

    private static final String POSITION_LIST = "talent:admin:position:list";
    private static final String POSITION_CREATE = "talent:admin:position:create";
    private static final String GRADE_LIST = "talent:admin:grade:list";
    private static final String HEADCOUNT_CREATE = "talent:admin:headcount:create";

    private final AdminService adminService;

    public TalentAdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/positions")
    public ApiResponse<PageResult<PositionView>> positions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, POSITION_LIST);
        return ApiResponse.success(adminService.listPositions(page, size));
    }

    @PostMapping("/positions")
    public ApiResponse<PositionCreateResult> createPosition(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody PositionCreateRequest request) {
        PermissionGuard.require(permissions, POSITION_CREATE);
        return ApiResponse.success(adminService.createPosition(request));
    }

    @GetMapping("/grades")
    public ApiResponse<List<GradeView>> grades(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, GRADE_LIST);
        return ApiResponse.success(adminService.listGrades());
    }

    @PostMapping("/headcount-plans")
    public ApiResponse<HeadcountPlanCreateResult> createHeadcountPlan(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody HeadcountPlanCreateRequest request) {
        PermissionGuard.require(permissions, HEADCOUNT_CREATE);
        return ApiResponse.success(adminService.createHeadcountPlan(request));
    }
}
