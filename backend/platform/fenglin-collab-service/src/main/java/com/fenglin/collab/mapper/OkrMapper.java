package com.fenglin.collab.mapper;

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
 * OKR 域 Mapper：对齐环检测使用递归 CTE，保证不会出现父子环。
 */
@Mapper
public interface OkrMapper {

    @Insert("""
            INSERT INTO collab_okr_period (id, tenant_id, name, start_date, end_date, score_policy, status, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, #{name}, #{startDate}, #{endDate}, #{scorePolicy}, #{status}, #{userId})
            """)
    void insertPeriod(@Param("id") String id, @Param("tenantId") long tenantId, @Param("name") String name,
                      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                      @Param("scorePolicy") String scorePolicy, @Param("status") String status,
                      @Param("userId") long userId);

    @Select("""
            SELECT id AS "periodId", name AS "name", to_char(start_date, 'YYYY-MM-DD') AS "startDate",
                   to_char(end_date, 'YYYY-MM-DD') AS "endDate", status AS "status", score_policy AS "scorePolicy"
            FROM collab_okr_period
            WHERE tenant_id = #{tenantId} AND id = CAST(#{periodId} AS uuid) AND deleted = 0
            """)
    Map<String, Object> findPeriod(@Param("tenantId") long tenantId, @Param("periodId") String periodId);

    @Select("""
            SELECT id AS "periodId", name AS "name", to_char(start_date, 'YYYY-MM-DD') AS "startDate",
                   to_char(end_date, 'YYYY-MM-DD') AS "endDate", status AS "status", score_policy AS "scorePolicy"
            FROM collab_okr_period
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY start_date DESC
            """)
    List<Map<String, Object>> listPeriods(@Param("tenantId") long tenantId);

