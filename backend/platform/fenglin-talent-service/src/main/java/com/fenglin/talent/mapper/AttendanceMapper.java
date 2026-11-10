package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 考勤日记录访问。所有查询显式带 tenant_id，RLS 再兜一层。
 */
@Mapper
public interface AttendanceMapper {

    String COLUMNS = """
            SELECT id AS "id", employee_id AS "employeeId", to_char(work_date, 'YYYY-MM-DD') AS "workDate",
                   extract(epoch FROM clock_in_at) * 1000 AS "clockInAt",
                   extract(epoch FROM clock_out_at) * 1000 AS "clockOutAt",
                   source AS "source", att_status AS "attStatus", work_minutes AS "workMinutes",
                   late_minutes AS "lateMinutes", early_minutes AS "earlyMinutes",
                   exception_note AS "exceptionNote", version AS "version"
            FROM fenglin_talent.talent_attendance_record
            WHERE tenant_id = #{tenantId} AND deleted = 0
            """;

    @Select(COLUMNS + " AND employee_id = #{employeeId} AND work_date = #{workDate}")
    Map<String, Object> findDay(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId,
                                @Param("workDate") LocalDate workDate);

    @Select(COLUMNS + " AND id = #{id}")
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO fenglin_talent.talent_attendance_record
                (id, tenant_id, employee_id, work_date, clock_in_at, source, att_status, late_minutes, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{workDate}, #{clockInAt}, #{source}, #{attStatus},
                    #{lateMinutes}, #{userId})
            """)
    void insertClockIn(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("employeeId") long employeeId, @Param("workDate") LocalDate workDate,
                       @Param("clockInAt") OffsetDateTime clockInAt, @Param("source") String source,
                       @Param("attStatus") String attStatus, @Param("lateMinutes") int lateMinutes,
                       @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_attendance_record
            SET clock_out_at = #{clockOutAt}, att_status = #{attStatus}, early_minutes = #{earlyMinutes},
                work_minutes = #{workMinutes}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateClockOut(@Param("tenantId") long tenantId, @Param("id") long id,
                       @Param("clockOutAt") OffsetDateTime clockOutAt,
                       @Param("attStatus") String attStatus, @Param("earlyMinutes") int earlyMinutes,
                       @Param("workMinutes") int workMinutes, @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_attendance_record
            SET att_status = #{status}, exception_note = #{note}, handled_by = #{userId}, handled_at = now(),
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int handleException(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                        @Param("note") String note, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id AS "id", employee_id AS "employeeId", to_char(work_date, 'YYYY-MM-DD') AS "workDate",
                   extract(epoch FROM clock_in_at) * 1000 AS "clockInAt",
                   extract(epoch FROM clock_out_at) * 1000 AS "clockOutAt",
                   source AS "source", att_status AS "attStatus", work_minutes AS "workMinutes",
                   late_minutes AS "lateMinutes", early_minutes AS "earlyMinutes",
                   exception_note AS "exceptionNote", version AS "version"
            FROM fenglin_talent.talent_attendance_record
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="employeeId != null"> AND employee_id = #{employeeId}</if>
            <if test="from != null"> AND work_date &gt;= #{from}</if>
            <if test="to != null"> AND work_date &lt;= #{to}</if>
            <if test="status != null"> AND att_status = #{status}</if>
            ORDER BY work_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("employeeId") Long employeeId,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to,
                                   @Param("status") String status, @Param("limit") int limit,
                                   @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(1) FROM fenglin_talent.talent_attendance_record
            WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="employeeId != null"> AND employee_id = #{employeeId}</if>
            <if test="from != null"> AND work_date &gt;= #{from}</if>
            <if test="to != null"> AND work_date &lt;= #{to}</if>
            <if test="status != null"> AND att_status = #{status}</if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId, @Param("employeeId") Long employeeId,
               @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("status") String status);

    @Select("""
            <script>
            SELECT id AS "id", employee_id AS "employeeId", to_char(work_date, 'YYYY-MM-DD') AS "workDate",
                   extract(epoch FROM clock_in_at) * 1000 AS "clockInAt",
                   extract(epoch FROM clock_out_at) * 1000 AS "clockOutAt",
                   source AS "source", att_status AS "attStatus", work_minutes AS "workMinutes",
                   late_minutes AS "lateMinutes", early_minutes AS "earlyMinutes",
                   exception_note AS "exceptionNote", version AS "version"
            FROM fenglin_talent.talent_attendance_record
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND att_status IN ('LATE', 'EARLY', 'LATE_EARLY', 'MISSING', 'ABSENT')
            <if test="from != null"> AND work_date &gt;= #{from}</if>
            <if test="to != null"> AND work_date &lt;= #{to}</if>
            ORDER BY work_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listExceptions(@Param("tenantId") long tenantId, @Param("from") LocalDate from,
                                             @Param("to") LocalDate to, @Param("limit") int limit,
                                             @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(1) FROM fenglin_talent.talent_attendance_record
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND att_status IN ('LATE', 'EARLY', 'LATE_EARLY', 'MISSING', 'ABSENT')
            <if test="from != null"> AND work_date &gt;= #{from}</if>
            <if test="to != null"> AND work_date &lt;= #{to}</if>
            </script>
            """)
    long countExceptions(@Param("tenantId") long tenantId, @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    @Select("""
            <script>
            SELECT count(*) FILTER (WHERE att_status IN ('NORMAL', 'MAKEUP')) AS "normalDays",
                   count(*) FILTER (WHERE att_status = 'LATE') AS "lateDays",
                   count(*) FILTER (WHERE att_status = 'EARLY') AS "earlyDays",
                   count(*) FILTER (WHERE att_status = 'LATE_EARLY') AS "lateEarlyDays",
                   count(*) FILTER (WHERE att_status = 'MISSING') AS "missingDays",
                   count(*) FILTER (WHERE att_status = 'ABSENT') AS "absentDays",
                   coalesce(sum(work_minutes), 0) AS "workMinutes",
                   coalesce(sum(late_minutes), 0) AS "lateMinutes",
                   coalesce(sum(early_minutes), 0) AS "earlyMinutes"
            FROM fenglin_talent.talent_attendance_record
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND work_date BETWEEN #{from} AND #{to}
            <if test="employeeId != null"> AND employee_id = #{employeeId}</if>
            </script>
            """)
    Map<String, Object> summary(@Param("tenantId") long tenantId, @Param("employeeId") Long employeeId,
                                @Param("from") LocalDate from, @Param("to") LocalDate to);
}
