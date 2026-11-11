package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.AttendanceService;
import com.fenglin.talent.web.dto.TalentDtos.AttendanceClockRequest;
import com.fenglin.talent.web.dto.TalentDtos.AttendanceHandleRequest;
import com.fenglin.talent.web.dto.TalentDtos.AttendanceRecordView;
import com.fenglin.talent.web.dto.TalentDtos.AttendanceSummaryView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 考勤接口：员工打卡 / 我的考勤、管理端记录、异常处理与月度统计。
 */
@RestController
@RequestMapping("/api/talent/v1/attendance")
public class TalentAttendanceController {

    private static final String ADMIN_LIST = "talent:admin:attendance:list";
    private static final String ADMIN_MANAGE = "talent:admin:attendance:manage";
    private static final String CLOCK = "talent:attendance:clock";
    private static final String VIEW = "talent:attendance:view";
    private static final String SELF_VIEW = "talent:self:attendance:view";

    private final AttendanceService attendanceService;

    public TalentAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/clock")
    public ApiResponse<AttendanceRecordView> clock(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody(required = false) AttendanceClockRequest request) {
        PermissionGuard.require(permissions, CLOCK);
        return ApiResponse.success(attendanceService.clock(request));
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<AttendanceRecordView>> records(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, ADMIN_LIST);
        return ApiResponse.success(attendanceService.list(employeeId, from, to, status, page, size));
    }

    @GetMapping("/exceptions")
    public ApiResponse<PageResult<AttendanceRecordView>> exceptions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, ADMIN_LIST);
        return ApiResponse.success(attendanceService.exceptions(from, to, page, size));
    }

    @PostMapping("/records/{id}/handle")
    public ApiResponse<AttendanceRecordView> handle(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody AttendanceHandleRequest request) {
        PermissionGuard.require(permissions, ADMIN_MANAGE);
        return ApiResponse.success(attendanceService.handle(id, request));
    }

    @GetMapping("/summary")
    public ApiResponse<AttendanceSummaryView> summary(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "employeeId", required = false) Long employeeId) {
        PermissionGuard.require(permissions, ADMIN_LIST);
        return ApiResponse.success(attendanceService.summary(employeeId, parseMonth(month)));
    }

    @GetMapping("/my/records")
    public ApiResponse<PageResult<AttendanceRecordView>> myRecords(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.requireAny(permissions, VIEW, SELF_VIEW);
        return ApiResponse.success(attendanceService.myRecords(from, to, status, page, size));
    }

    @GetMapping("/my")
    public ApiResponse<AttendanceSummaryView> my(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "month", required = false) String month) {
        PermissionGuard.requireAny(permissions, VIEW, SELF_VIEW);
        return ApiResponse.success(attendanceService.mySummary(parseMonth(month)));
    }

    private YearMonth parseMonth(String month) {
        return month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month.trim());
    }
}
