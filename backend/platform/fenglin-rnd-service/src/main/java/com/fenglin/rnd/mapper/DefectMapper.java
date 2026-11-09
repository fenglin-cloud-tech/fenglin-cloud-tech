package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * E1 缺陷 Mapper。
 */
@Mapper
public interface DefectMapper {

    @Insert("""
            INSERT INTO devops_defect
                (id, tenant_id, product_id, defect_no, title, severity, priority, status, env, found_stage,
                 sprint_id, requirement_id, test_case_id, assignee_id, reporter_id, reproduce_steps, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{defectNo}, #{title}, #{severity}, #{priority}, 'new',
                    #{env,jdbcType=VARCHAR}, #{foundStage}, #{sprintId,jdbcType=BIGINT},
                    #{requirementId,jdbcType=BIGINT}, #{testCaseId,jdbcType=BIGINT},
                    #{assigneeId,jdbcType=BIGINT}, #{reporterId}, #{reproduceSteps,jdbcType=VARCHAR}, #{userId})
            """)
    void insertDefect(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                      @Param("defectNo") String defectNo, @Param("title") String title,
                      @Param("severity") String severity, @Param("priority") String priority,
                      @Param("env") String env, @Param("foundStage") String foundStage,
                      @Param("sprintId") Long sprintId, @Param("requirementId") Long requirementId,
                      @Param("testCaseId") Long testCaseId, @Param("assigneeId") Long assigneeId,
                      @Param("reporterId") long reporterId, @Param("reproduceSteps") String reproduceSteps,
                      @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", product_id AS "productId", defect_no AS "defectNo", title AS "title",
                   severity AS "severity", priority AS "priority", status AS "status", env AS "env",
                   found_stage AS "foundStage", sprint_id AS "sprintId", requirement_id AS "requirementId",
                   assignee_id AS "assigneeId", reporter_id AS "reporterId",
                   reproduce_steps AS "reproduceSteps", reopen_count AS "reopenCount",
                   fixed_at::text AS "fixedAt", closed_at::text AS "closedAt", created_at::text AS "createdAt"
            FROM devops_defect
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findDefect(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id AS "id", product_id AS "productId", defect_no AS "defectNo", title AS "title",
                   severity AS "severity", priority AS "priority", status AS "status", env AS "env",
                   found_stage AS "foundStage", sprint_id AS "sprintId", requirement_id AS "requirementId",
                   assignee_id AS "assigneeId", reporter_id AS "reporterId",
                   reproduce_steps AS "reproduceSteps", reopen_count AS "reopenCount",
                   fixed_at::text AS "fixedAt", closed_at::text AS "closedAt", created_at::text AS "createdAt"
            FROM devops_defect
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND deleted = 0
              <if test="status != null">AND status = #{status}</if>
              <if test="severity != null">AND severity = #{severity}</if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listDefects(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                          @Param("status") String status, @Param("severity") String severity,
                                          @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE devops_defect
            SET status = #{status},
                fixed_at = CASE WHEN #{status} = 'fixed' AND fixed_at IS NULL THEN now() ELSE fixed_at END,
                closed_at = CASE WHEN #{status} = 'closed' THEN now()
                                 WHEN #{status} NOT IN ('closed') THEN NULL ELSE closed_at END,
                reopen_count = CASE WHEN #{status} = 'reopened' THEN reopen_count + 1 ELSE reopen_count END,
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                     @Param("userId") long userId);

    @Select("SELECT count(1) FROM devops_defect WHERE tenant_id = #{tenantId} AND product_id = #{productId} "
            + "AND deleted = 0")
    long countByProduct(@Param("tenantId") long tenantId, @Param("productId") long productId);

    @Select("SELECT count(1) FROM devops_defect WHERE tenant_id = #{tenantId} AND product_id = #{productId} "
            + "AND found_stage = 'prod' AND deleted = 0")
    long countProdByProduct(@Param("tenantId") long tenantId, @Param("productId") long productId);

    @Select("""
            SELECT COALESCE(avg(extract(epoch FROM (fixed_at - created_at)) / 3600.0), 0)
            FROM devops_defect
            WHERE tenant_id = #{tenantId} AND product_id = #{productId}
              AND severity IN ('blocker', 'critical') AND fixed_at IS NOT NULL
              AND fixed_at >= CAST(#{from} AS timestamptz) AND fixed_at < CAST(#{to} AS timestamptz)
              AND deleted = 0
            """)
    java.math.BigDecimal avgMttrHours(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                      @Param("from") String from, @Param("to") String to);
}
