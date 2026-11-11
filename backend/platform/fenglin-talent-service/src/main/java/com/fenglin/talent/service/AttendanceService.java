package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.mapper.AttendanceMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.web.dto.TalentDtos.AttendanceClockRequest;
import com.fenglin.talent.web.dto.TalentDtos.AttendanceHandleRequest;
import com.fenglin.talent.web.dto.TalentDtos.AttendanceRecordView;
import com.fenglin.talent.web.dto.TalentDtos.AttendanceSummaryView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 考勤：一人一天一条日记录，打卡按租户配置的上下班时间判定迟到/早退。
 * 幂等由唯一索引 (tenant_id, employee_id, work_date) 兜底，重复打卡直接 409。
 */
@Service
public class AttendanceService {

    private static final Set<String> HANDLE_STATUSES = Set.of("NORMAL", "MAKEUP", "ABSENT", "MISSING");

    private final AttendanceMapper attendanceMapper;
    private final TalentAudit audit;
    private final EmployeeBindingService employeeBindingService;
    private final LocalTime workStart;
    private final LocalTime workEnd;

    public AttendanceService(AttendanceMapper attendanceMapper, TalentAudit audit,
                             EmployeeBindingService employeeBindingService,
                             @Value("${fenglin.talent.attendance.work-start:09:00}") String workStart,
                             @Value("${fenglin.talent.attendance.work-end:18:00}") String workEnd) {
        this.attendanceMapper = attendanceMapper;
        this.audit = audit;
        this.employeeBindingService = employeeBindingService;
        this.workStart = LocalTime.parse(workStart);
        this.workEnd = LocalTime.parse(workEnd);
    }

