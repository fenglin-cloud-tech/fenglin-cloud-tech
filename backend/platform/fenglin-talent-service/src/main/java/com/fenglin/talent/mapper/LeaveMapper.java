package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 假期类型 / 额度 / 申请单访问。额度扣减使用 version 乐观锁。
 */
@Mapper
public interface LeaveMapper {

    // ---------- 假期类型 ----------

    @Select("""
            SELECT id AS "id", type_code AS "typeCode", type_name AS "typeName",
                   annual_quota_days AS "annualQuotaDays", paid AS "paid"
            FROM fenglin_talent.talent_leave_type
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 'ACTIVE'
            ORDER BY sort_no, id
            """)
    List<Map<String, Object>> listTypes(@Param("tenantId") long tenantId);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_leave_type
            WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    long countTypes(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id AS "id", type_code AS "typeCode", type_name AS "typeName",
                   annual_quota_days AS "annualQuotaDays", paid AS "paid"
            FROM fenglin_talent.talent_leave_type
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0 AND status = 'ACTIVE'
            """)
    Map<String, Object> findType(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO fenglin_talent.talent_leave_type
                (id, tenant_id, type_code, type_name, annual_quota_days, paid, sort_no, created_by)
            VALUES (#{id}, #{tenantId}, #{typeCode}, #{typeName}, #{quotaDays}, #{paid}, #{sortNo}, #{userId})
            ON CONFLICT DO NOTHING
            """)
    void insertType(@Param("id") long id, @Param("tenantId") long tenantId,
                    @Param("typeCode") String typeCode, @Param("typeName") String typeName,
                    @Param("quotaDays") BigDecimal quotaDays, @Param("paid") int paid,
                    @Param("sortNo") int sortNo, @Param("userId") long userId);

    // ---------- 年度额度 ----------

    @Select("""
            SELECT id AS "id", leave_type_id AS "leaveTypeId", balance_year AS "balanceYear",
                   total_days AS "totalDays", used_days AS "usedDays", version AS "version"
            FROM fenglin_talent.talent_leave_balance
            WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND leave_type_id = #{leaveTypeId}
              AND balance_year = #{year} AND deleted = 0
            """)
    Map<String, Object> findBalance(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId,
                                    @Param("leaveTypeId") long leaveTypeId, @Param("year") int year);

    @Insert("""
            INSERT INTO fenglin_talent.talent_leave_balance
                (id, tenant_id, employee_id, leave_type_id, balance_year, total_days, used_days, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{leaveTypeId}, #{year}, #{totalDays}, 0, #{userId})
            ON CONFLICT DO NOTHING
            """)
    void insertBalance(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("employeeId") long employeeId, @Param("leaveTypeId") long leaveTypeId,
                       @Param("year") int year, @Param("totalDays") BigDecimal totalDays,
                       @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_leave_balance
            SET used_days = #{usedDays}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND version = #{version} AND deleted = 0
            """)
    int updateUsed(@Param("tenantId") long tenantId, @Param("id") long id,
                   @Param("usedDays") BigDecimal usedDays, @Param("version") int version,
                   @Param("userId") long userId);

    // ---------- 申请单 ----------

    @Select("""
            SELECT 'LV' || to_char(now(), 'YYYYMM')
                   || lpad(nextval('fenglin_talent.talent_leave_no_seq')::text, 6, '0')
            """)
    String nextRequestNo();

    @Select("""
            SELECT id AS "id", request_no AS "requestNo", employee_id AS "employeeId", leave_type_id AS "leaveTypeId",
                   to_char(start_date, 'YYYY-MM-DD') AS "startDate", to_char(end_date, 'YYYY-MM-DD') AS "endDate",
                   days AS "days", reason AS "reason", lr_status AS "lrStatus",
                   process_instance_id AS "processInstanceId", idempotency_key AS "idempotencyKey",
                   approve_opinion AS "approveOpinion", version AS "version"
            FROM fenglin_talent.talent_leave_request
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findRequest(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", request_no AS "requestNo", employee_id AS "employeeId", leave_type_id AS "leaveTypeId",
                   to_char(start_date, 'YYYY-MM-DD') AS "startDate", days AS "days", lr_status AS "lrStatus",
                   process_instance_id AS "processInstanceId", idempotency_key AS "idempotencyKey"
            FROM fenglin_talent.talent_leave_request
            WHERE tenant_id = #{tenantId} AND idempotency_key = #{key} AND deleted = 0
            """)
    Map<String, Object> findRequestByKey(@Param("tenantId") long tenantId, @Param("key") String key);

    @Insert("""
            INSERT INTO fenglin_talent.talent_leave_request
                (id, tenant_id, request_no, employee_id, leave_type_id, start_date, end_date, days, reason,
                 lr_status, process_instance_id, idempotency_key, created_by)
            VALUES (#{id}, #{tenantId}, #{requestNo}, #{employeeId}, #{leaveTypeId}, #{startDate}, #{endDate},
                    #{days}, #{reason}, 'APPROVING', #{processInstanceId}, #{idempotencyKey}, #{userId})
            """)
    void insertRequest(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("requestNo") String requestNo, @Param("employeeId") long employeeId,
                       @Param("leaveTypeId") long leaveTypeId, @Param("startDate") LocalDate startDate,
                       @Param("endDate") LocalDate endDate, @Param("days") BigDecimal days,
                       @Param("reason") String reason, @Param("processInstanceId") String processInstanceId,
                       @Param("idempotencyKey") String idempotencyKey, @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_leave_request
            SET lr_status = #{status}, approved_by = #{userId}, approved_at = now(),
                approve_opinion = #{opinion}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND lr_status = 'APPROVING' AND deleted = 0
            """)
    int updateApproval(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                       @Param("opinion") String opinion, @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_leave_request
            SET lr_status = 'CANCELED', updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
              AND lr_status IN ('APPROVING', 'APPROVED')
            """)
    int cancel(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT r.id AS "id", r.request_no AS "requestNo", r.employee_id AS "employeeId",
                   e.name AS "employeeName", t.type_name AS "leaveTypeName",
                   to_char(r.start_date, 'YYYY-MM-DD') AS "startDate",
                   to_char(r.end_date, 'YYYY-MM-DD') AS "endDate", r.days AS "days",
                   r.lr_status AS "lrStatus", r.approve_opinion AS "approveOpinion",
                   extract(epoch FROM r.created_at) * 1000 AS "createdAt"
            FROM fenglin_talent.talent_leave_request r
            LEFT JOIN fenglin_talent.talent_employee e ON e.id = r.employee_id AND e.tenant_id = r.tenant_id
                AND e.deleted = 0
            JOIN fenglin_talent.talent_leave_type t ON t.id = r.leave_type_id AND t.tenant_id = r.tenant_id
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
            <if test="employeeId != null"> AND r.employee_id = #{employeeId}</if>
            <if test="status != null"> AND r.lr_status = #{status}</if>
            ORDER BY r.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listRequests(@Param("tenantId") long tenantId,
                                           @Param("employeeId") Long employeeId,
                                           @Param("status") String status,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(1) FROM fenglin_talent.talent_leave_request r
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
            <if test="employeeId != null"> AND r.employee_id = #{employeeId}</if>
            <if test="status != null"> AND r.lr_status = #{status}</if>
            </script>
            """)
    long countRequests(@Param("tenantId") long tenantId, @Param("employeeId") Long employeeId,
                       @Param("status") String status);
}
