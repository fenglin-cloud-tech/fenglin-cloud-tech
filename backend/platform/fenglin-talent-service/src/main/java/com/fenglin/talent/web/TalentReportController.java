package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.EmployeeService;
import com.fenglin.talent.web.dto.TalentDtos.HeadcountReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人员结构报表。
 */
@RestController
@RequestMapping("/api/talent/v1/reports")
public class TalentReportController {

    private static final String EXPORT = "talent:admin:employee:export";

    private final EmployeeService employeeService;

    public TalentReportController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/headcount")
    public ApiResponse<HeadcountReport> headcount(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "includeSubOrg", required = false) Boolean includeSubOrg) {
        PermissionGuard.require(permissions, EXPORT);
        return ApiResponse.success(employeeService.headcountReport(orgId, includeSubOrg));
    }
}