    @Update("""
            UPDATE collab_okr_period SET status = #{status}, updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{periodId} AS uuid) AND deleted = 0
            """)
    void updatePeriodStatus(@Param("tenantId") long tenantId, @Param("periodId") String periodId,
                            @Param("status") String status, @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_okr_objective
                (id, tenant_id, period_id, owner_id, obj_type, dept_id, title, description_cipher, visibility,
                 status, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, CAST(#{periodId} AS uuid), #{ownerId}, #{objType},
                    #{deptId,jdbcType=BIGINT}, #{title}, #{descriptionCipher,jdbcType=VARCHAR}, #{visibility},
                    'ACTIVE', #{ownerId})
            """)
    void insertObjective(@Param("id") String id, @Param("tenantId") long tenantId, @Param("periodId") String periodId,
                         @Param("ownerId") long ownerId, @Param("objType") String objType,
                         @Param("deptId") Long deptId, @Param("title") String title,
                         @Param("descriptionCipher") String descriptionCipher, @Param("visibility") String visibility);

    @Select("""
            SELECT id AS "objectiveId", period_id AS "periodId", owner_id AS "ownerId", obj_type AS "objType",
                   dept_id AS "deptId", title AS "title", description_cipher AS "descriptionCipher",
                   visibility AS "visibility", status AS "status", score AS "score"
            FROM collab_okr_objective
            WHERE tenant_id = #{tenantId} AND id = CAST(#{objectiveId} AS uuid) AND deleted = 0
            """)
    Map<String, Object> findObjective(@Param("tenantId") long tenantId, @Param("objectiveId") String objectiveId);

    @Select("""
            SELECT o.id AS "objectiveId", o.period_id AS "periodId", o.owner_id AS "ownerId", o.obj_type AS "objType",
                   o.dept_id AS "deptId", o.title AS "title", o.visibility AS "visibility", o.status AS "status",
                   o.score AS "score"
            FROM collab_okr_objective o
            WHERE o.tenant_id = #{tenantId} AND o.deleted = 0
              AND (CAST(#{periodId,jdbcType=VARCHAR} AS uuid) IS NULL OR o.period_id = CAST(#{periodId} AS uuid))
              AND (CAST(#{ownerId,jdbcType=BIGINT} AS bigint) IS NULL OR o.owner_id = #{ownerId})
            ORDER BY o.created_at
            """)
    List<Map<String, Object>> listObjectives(@Param("tenantId") long tenantId,
                                             @Param("periodId") String periodId, @Param("ownerId") Long ownerId);

    @Insert("""
            INSERT INTO collab_okr_key_result
                (id, tenant_id, objective_id, owner_id, title, metric_type, target_value, current_value,
                 progress, confidence, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, CAST(#{objectiveId} AS uuid), #{ownerId}, #{title},
                    #{metricType}, #{targetValue,jdbcType=NUMERIC}, #{currentValue,jdbcType=NUMERIC},
                    #{progress}, #{confidence}, #{userId})
            """)
    void insertKr(@Param("id") String id, @Param("tenantId") long tenantId, @Param("objectiveId") String objectiveId,
                  @Param("ownerId") long ownerId, @Param("title") String title,
                  @Param("metricType") String metricType, @Param("targetValue") BigDecimal targetValue,
                  @Param("currentValue") BigDecimal currentValue, @Param("progress") BigDecimal progress,
                  @Param("confidence") String confidence, @Param("userId") long userId);

    @Select("""
            SELECT id AS "krId", objective_id AS "objectiveId", owner_id AS "ownerId", title AS "title",
                   metric_type AS "metricType", target_value AS "targetValue", current_value AS "currentValue",
                   progress AS "progress", confidence AS "confidence"
            FROM collab_okr_key_result
            WHERE tenant_id = #{tenantId} AND id = CAST(#{krId} AS uuid) AND deleted = 0
            """)
    Map<String, Object> findKr(@Param("tenantId") long tenantId, @Param("krId") String krId);

    @Select("""
            SELECT id AS "krId", objective_id AS "objectiveId", owner_id AS "ownerId", title AS "title",
                   metric_type AS "metricType", target_value AS "targetValue", current_value AS "currentValue",
                   progress AS "progress", confidence AS "confidence"
            FROM collab_okr_key_result
            WHERE tenant_id = #{tenantId} AND objective_id = CAST(#{objectiveId} AS uuid) AND deleted = 0
            ORDER BY created_at
            """)
    List<Map<String, Object>> listKrs(@Param("tenantId") long tenantId, @Param("objectiveId") String objectiveId);

    @Update("""
            UPDATE collab_okr_key_result
            SET progress = #{progress}, current_value = #{currentValue,jdbcType=NUMERIC},
                confidence = #{confidence}, updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{krId} AS uuid) AND deleted = 0
            """)
    void updateKrProgress(@Param("tenantId") long tenantId, @Param("krId") String krId,
                          @Param("progress") BigDecimal progress, @Param("currentValue") BigDecimal currentValue,
                          @Param("confidence") String confidence, @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_okr_alignment
                (id, tenant_id, child_objective_id, parent_objective_id, align_type, weight, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{childId} AS uuid), CAST(#{parentId} AS uuid), #{alignType},
                    #{weight,jdbcType=NUMERIC}, #{userId})
            """)
    void insertAlignment(@Param("id") long id, @Param("tenantId") long tenantId, @Param("childId") String childId,
                         @Param("parentId") String parentId, @Param("alignType") String alignType,
                         @Param("weight") BigDecimal weight, @Param("userId") long userId);

    @Select("""
            SELECT id AS "alignmentId", child_objective_id AS "childId", parent_objective_id AS "parentId",
                   align_type AS "alignType", weight AS "weight"
            FROM collab_okr_alignment
            WHERE tenant_id = #{tenantId} AND child_objective_id = CAST(#{childId} AS uuid)
              AND parent_objective_id = CAST(#{parentId} AS uuid) AND deleted = 0
            """)
    Map<String, Object> findAlignment(@Param("tenantId") long tenantId, @Param("childId") String childId,
                                      @Param("parentId") String parentId);

    @Select("""
            WITH RECURSIVE ancestors AS (
                SELECT a.parent_objective_id AS objective_id
                FROM collab_okr_alignment a
                WHERE a.tenant_id = #{tenantId} AND a.child_objective_id = CAST(#{parentId} AS uuid) AND a.deleted = 0
                UNION
                SELECT a.parent_objective_id
                FROM collab_okr_alignment a
                JOIN ancestors anc ON a.child_objective_id = anc.objective_id
                WHERE a.tenant_id = #{tenantId} AND a.deleted = 0
            )
            SELECT count(1) FROM ancestors WHERE objective_id = CAST(#{childId} AS uuid)
            """)
    long countCycle(@Param("tenantId") long tenantId, @Param("childId") String childId,
                    @Param("parentId") String parentId);

    @Select("""
            SELECT a.child_objective_id AS "childId", a.parent_objective_id AS "parentId",
                   a.align_type AS "alignType"
            FROM collab_okr_alignment a
            JOIN collab_okr_objective co ON co.id = a.child_objective_id AND co.tenant_id = a.tenant_id
            WHERE a.tenant_id = #{tenantId} AND a.deleted = 0
              AND (CAST(#{periodId,jdbcType=VARCHAR} AS uuid) IS NULL OR co.period_id = CAST(#{periodId} AS uuid))
            """)
    List<Map<String, Object>> listAlignments(@Param("tenantId") long tenantId,
                                             @Param("periodId") String periodId);

    @Insert("""
            INSERT INTO collab_okr_progress
                (id, tenant_id, kr_id, progress, confidence, note_cipher, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{krId} AS uuid), #{progress}, #{confidence},
                    #{noteCipher,jdbcType=VARCHAR}, #{userId})
            """)
    void insertProgress(@Param("id") long id, @Param("tenantId") long tenantId, @Param("krId") String krId,
                        @Param("progress") BigDecimal progress, @Param("confidence") String confidence,
                        @Param("noteCipher") String noteCipher, @Param("userId") long userId);
}
