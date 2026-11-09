package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * E1 需求、需求关联 Mapper。
 */
@Mapper
public interface RequirementMapper {

    @Insert("""
            INSERT INTO devops_requirement
                (id, tenant_id, product_id, req_no, title, req_type, priority, status_id, status_category,
                 sprint_id, parent_id, assignee_id, reporter_id, estimate_hours, acceptance_criteria,
                 plan_start_at, plan_end_at, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{reqNo}, #{title}, #{reqType}, #{priority}, #{statusId},
                    #{statusCategory}, #{sprintId,jdbcType=BIGINT}, #{parentId,jdbcType=BIGINT},
                    #{assigneeId,jdbcType=BIGINT}, #{reporterId}, #{estimateHours,jdbcType=NUMERIC},
                    #{acceptanceCriteria,jdbcType=VARCHAR},
                    CAST(#{planStartAt,jdbcType=VARCHAR} AS timestamptz),
                    CAST(#{planEndAt,jdbcType=VARCHAR} AS timestamptz), #{userId})
            """)
    void insertRequirement(@Param("id") long id, @Param("tenantId") long tenantId,
                           @Param("productId") long productId, @Param("reqNo") String reqNo,
                           @Param("title") String title, @Param("reqType") String reqType,
                           @Param("priority") String priority, @Param("statusId") long statusId,
                           @Param("statusCategory") String statusCategory, @Param("sprintId") Long sprintId,
                           @Param("parentId") Long parentId, @Param("assigneeId") Long assigneeId,
                           @Param("reporterId") long reporterId, @Param("estimateHours") java.math.BigDecimal estimateHours,
                           @Param("acceptanceCriteria") String acceptanceCriteria,
                           @Param("planStartAt") String planStartAt, @Param("planEndAt") String planEndAt,
                           @Param("userId") long userId);

    @Select("""
            SELECT r.id AS "id", r.product_id AS "productId", r.req_no AS "reqNo", r.title AS "title",
                   r.req_type AS "reqType", r.priority AS "priority", r.status_id AS "statusId",
                   s.status_code AS "statusCode", s.status_name AS "statusName",
                   r.status_category AS "statusCategory", r.sprint_id AS "sprintId", r.parent_id AS "parentId",
                   r.assignee_id AS "assigneeId", r.reporter_id AS "reporterId",
                   r.estimate_hours AS "estimateHours", r.acceptance_criteria AS "acceptanceCriteria",
                   r.plan_start_at::text AS "planStartAt", r.plan_end_at::text AS "planEndAt",
                   r.delivered_at::text AS "deliveredAt", r.is_escaped AS "escaped", r.created_at::text AS "createdAt"
            FROM devops_requirement r
            LEFT JOIN devops_requirement_status s
                   ON s.tenant_id = r.tenant_id AND s.id = r.status_id AND s.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.id = #{id} AND r.deleted = 0
            """)
    Map<String, Object> findRequirement(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT r.id AS "id", r.product_id AS "productId", r.req_no AS "reqNo", r.title AS "title",
                   r.req_type AS "reqType", r.priority AS "priority", r.status_id AS "statusId",
                   s.status_code AS "statusCode", s.status_name AS "statusName",
                   r.status_category AS "statusCategory", r.sprint_id AS "sprintId", r.parent_id AS "parentId",
                   r.assignee_id AS "assigneeId", r.reporter_id AS "reporterId",
                   r.estimate_hours AS "estimateHours", r.acceptance_criteria AS "acceptanceCriteria",
                   r.plan_start_at::text AS "planStartAt", r.plan_end_at::text AS "planEndAt",
                   r.delivered_at::text AS "deliveredAt", r.is_escaped AS "escaped", r.created_at::text AS "createdAt"
            FROM devops_requirement r
            LEFT JOIN devops_requirement_status s
                   ON s.tenant_id = r.tenant_id AND s.id = r.status_id AND s.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0 AND r.product_id = #{productId}
              <if test="sprintId != null">AND r.sprint_id = #{sprintId}</if>
              <if test="statusCode != null">AND s.status_code = #{statusCode}</if>
            ORDER BY r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listRequirements(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                               @Param("sprintId") Long sprintId,
                                               @Param("statusCode") String statusCode,
                                               @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE devops_requirement
            SET status_id = #{statusId}, status_category = #{statusCategory},
                delivered_at = CASE WHEN #{statusCategory} = 'done' AND delivered_at IS NULL
                                    THEN now() ELSE delivered_at END,
                is_escaped = CASE WHEN #{statusCategory} = 'done' AND EXISTS (
                        SELECT 1 FROM devops_defect d
                        WHERE d.tenant_id = #{tenantId} AND d.requirement_id = #{id}
                          AND d.found_stage = 'prod' AND d.deleted = 0)
                    THEN 1 ELSE is_escaped END,
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("statusId") long statusId,
                     @Param("statusCategory") String statusCategory, @Param("userId") long userId);

    @Update("""
            UPDATE devops_requirement SET sprint_id = #{sprintId,jdbcType=BIGINT}, updated_by = #{userId},
                   version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int assignSprint(@Param("tenantId") long tenantId, @Param("id") long id,
                     @Param("sprintId") Long sprintId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_requirement_relation
                (id, tenant_id, src_requirement_id, dst_requirement_id, relation_type, created_by)
            VALUES (#{id}, #{tenantId}, #{srcId}, #{dstId}, #{relationType}, #{userId})
            ON CONFLICT (tenant_id, src_requirement_id, dst_requirement_id, relation_type)
                WHERE deleted = 0 DO NOTHING
            """)
    int insertRelation(@Param("id") long id, @Param("tenantId") long tenantId, @Param("srcId") long srcId,
                       @Param("dstId") long dstId, @Param("relationType") String relationType,
                       @Param("userId") long userId);

    @Select("SELECT count(1) FROM devops_requirement WHERE tenant_id = #{tenantId} AND product_id = #{productId} "
            + "AND deleted = 0")
    long countByProduct(@Param("tenantId") long tenantId, @Param("productId") long productId);

    @Select("SELECT count(1) FROM devops_requirement WHERE tenant_id = #{tenantId} AND product_id = #{productId} "
            + "AND status_category = 'done' AND delivered_at IS NOT NULL AND deleted = 0")
    long countDelivered(@Param("tenantId") long tenantId, @Param("productId") long productId);

    @Select("""
            SELECT (extract(epoch FROM created_at) * 1000)::bigint AS "createdAtMillis",
                   (extract(epoch FROM delivered_at) * 1000)::bigint AS "deliveredAtMillis"
            FROM devops_requirement
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND status_category = 'done'
              AND delivered_at IS NOT NULL AND deleted = 0
            """)
    List<Map<String, Object>> listDeliveredTimestamps(@Param("tenantId") long tenantId,
                                                      @Param("productId") long productId);
}
