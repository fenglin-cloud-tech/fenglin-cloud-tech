package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 入转调离 Mapper：入职、试用期、异动、离职。
 */
@Mapper
public interface LifecycleMapper {

    // ---------- 入职 ----------

    @Insert("""
            INSERT INTO talent_onboarding
                (id, tenant_id, employee_id, offer_id, onboard_date, ob_status, checklist, mentor_id, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{offerId,jdbcType=BIGINT}, #{onboardDate}, 'PENDING',
                    CAST(#{checklist,jdbcType=VARCHAR} AS jsonb), #{mentorId,jdbcType=BIGINT}, #{userId})
            """)
    void insertOnboarding(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("employeeId") long employeeId, @Param("offerId") Long offerId,
                          @Param("onboardDate") LocalDate onboardDate, @Param("checklist") String checklist,
                          @Param("mentorId") Long mentorId, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", employee_id AS "employeeId", offer_id AS "offerId",
                   to_char(onboard_date, 'YYYY-MM-DD') AS "onboardDate", ob_status AS "obStatus",
                   version AS "version"
            FROM talent_onboarding
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findOnboarding(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_onboarding
            SET ob_status = #{obStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateOnboardingStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                                @Param("obStatus") String obStatus, @Param("userId") long userId);

    // ---------- 试用期 ----------

    @Insert("""
            INSERT INTO talent_probation
                (id, tenant_id, employee_id, end_date, prob_status, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{endDate}, 'ONGOING', #{userId})
            """)
    void insertProbation(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("employeeId") long employeeId, @Param("endDate") LocalDate endDate,
                         @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", employee_id AS "employeeId", to_char(end_date, 'YYYY-MM-DD') AS "endDate",
                   prob_status AS "probStatus", version AS "version"
            FROM talent_probation
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findProbation(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_probation
            SET end_date = #{endDate}, evaluation = #{evaluation,jdbcType=VARCHAR}, prob_status = #{probStatus},
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateProbation(@Param("tenantId") long tenantId, @Param("id") long id, @Param("endDate") LocalDate endDate,
                         @Param("evaluation") String evaluation, @Param("probStatus") String probStatus,
                         @Param("userId") long userId);

    // ---------- 异动 ----------

    @Insert("""
            INSERT INTO talent_transfer
                (id, tenant_id, employee_id, transfer_type, before_json, after_json, effective_date, reason,
                 tf_status, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{transferType},
                    CAST(#{beforeJson,jdbcType=VARCHAR} AS jsonb), CAST(#{afterJson,jdbcType=VARCHAR} AS jsonb),
                    #{effectiveDate}, #{reason,jdbcType=VARCHAR}, 'APPROVING', #{userId})
            """)
    void insertTransfer(@Param("id") long id, @Param("tenantId") long tenantId, @Param("employeeId") long employeeId,
                        @Param("transferType") String transferType, @Param("beforeJson") String beforeJson,
                        @Param("afterJson") String afterJson, @Param("effectiveDate") LocalDate effectiveDate,
                        @Param("reason") String reason, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", employee_id AS "employeeId", transfer_type AS "transferType",
                   before_json::text AS "beforeJson", after_json::text AS "afterJson",
                   to_char(effective_date, 'YYYY-MM-DD') AS "effectiveDate", tf_status AS "tfStatus",
                   version AS "version"
            FROM talent_transfer
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findTransfer(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_transfer
            SET tf_status = #{tfStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateTransferStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                              @Param("tfStatus") String tfStatus, @Param("userId") long userId);

    // ---------- 离职 ----------

    @Insert("""
            INSERT INTO talent_resignation
                (id, tenant_id, employee_id, resign_type, apply_date, last_work_date, reason_category,
                 handover_status, rs_status, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{resignType}, #{applyDate}, #{lastWorkDate},
                    #{reasonCategory,jdbcType=VARCHAR}, 'PENDING', 'APPROVING', #{userId})
            """)
    void insertResignation(@Param("id") long id, @Param("tenantId") long tenantId,
                           @Param("employeeId") long employeeId, @Param("resignType") String resignType,
                           @Param("applyDate") LocalDate applyDate, @Param("lastWorkDate") LocalDate lastWorkDate,
                           @Param("reasonCategory") String reasonCategory, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", employee_id AS "employeeId", resign_type AS "resignType",
                   to_char(apply_date, 'YYYY-MM-DD') AS "applyDate",
                   to_char(last_work_date, 'YYYY-MM-DD') AS "lastWorkDate", rs_status AS "rsStatus",
                   version AS "version"
            FROM talent_resignation
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findResignation(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT count(1) FROM talent_resignation
            WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId}
              AND rs_status = 'APPROVING' AND deleted = 0
            """)
    long countApprovingResignation(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId);

    @Update("""
            UPDATE talent_resignation
            SET rs_status = #{rsStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateResignationStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                                 @Param("rsStatus") String rsStatus, @Param("userId") long userId);

    // ---------- 单据列表 ----------

    @Select("""
            <script>
            SELECT o.id AS "id", o.employee_id AS "employeeId", e.name AS "employeeName",
                   o.offer_id AS "offerId", to_char(o.onboard_date, 'YYYY-MM-DD') AS "onboardDate",
                   o.ob_status AS "obStatus", o.checklist::text AS "checklist", o.mentor_id AS "mentorId",
                   extract(epoch FROM o.created_at) * 1000 AS "createdAt"
            FROM talent_onboarding o
            LEFT JOIN talent_employee e ON e.id = o.employee_id AND e.tenant_id = o.tenant_id AND e.deleted = 0
            WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
              AND (CAST(#{obStatus,jdbcType=VARCHAR} AS text) IS NULL OR o.ob_status = #{obStatus})
            ORDER BY o.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listOnboardings(@Param("tenantId") long tenantId,
                                              @Param("obStatus") String obStatus,
                                              @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_onboarding o
            WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
              AND (CAST(#{obStatus,jdbcType=VARCHAR} AS text) IS NULL OR o.ob_status = #{obStatus})
            """)
    long countOnboardings(@Param("tenantId") long tenantId, @Param("obStatus") String obStatus);

    @Select("""
            <script>
            SELECT t.id AS "id", t.employee_id AS "employeeId", e.name AS "employeeName",
                   t.transfer_type AS "transferType", t.before_json::text AS "beforeJson",
                   t.after_json::text AS "afterJson", to_char(t.effective_date, 'YYYY-MM-DD') AS "effectiveDate",
                   t.reason AS "reason", t.tf_status AS "tfStatus",
                   extract(epoch FROM t.created_at) * 1000 AS "createdAt"
            FROM talent_transfer t
            LEFT JOIN talent_employee e ON e.id = t.employee_id AND e.tenant_id = t.tenant_id AND e.deleted = 0
            WHERE t.tenant_id = #{tenantId} AND t.deleted = 0
              AND (CAST(#{tfStatus,jdbcType=VARCHAR} AS text) IS NULL OR t.tf_status = #{tfStatus})
            ORDER BY t.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listTransfers(@Param("tenantId") long tenantId,
                                            @Param("tfStatus") String tfStatus,
                                            @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_transfer t
            WHERE t.tenant_id = #{tenantId} AND t.deleted = 0
              AND (CAST(#{tfStatus,jdbcType=VARCHAR} AS text) IS NULL OR t.tf_status = #{tfStatus})
            """)
    long countTransfers(@Param("tenantId") long tenantId, @Param("tfStatus") String tfStatus);

    @Select("""
            <script>
            SELECT r.id AS "id", r.employee_id AS "employeeId", e.name AS "employeeName",
                   r.resign_type AS "resignType", to_char(r.apply_date, 'YYYY-MM-DD') AS "applyDate",
                   to_char(r.last_work_date, 'YYYY-MM-DD') AS "lastWorkDate",
                   r.reason_category AS "reasonCategory", r.handover_status AS "handoverStatus",
                   r.rs_status AS "rsStatus", extract(epoch FROM r.created_at) * 1000 AS "createdAt"
            FROM talent_resignation r
            LEFT JOIN talent_employee e ON e.id = r.employee_id AND e.tenant_id = r.tenant_id AND e.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
              AND (CAST(#{rsStatus,jdbcType=VARCHAR} AS text) IS NULL OR r.rs_status = #{rsStatus})
            ORDER BY r.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listResignations(@Param("tenantId") long tenantId,
                                               @Param("rsStatus") String rsStatus,
                                               @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_resignation r
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
              AND (CAST(#{rsStatus,jdbcType=VARCHAR} AS text) IS NULL OR r.rs_status = #{rsStatus})
            """)
    long countResignations(@Param("tenantId") long tenantId, @Param("rsStatus") String rsStatus);
}