    @Transactional
    public AttendanceRecordView clock(AttendanceClockRequest request) {
        long tenantId = TalentContext.tenantId();
        long employeeId = employeeBindingService.requireCurrentEmployeeId();
        String type = request == null || request.clockType() == null ? "" : request.clockType().toUpperCase();
        if (!"IN".equals(type) && !"OUT".equals(type)) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "打卡类型必须为 IN 或 OUT");
        }
        LocalDate today = LocalDate.now();
        Map<String, Object> existing = attendanceMapper.findDay(tenantId, employeeId, today);

        if ("IN".equals(type)) {
            if (existing != null) {
                throw new BizException(TalentErrorCode.ATTENDANCE_CLOCK_CONFLICT, "今日已打卡上班");
            }
            LocalDateTime now = LocalDateTime.now();
            long lateMinutes = now.toLocalTime().isAfter(workStart)
                    ? Duration.between(workStart, now.toLocalTime()).toMinutes() : 0;
            String status = lateMinutes > 0 ? "LATE" : "NORMAL";
            String source = request.source() == null || request.source().isBlank() ? "WEB" : request.source();
            long id = IdWorker.getId();
            attendanceMapper.insertClockIn(id, tenantId, employeeId, today, OffsetDateTime.now(), source,
                    status, (int) lateMinutes, TalentContext.userId());
            audit.publish("TALENT_ATTENDANCE_CLOCKED_IN", "CREATE", "talent_attendance_record", id, "LOW");
            return toView(attendanceMapper.findById(tenantId, id));
        }

        if (existing == null || Rows.instant(existing, "clockInAt") == null) {
            throw new BizException(TalentErrorCode.ATTENDANCE_CLOCK_CONFLICT, "缺少上班卡，请先打上班卡");
        }
        if (Rows.instant(existing, "clockOutAt") != null) {
            throw new BizException(TalentErrorCode.ATTENDANCE_CLOCK_CONFLICT, "今日已打卡下班");
        }
        OffsetDateTime clockOutAt = OffsetDateTime.now();
        LocalTime clockOutTime = clockOutAt.toLocalTime();
        int earlyMinutes = clockOutTime.isBefore(workEnd)
                ? (int) Duration.between(clockOutTime, workEnd).toMinutes() : 0;
        String previous = Rows.str(existing, "attStatus");
        boolean wasLate = "LATE".equals(previous) || "LATE_EARLY".equals(previous);
        String status = earlyMinutes > 0
                ? (wasLate ? "LATE_EARLY" : "EARLY")
                : (wasLate ? "LATE" : "NORMAL");
        int workMinutes = (int) Duration.between(Rows.instant(existing, "clockInAt"), clockOutAt.toInstant())
                .toMinutes();
        long id = Rows.lngVal(existing, "id");
        attendanceMapper.updateClockOut(tenantId, id, clockOutAt, status, earlyMinutes,
                Math.max(workMinutes, 0), TalentContext.userId());
        audit.publish("TALENT_ATTENDANCE_CLOCKED_OUT", "UPDATE", "talent_attendance_record", id, "LOW");
        return toView(attendanceMapper.findById(tenantId, id));
    }

    @Transactional(readOnly = true)
    public PageResult<AttendanceRecordView> list(Long employeeId, LocalDate from, LocalDate to, String status,
                                                 Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<AttendanceRecordView> records = attendanceMapper
                .list(tenantId, employeeId, from, to, blankToNull(status), pageSize, (pageNo - 1) * pageSize)
                .stream().map(this::toView).toList();
        long total = attendanceMapper.count(tenantId, employeeId, from, to, blankToNull(status));
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public PageResult<AttendanceRecordView> exceptions(LocalDate from, LocalDate to, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<AttendanceRecordView> records = attendanceMapper
                .listExceptions(tenantId, from, to, pageSize, (pageNo - 1) * pageSize)
                .stream().map(this::toView).toList();
        return PageResult.of(records, attendanceMapper.countExceptions(tenantId, from, to), pageNo, pageSize);
    }

    @Transactional
    public AttendanceRecordView handle(long id, AttendanceHandleRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.status() == null || !HANDLE_STATUSES.contains(request.status())) {
            throw new BizException(TalentErrorCode.ATTENDANCE_HANDLE_INVALID,
                    "处理状态必须为 NORMAL/MAKEUP/ABSENT/MISSING");
        }
        Map<String, Object> current = attendanceMapper.findById(tenantId, id);
        if (current == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "考勤记录不存在");
        }
        attendanceMapper.handleException(tenantId, id, request.status(), request.note(), userId);
        audit.publish("TALENT_ATTENDANCE_HANDLED", "UPDATE", "talent_attendance_record", id, "MEDIUM");
        return toView(attendanceMapper.findById(tenantId, id));
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryView summary(Long employeeId, YearMonth month) {
        long tenantId = TalentContext.tenantId();
        return toSummary(attendanceMapper.summary(tenantId, employeeId, month.atDay(1), month.atEndOfMonth()));
    }

    /**
     * 员工自助逐日明细：只按当前登录账号绑定的员工过滤，不接受外部传入 employeeId。
     */
    @Transactional(readOnly = true)
    public PageResult<AttendanceRecordView> myRecords(LocalDate from, LocalDate to, String status,
                                                      Integer page, Integer size) {
        Long employeeId = employeeBindingService.currentEmployeeIdOrNull();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        if (employeeId == null) {
            return PageResult.of(List.of(), 0, pageNo, pageSize);
        }
        return list(employeeId, from, to, status, pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryView mySummary(YearMonth month) {
        Long employeeId = employeeBindingService.currentEmployeeIdOrNull();
        if (employeeId == null) {
            return toSummary(null);
        }
        return summary(employeeId, month);
    }

    private AttendanceSummaryView toSummary(Map<String, Object> row) {
        if (row == null) {
            return new AttendanceSummaryView(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return new AttendanceSummaryView(Rows.lngVal(row, "normalDays"), Rows.lngVal(row, "lateDays"),
                Rows.lngVal(row, "earlyDays"), Rows.lngVal(row, "lateEarlyDays"),
                Rows.lngVal(row, "missingDays"), Rows.lngVal(row, "absentDays"),
                Rows.lngVal(row, "workMinutes"), Rows.lngVal(row, "lateMinutes"),
                Rows.lngVal(row, "earlyMinutes"));
    }

    private AttendanceRecordView toView(Map<String, Object> row) {
        return new AttendanceRecordView(Rows.lngVal(row, "id"), Rows.lngVal(row, "employeeId"),
                Rows.date(row, "workDate"), Rows.instant(row, "clockInAt"), Rows.instant(row, "clockOutAt"),
                Rows.str(row, "source"), Rows.str(row, "attStatus"), Rows.intg(row, "workMinutes"),
                Rows.intg(row, "lateMinutes"), Rows.intg(row, "earlyMinutes"),
                Rows.str(row, "exceptionNote"), Rows.intg(row, "version"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
