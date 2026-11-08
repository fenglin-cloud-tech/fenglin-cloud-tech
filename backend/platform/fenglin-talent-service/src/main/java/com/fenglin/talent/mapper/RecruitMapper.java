package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 招聘域 Mapper：需求、候选人、应聘、面试、Offer。PII 仅存密文与哈希。
 */
@Mapper
public interface RecruitMapper {

    // ---------- 招聘需求 ----------

    @Select("""
            SELECT 'TAL' || to_char(now(), 'YYYYMM') || lpad(nextval('talent_req_no_seq')::text, 6, '0')
            """)
    String nextReqNo();

    @Insert("""
            INSERT INTO talent_recruit_requisition
                (id, tenant_id, req_no, position_id, org_id, headcount, salary_min, salary_max, urgency,
                 reason_type, owner_id, expect_date, req_status, description, requirements, created_by)
            VALUES (#{id}, #{tenantId}, #{reqNo}, #{positionId}, #{orgId}, #{headcount},
                    #{salaryMin,jdbcType=NUMERIC}, #{salaryMax,jdbcType=NUMERIC}, #{urgency}, #{reasonType},
                    #{ownerId}, #{expectDate,jdbcType=DATE}, 'DRAFT', #{description,jdbcType=VARCHAR},
                    #{requirements,jdbcType=VARCHAR}, #{userId})
            """)
    void insertRequisition(@Param("id") long id, @Param("tenantId") long tenantId, @Param("reqNo") String reqNo,
                           @Param("positionId") long positionId, @Param("orgId") long orgId,
                           @Param("headcount") int headcount, @Param("salaryMin") BigDecimal salaryMin,
                           @Param("salaryMax") BigDecimal salaryMax, @Param("urgency") String urgency,
                           @Param("reasonType") String reasonType, @Param("ownerId") long ownerId,
                           @Param("expectDate") LocalDate expectDate, @Param("description") String description,
                           @Param("requirements") String requirements, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", req_no AS "reqNo", position_id AS "positionId", org_id AS "orgId",
                   headcount AS "headcount", owner_id AS "ownerId", req_status AS "reqStatus",
                   version AS "version"
            FROM talent_recruit_requisition
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findRequisition(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT coalesce(sum(headcount), 0) FROM talent_recruit_requisition
            WHERE tenant_id = #{tenantId} AND org_id = #{orgId}
              AND (#{positionId,jdbcType=BIGINT} IS NULL OR position_id = #{positionId})
              AND extract(year FROM created_at) = #{planYear}
              AND req_status IN ('DRAFT', 'APPROVING', 'RECRUITING', 'PAUSED') AND deleted = 0
            """)
    long sumCommittedHeadcount(@Param("tenantId") long tenantId, @Param("orgId") long orgId,
                               @Param("positionId") Long positionId, @Param("planYear") int planYear);

    @Select("""
            SELECT r.id AS "id", r.req_no AS "reqNo", p.position_name AS "positionName",
                   o.org_name AS "orgName", r.headcount AS "headcount", r.req_status AS "reqStatus",
                   r.owner_id AS "ownerId", e.name AS "ownerName",
                   extract(epoch FROM r.created_at) * 1000 AS "createdAt"
            FROM talent_recruit_requisition r
            JOIN talent_org o ON o.id = r.org_id AND o.tenant_id = r.tenant_id
            LEFT JOIN talent_position p ON p.id = r.position_id AND p.tenant_id = r.tenant_id
            LEFT JOIN talent_employee e ON e.id = r.owner_id AND e.tenant_id = r.tenant_id
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
              AND (CAST(#{reqStatus,jdbcType=VARCHAR} AS text) IS NULL OR r.req_status = #{reqStatus})
              AND (#{orgId,jdbcType=BIGINT} IS NULL OR r.org_id = #{orgId})
              AND (#{ownerId,jdbcType=BIGINT} IS NULL OR r.owner_id = #{ownerId})
            ORDER BY r.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listRequisitions(@Param("tenantId") long tenantId,
                                               @Param("reqStatus") String reqStatus,
                                               @Param("orgId") Long orgId, @Param("ownerId") Long ownerId,
                                               @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_recruit_requisition r
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
              AND (CAST(#{reqStatus,jdbcType=VARCHAR} AS text) IS NULL OR r.req_status = #{reqStatus})
              AND (#{orgId,jdbcType=BIGINT} IS NULL OR r.org_id = #{orgId})
              AND (#{ownerId,jdbcType=BIGINT} IS NULL OR r.owner_id = #{ownerId})
            """)
    long countRequisitions(@Param("tenantId") long tenantId, @Param("reqStatus") String reqStatus,
                           @Param("orgId") Long orgId, @Param("ownerId") Long ownerId);

    @Update("""
            UPDATE talent_recruit_requisition
            SET req_status = #{reqStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateRequisitionStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                                 @Param("reqStatus") String reqStatus, @Param("userId") long userId);

    // ---------- 候选人 ----------

    @Insert("""
            INSERT INTO talent_candidate
                (id, tenant_id, name, phone_enc, phone_hash, id_card_enc, id_card_hash, email, gender, birth_date,
                 source, cand_status, experience_years, current_company, expected_salary, tags, created_by)
            VALUES (#{id}, #{tenantId}, #{name}, #{phoneEnc,jdbcType=VARCHAR}, #{phoneHash,jdbcType=CHAR},
                    #{idCardEnc,jdbcType=VARCHAR}, #{idCardHash,jdbcType=CHAR}, #{email,jdbcType=VARCHAR},
                    #{gender,jdbcType=VARCHAR}, #{birthDate,jdbcType=DATE}, #{source}, 'NEW',
                    #{experienceYears,jdbcType=NUMERIC}, #{currentCompany,jdbcType=VARCHAR},
                    #{expectedSalary,jdbcType=NUMERIC}, CAST(#{tags,jdbcType=VARCHAR} AS jsonb), #{userId})
            """)
    void insertCandidate(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                         @Param("phoneEnc") String phoneEnc, @Param("phoneHash") String phoneHash,
                         @Param("idCardEnc") String idCardEnc, @Param("idCardHash") String idCardHash,
                         @Param("email") String email, @Param("gender") String gender,
                         @Param("birthDate") LocalDate birthDate, @Param("source") String source,
                         @Param("experienceYears") BigDecimal experienceYears,
                         @Param("currentCompany") String currentCompany,
                         @Param("expectedSalary") BigDecimal expectedSalary, @Param("tags") String tags,
                         @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", name AS "name", phone_enc AS "phoneEnc", phone_hash AS "phoneHash",
                   cand_status AS "candStatus", source AS "source", version AS "version"
            FROM talent_candidate
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findCandidate(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT count(1) FROM talent_candidate
            WHERE tenant_id = #{tenantId} AND phone_hash = #{phoneHash} AND deleted = 0
            """)
    long countByCandidatePhoneHash(@Param("tenantId") long tenantId, @Param("phoneHash") String phoneHash);

    @Select("""
            SELECT c.id AS "id", c.name AS "name", c.source AS "source", c.cand_status AS "candStatus",
                   c.experience_years AS "experienceYears", c.current_company AS "currentCompany",
                   c.phone_enc AS "phoneEnc", extract(epoch FROM c.created_at) * 1000 AS "createdAt"
            FROM talent_candidate c
            WHERE c.tenant_id = #{tenantId} AND c.deleted = 0
              AND (CAST(#{candStatus,jdbcType=VARCHAR} AS text) IS NULL OR c.cand_status = #{candStatus})
              AND (CAST(#{source,jdbcType=VARCHAR} AS text) IS NULL OR c.source = #{source})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR c.name ILIKE '%' || #{keyword} || '%'
                   OR c.current_company ILIKE '%' || #{keyword} || '%')
              AND (#{requisitionId,jdbcType=BIGINT} IS NULL OR EXISTS (
                   SELECT 1 FROM talent_application a
                   WHERE a.tenant_id = c.tenant_id AND a.candidate_id = c.id
                     AND a.requisition_id = #{requisitionId} AND a.deleted = 0))
            ORDER BY c.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listCandidates(@Param("tenantId") long tenantId,
                                             @Param("requisitionId") Long requisitionId,
                                             @Param("candStatus") String candStatus, @Param("source") String source,
                                             @Param("keyword") String keyword,
                                             @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_candidate c
            WHERE c.tenant_id = #{tenantId} AND c.deleted = 0
              AND (CAST(#{candStatus,jdbcType=VARCHAR} AS text) IS NULL OR c.cand_status = #{candStatus})
              AND (CAST(#{source,jdbcType=VARCHAR} AS text) IS NULL OR c.source = #{source})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR c.name ILIKE '%' || #{keyword} || '%'
                   OR c.current_company ILIKE '%' || #{keyword} || '%')
              AND (#{requisitionId,jdbcType=BIGINT} IS NULL OR EXISTS (
                   SELECT 1 FROM talent_application a
                   WHERE a.tenant_id = c.tenant_id AND a.candidate_id = c.id
                     AND a.requisition_id = #{requisitionId} AND a.deleted = 0))
            """)
    long countCandidates(@Param("tenantId") long tenantId, @Param("requisitionId") Long requisitionId,
                         @Param("candStatus") String candStatus, @Param("source") String source,
                         @Param("keyword") String keyword);

    @Update("""
            UPDATE talent_candidate
            SET cand_status = #{candStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateCandidateStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                               @Param("candStatus") String candStatus, @Param("userId") long userId);

    // ---------- 应聘记录 ----------

    @Insert("""
            INSERT INTO talent_application
                (id, tenant_id, candidate_id, requisition_id, stage, app_status, created_by)
            VALUES (#{id}, #{tenantId}, #{candidateId}, #{requisitionId}, #{stage}, 'IN_PROGRESS', #{userId})
            """)
    void insertApplication(@Param("id") long id, @Param("tenantId") long tenantId,
                           @Param("candidateId") long candidateId, @Param("requisitionId") long requisitionId,
                           @Param("stage") String stage, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", candidate_id AS "candidateId", requisition_id AS "requisitionId",
                   stage AS "stage", app_status AS "appStatus", version AS "version"
            FROM talent_application
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findApplication(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_application
            SET stage = #{stage}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateApplicationStage(@Param("tenantId") long tenantId, @Param("id") long id,
                                @Param("stage") String stage, @Param("userId") long userId);

    @Update("""
            UPDATE talent_application
            SET app_status = #{appStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateApplicationStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                                 @Param("appStatus") String appStatus, @Param("userId") long userId);

    // ---------- 面试 ----------

    @Insert("""
            INSERT INTO talent_interview
                (id, tenant_id, application_id, round_no, interviewer_id, scheduled_at, duration_min, mode,
                 location, int_status, created_by)
            VALUES (#{id}, #{tenantId}, #{applicationId}, #{roundNo}, #{interviewerId},
                    #{scheduledAt,jdbcType=TIMESTAMP_WITH_TIMEZONE}, #{durationMin}, #{mode},
                    #{location,jdbcType=VARCHAR}, 'PENDING', #{userId})
            """)
    void insertInterview(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("applicationId") long applicationId, @Param("roundNo") int roundNo,
                         @Param("interviewerId") long interviewerId,
                         @Param("scheduledAt") OffsetDateTime scheduledAt, @Param("durationMin") int durationMin,
                         @Param("mode") String mode, @Param("location") String location,
                         @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", application_id AS "applicationId", round_no AS "roundNo",
                   interviewer_id AS "interviewerId", int_status AS "intStatus", result AS "result",
                   version AS "version"
            FROM talent_interview
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findInterview(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_interview
            SET score = #{score,jdbcType=NUMERIC}, evaluation = #{evaluation,jdbcType=VARCHAR},
                result = #{result}, int_status = 'DONE', updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND version = #{version} AND deleted = 0
            """)
    int evaluateInterview(@Param("tenantId") long tenantId, @Param("id") long id,
                          @Param("score") BigDecimal score, @Param("evaluation") String evaluation,
                          @Param("result") String result, @Param("version") int version,
                          @Param("userId") long userId);

    @Select("""
            SELECT i.id AS "id", c.name AS "candidateName", p.position_name AS "positionName",
                   i.round_no AS "roundNo", extract(epoch FROM i.scheduled_at) * 1000 AS "scheduledAt",
                   i.mode AS "mode", i.int_status AS "intStatus"
            FROM talent_interview i
            JOIN talent_application a ON a.id = i.application_id AND a.tenant_id = i.tenant_id AND a.deleted = 0
            JOIN talent_candidate c ON c.id = a.candidate_id AND c.tenant_id = a.tenant_id AND c.deleted = 0
            LEFT JOIN talent_recruit_requisition r ON r.id = a.requisition_id AND r.tenant_id = a.tenant_id
            LEFT JOIN talent_position p ON p.id = r.position_id AND p.tenant_id = r.tenant_id
            WHERE i.tenant_id = #{tenantId} AND i.interviewer_id = #{interviewerId} AND i.deleted = 0
            ORDER BY i.scheduled_at DESC
            """)
    List<Map<String, Object>> listMyInterviews(@Param("tenantId") long tenantId,
                                               @Param("interviewerId") long interviewerId);

    // ---------- Offer ----------

    @Insert("""
            INSERT INTO talent_offer
                (id, tenant_id, candidate_id, requisition_id, position_id, grade_id, base_salary, bonus,
                 offer_date, expect_join_date, offer_status, created_by)
            VALUES (#{id}, #{tenantId}, #{candidateId}, #{requisitionId}, #{positionId},
                    #{gradeId,jdbcType=BIGINT}, #{baseSalary}, #{bonus,jdbcType=NUMERIC}, #{offerDate},
                    #{expectJoinDate,jdbcType=DATE}, 'APPROVING', #{userId})
            """)
    void insertOffer(@Param("id") long id, @Param("tenantId") long tenantId,
                     @Param("candidateId") long candidateId, @Param("requisitionId") long requisitionId,
                     @Param("positionId") long positionId, @Param("gradeId") Long gradeId,
                     @Param("baseSalary") BigDecimal baseSalary, @Param("bonus") BigDecimal bonus,
                     @Param("offerDate") LocalDate offerDate, @Param("expectJoinDate") LocalDate expectJoinDate,
                     @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", candidate_id AS "candidateId", requisition_id AS "requisitionId",
                   position_id AS "positionId", grade_id AS "gradeId", base_salary AS "baseSalary",
                   offer_status AS "offerStatus", version AS "version"
            FROM talent_offer
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findOffer(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_offer
            SET offer_status = #{offerStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateOfferStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                           @Param("offerStatus") String offerStatus, @Param("userId") long userId);
}
