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
 * C5 OKR 与创新 Mapper。
 */
@Mapper
public interface OkrMapper {

    // ---------- 周期 ----------

    @Insert("""
            INSERT INTO talent_okr_period
                (id, tenant_id, period_name, period_type, start_date, end_date, review_start, review_end,
                 period_status, created_by)
            VALUES (#{id}, #{tenantId}, #{periodName}, #{periodType}, #{startDate}, #{endDate},
                    #{reviewStart,jdbcType=DATE}, #{reviewEnd,jdbcType=DATE}, 'PLANNING', #{userId})
            """)
    void insertPeriod(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("periodName") String periodName, @Param("periodType") String periodType,
                      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                      @Param("reviewStart") LocalDate reviewStart, @Param("reviewEnd") LocalDate reviewEnd,
                      @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", period_name AS "periodName", period_type AS "periodType",
                   to_char(start_date, 'YYYY-MM-DD') AS "startDate",
                   to_char(end_date, 'YYYY-MM-DD') AS "endDate",
                   to_char(review_start, 'YYYY-MM-DD') AS "reviewStart",
                   to_char(review_end, 'YYYY-MM-DD') AS "reviewEnd",
                   period_status AS "periodStatus"
            FROM talent_okr_period
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY start_date DESC, id DESC
            """)
    List<Map<String, Object>> listPeriods(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id AS "id", period_name AS "periodName", period_type AS "periodType",
                   to_char(start_date, 'YYYY-MM-DD') AS "startDate",
                   to_char(end_date, 'YYYY-MM-DD') AS "endDate", period_status AS "periodStatus"
            FROM talent_okr_period
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findPeriod(@Param("tenantId") long tenantId, @Param("id") long id);

    // ---------- 目标 ----------

    @Insert("""
            INSERT INTO talent_okr_objective
                (id, tenant_id, period_id, owner_id, org_id, obj_level, objective, progress, parent_objective_id,
                 visibility, obj_status, created_by)
            VALUES (#{id}, #{tenantId}, #{periodId}, #{ownerId}, #{orgId,jdbcType=BIGINT}, #{objLevel},
                    #{objective}, 0, #{parentObjectiveId,jdbcType=BIGINT},
                    coalesce(#{visibility,jdbcType=VARCHAR}, 'PUBLIC'), 'DRAFT', #{userId})
            """)
    void insertObjective(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("periodId") long periodId, @Param("ownerId") long ownerId,
                         @Param("orgId") Long orgId, @Param("objLevel") String objLevel,
                         @Param("objective") String objective, @Param("parentObjectiveId") Long parentObjectiveId,
                         @Param("visibility") String visibility, @Param("userId") long userId);

    String OBJECTIVE_SELECT = """
            SELECT o.id AS "id", o.period_id AS "periodId", o.org_id AS "orgId", o.owner_id AS "ownerId",
                   e.name AS "ownerName", og.org_name AS "orgName", o.obj_level AS "objLevel",
                   o.objective AS "objective", o.progress AS "progress", o.confidence AS "confidence",
                   o.parent_objective_id AS "parentObjectiveId", o.obj_status AS "objStatus",
                   o.final_score AS "finalScore", o.version AS "version"
            FROM talent_okr_objective o
            LEFT JOIN talent_employee e ON e.id = o.owner_id AND e.tenant_id = o.tenant_id
            LEFT JOIN talent_org og ON og.id = o.org_id AND og.tenant_id = o.tenant_id
            """;

    @Select(OBJECTIVE_SELECT + """
            WHERE o.tenant_id = #{tenantId} AND o.id = #{id} AND o.deleted = 0
            """)
    Map<String, Object> findObjective(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select(OBJECTIVE_SELECT + """
            WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
              AND (#{periodId,jdbcType=BIGINT} IS NULL OR o.period_id = #{periodId})
              AND (#{orgId,jdbcType=BIGINT} IS NULL OR o.org_id = #{orgId})
              AND (CAST(#{objLevel,jdbcType=VARCHAR} AS text) IS NULL OR o.obj_level = #{objLevel})
              AND (#{ownerId,jdbcType=BIGINT} IS NULL OR o.owner_id = #{ownerId})
              AND (#{parentId,jdbcType=BIGINT} IS NULL OR o.parent_objective_id = #{parentId})
            ORDER BY o.created_at, o.id
            """)
    List<Map<String, Object>> listObjectives(@Param("tenantId") long tenantId,
                                             @Param("periodId") Long periodId,
                                             @Param("orgId") Long orgId,
                                             @Param("objLevel") String objLevel,
                                             @Param("ownerId") Long ownerId,
                                             @Param("parentId") Long parentId);

    @Select("""
            SELECT parent_objective_id AS "parentObjectiveId"
            FROM talent_okr_objective
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findObjectiveParent(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_okr_objective
            SET obj_status = #{objStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateObjectiveStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                               @Param("objStatus") String objStatus, @Param("userId") long userId);

    @Update("""
            UPDATE talent_okr_objective
            SET progress = #{progress}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateObjectiveProgress(@Param("tenantId") long tenantId, @Param("id") long id,
                                 @Param("progress") int progress, @Param("userId") long userId);

    @Update("""
            UPDATE talent_okr_objective
            SET final_score = #{finalScore}, obj_status = 'CLOSED', updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateObjectiveReview(@Param("tenantId") long tenantId, @Param("id") long id,
                               @Param("finalScore") BigDecimal finalScore, @Param("userId") long userId);

    @Update("""
            UPDATE talent_okr_objective
            SET parent_objective_id = #{parentObjectiveId}, obj_status = 'ALIGNED', updated_by = #{userId},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateObjectiveParent(@Param("tenantId") long tenantId, @Param("id") long id,
                               @Param("parentObjectiveId") long parentObjectiveId, @Param("userId") long userId);

    // ---------- 关键结果 ----------

    @Insert("""
            INSERT INTO talent_okr_key_result
                (id, tenant_id, objective_id, title, metric_type, start_value, target_value, current_value,
                 weight, progress, kr_status, created_by)
            VALUES (#{id}, #{tenantId}, #{objectiveId}, #{title}, #{metricType}, #{startValue}, #{targetValue},
                    #{startValue}, coalesce(#{weight}, 0), 0, 'NOT_STARTED', #{userId})
            """)
    void insertKr(@Param("id") long id, @Param("tenantId") long tenantId, @Param("objectiveId") long objectiveId,
                  @Param("title") String title, @Param("metricType") String metricType,
                  @Param("startValue") BigDecimal startValue, @Param("targetValue") BigDecimal targetValue,
                  @Param("weight") Integer weight, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", objective_id AS "objectiveId", title AS "title", metric_type AS "metricType",
                   start_value AS "startValue", target_value AS "targetValue", current_value AS "currentValue",
                   weight AS "weight", progress AS "progress", kr_status AS "krStatus", version AS "version"
            FROM talent_okr_key_result
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findKr(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", weight AS "weight", progress AS "progress"
            FROM talent_okr_key_result
            WHERE tenant_id = #{tenantId} AND objective_id = #{objectiveId} AND deleted = 0
            """)
    List<Map<String, Object>> listKrsByObjective(@Param("tenantId") long tenantId,
                                                 @Param("objectiveId") long objectiveId);

    @Update("""
            UPDATE talent_okr_key_result
            SET current_value = #{currentValue}, progress = #{progress}, kr_status = #{krStatus},
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND version = #{version} AND deleted = 0
            """)
    int updateKrProgress(@Param("tenantId") long tenantId, @Param("id") long id,
                         @Param("currentValue") BigDecimal currentValue, @Param("progress") int progress,
                         @Param("krStatus") String krStatus, @Param("version") int version,
                         @Param("userId") long userId);

    // ---------- 对齐 ----------

    @Insert("""
            INSERT INTO talent_okr_alignment
                (id, tenant_id, child_objective_id, parent_objective_id, align_type, weight, created_by)
            VALUES (#{id}, #{tenantId}, #{childObjectiveId}, #{parentObjectiveId}, #{alignType},
                    coalesce(#{weight}, 100), #{userId})
            """)
    void insertAlignment(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("childObjectiveId") long childObjectiveId,
                         @Param("parentObjectiveId") long parentObjectiveId,
                         @Param("alignType") String alignType, @Param("weight") Integer weight,
                         @Param("userId") long userId);

    @Select("""
            SELECT a.child_objective_id AS "childObjectiveId", a.parent_objective_id AS "parentObjectiveId",
                   a.align_type AS "alignType", a.weight AS "weight"
            FROM talent_okr_alignment a
            JOIN talent_okr_objective o ON o.id = a.child_objective_id AND o.tenant_id = a.tenant_id
            WHERE a.tenant_id = #{tenantId} AND a.deleted = 0 AND o.deleted = 0
              AND (#{periodId,jdbcType=BIGINT} IS NULL OR o.period_id = #{periodId})
            """)
    List<Map<String, Object>> listAlignments(@Param("tenantId") long tenantId,
                                             @Param("periodId") Long periodId);

    // ---------- 创新提案 ----------

    @Insert("""
            INSERT INTO talent_innovation_proposal
                (id, tenant_id, proposal_no, title, category, content, proposer_id, team_members,
                 expected_benefit, attachment_ids, votes, prop_status, created_by)
            VALUES (#{id}, #{tenantId}, #{proposalNo}, #{title}, #{category,jdbcType=VARCHAR}, #{content},
                    #{proposerId}, CAST(#{teamMembers,jdbcType=VARCHAR} AS jsonb),
                    #{expectedBenefit,jdbcType=VARCHAR}, CAST(#{attachmentIds,jdbcType=VARCHAR} AS jsonb),
                    0, 'REVIEWING', #{userId})
            """)
    void insertProposal(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("proposalNo") String proposalNo, @Param("title") String title,
                        @Param("category") String category, @Param("content") String content,
                        @Param("proposerId") long proposerId, @Param("teamMembers") String teamMembers,
                        @Param("expectedBenefit") String expectedBenefit, @Param("attachmentIds") String attachmentIds,
                        @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", proposal_no AS "proposalNo", title AS "title", category AS "category",
                   content AS "content", proposer_id AS "proposerId", votes AS "votes",
                   prop_status AS "propStatus", version AS "version"
            FROM talent_innovation_proposal
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findProposal(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT p.id AS "id", p.proposal_no AS "proposalNo", p.title AS "title", p.category AS "category",
                   e.name AS "proposer", p.votes AS "votes", p.prop_status AS "propStatus"
            FROM talent_innovation_proposal p
            LEFT JOIN talent_employee e ON e.id = p.proposer_id AND e.tenant_id = p.tenant_id
            WHERE p.tenant_id = #{tenantId} AND p.deleted = 0
              AND (CAST(#{propStatus,jdbcType=VARCHAR} AS text) IS NULL OR p.prop_status = #{propStatus})
              AND (CAST(#{category,jdbcType=VARCHAR} AS text) IS NULL OR p.category = #{category})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR p.title ILIKE '%' || #{keyword} || '%'
                   OR p.proposal_no ILIKE '%' || #{keyword} || '%')
            ORDER BY p.votes DESC, p.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listProposals(@Param("tenantId") long tenantId,
                                            @Param("propStatus") String propStatus,
                                            @Param("category") String category,
                                            @Param("keyword") String keyword,
                                            @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_innovation_proposal p
            WHERE p.tenant_id = #{tenantId} AND p.deleted = 0
              AND (CAST(#{propStatus,jdbcType=VARCHAR} AS text) IS NULL OR p.prop_status = #{propStatus})
              AND (CAST(#{category,jdbcType=VARCHAR} AS text) IS NULL OR p.category = #{category})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR p.title ILIKE '%' || #{keyword} || '%'
                   OR p.proposal_no ILIKE '%' || #{keyword} || '%')
            """)
    long countProposals(@Param("tenantId") long tenantId, @Param("propStatus") String propStatus,
                        @Param("category") String category, @Param("keyword") String keyword);

    @Update("""
            UPDATE talent_innovation_proposal
            SET votes = votes + 1, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void incrementVotes(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Update("""
            UPDATE talent_innovation_proposal
            SET prop_status = #{propStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateProposalStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                              @Param("propStatus") String propStatus, @Param("userId") long userId);

    @Select("""
            SELECT count(1) FROM talent_innovation_review
            WHERE tenant_id = #{tenantId} AND proposal_id = #{proposalId} AND deleted = 0
            """)
    long countReviews(@Param("tenantId") long tenantId, @Param("proposalId") long proposalId);

    @Insert("""
            INSERT INTO talent_innovation_review
                (id, tenant_id, proposal_id, reviewer_id, round_no, score, comment, rv_result, created_by)
            VALUES (#{id}, #{tenantId}, #{proposalId}, #{reviewerId}, #{roundNo}, #{score,jdbcType=NUMERIC},
                    #{comment,jdbcType=VARCHAR}, #{rvResult}, #{userId})
            """)
    void insertReview(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("proposalId") long proposalId, @Param("reviewerId") long reviewerId,
                      @Param("roundNo") int roundNo, @Param("score") BigDecimal score,
                      @Param("comment") String comment, @Param("rvResult") String rvResult,
                      @Param("userId") long userId);
}
