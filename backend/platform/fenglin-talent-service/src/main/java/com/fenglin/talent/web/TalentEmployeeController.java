package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.EmployeeService;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeCreateRequest;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeCreateResult;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeDetail;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeListItem;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeImportRequest;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeImportResult;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeUpdateRequest;
import com.fenglin.talent.web.dto.TalentDtos.EmployeeUpdateResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 员工主数据接口（API-012 基础信息）。
 */
@RestController
@RequestMapping("/api/talent/v1/employees")
public class TalentEmployeeController {

    private static final String LIST = "talent:admin:employee:list";
    private static final String VIEW = "talent:employee:view";
    private static final String SELF_VIEW = "talent:self:profile:view";
    private static final String CREATE = "talent:admin:employee:create";
    private static final String UPDATE = "talent:admin:employee:update";
    private static final String IMPORT = "talent:admin:employee:import";
    private static final String EXPORT = "talent:admin:employee:export";

    private final EmployeeService employeeService;

    public TalentEmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public ApiResponse<PageResult<EmployeeListItem>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "includeSubOrg", required = false) Boolean includeSubOrg,
            @RequestParam(value = "empStatus", required = false) String empStatus,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, LIST);
        return ApiResponse.success(employeeService.list(orgId, includeSubOrg, empStatus, keyword, page, size));
    }

    /** 员工自助：本人档案，按账号绑定的员工收敛。 */
    @GetMapping("/my")
    public ApiResponse<EmployeeDetail> myProfile(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, SELF_VIEW);
        return ApiResponse.success(employeeService.myProfile());
    }

    @GetMapping("/{id}")
    public ApiResponse<EmployeeDetail> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, VIEW);
        return ApiResponse.success(employeeService.detail(id));
    }

    @PostMapping
    public ApiResponse<EmployeeCreateResult> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody EmployeeCreateRequest request) {
        PermissionGuard.require(permissions, CREATE);
        return ApiResponse.success(employeeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<EmployeeUpdateResult> update(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody EmployeeUpdateRequest request) {
        PermissionGuard.require(permissions, UPDATE);
        return ApiResponse.success(employeeService.update(id, request));
    }

    @PostMapping("/import")
    public ApiResponse<EmployeeImportResult> importEmployees(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody EmployeeImportRequest request) {
        PermissionGuard.require(permissions, IMPORT);
        return ApiResponse.success(employeeService.importEmployees(request));
    }

    @GetMapping(value = "/roster/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportRoster(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "includeSubOrg", required = false) Boolean includeSubOrg,
            @RequestParam(value = "empStatus", required = false) String empStatus) {
        PermissionGuard.require(permissions, EXPORT);
        String csv = employeeService.rosterCsv(orgId, includeSubOrg, empStatus);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"roster.csv\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
