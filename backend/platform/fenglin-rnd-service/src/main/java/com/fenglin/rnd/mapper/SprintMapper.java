package com.fenglin.rnd.mapper;

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
 * E1 迭代、迭代成员与燃尽图 Mapper。
 */
@Mapper
public interface SprintMapper {

    @Insert("""
            INSERT INTO devops_sprint
                (id, tenant_id, product_id, sprint_no, name, goal, start_date, end_date, capacity_hours,
                 status, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{sprintNo}, #{name}, #{goal,jdbcType=VARCHAR},
                    #{startDate}, #{endDate}, #{capacityHours,jdbcType=NUMERIC}, 'planning', #{userId})
            """)
    void insertSprint(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                      @Param("sprintNo") String sprintNo, @Param("name") String name, @Param("goal") String goal,
                      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                      @Param("capacityHours") BigDecimal capacityHours, @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_sprint_member (id, tenant_id, sprint_id, user_id, available_hours, created_by)
            VALUES (#{id}, #{tenantId}, #{sprintId}, #{userId}, #{availableHours}, #{operatorId})
            """)
    void insertMember(@Param("id") long id, @Param("tenantId") long tenantId, @Param("sprintId") long sprintId,
                      @Param("userId") long userId, @Param("availableHours") BigDecimal availableHours,
                      @Param("operatorId") long operatorId);

    @Select("""
            SELECT s.id AS "id", s.product_id AS "productId", s.sprint_no AS "sprintNo", s.name AS "name",
                   s.goal AS "goal", s.start_date AS "startDate", s.end_date AS "endDate",
                   s.capacity_hours AS "capacityHours", s.status AS "status", s.created_at::text AS "createdAt",
                   (SELECT count(1) FROM devops_requirement r WHERE r.tenant_id = s.tenant_id
                      AND r.sprint_id = s.id AND r.deleted = 0) AS "requirementCount",
                   (SELECT count(1) FROM devops_task t WHERE t.tenant_id = s.tenant_id
                      AND t.sprint_id = s.id AND t.deleted = 0) AS "taskCount",
                   (SELECT count(1) FROM devops_task t WHERE t.tenant_id = s.tenant_id
                      AND t.sprint_id = s.id AND t.status = 'done' AND t.deleted = 0) AS "doneTaskCount"
            FROM devops_sprint s
            WHERE s.tenant_id = #{tenantId} AND s.id = #{id} AND s.deleted = 0
            """)
    Map<String, Object> findSprint(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT s.id AS "id", s.product_id AS "productId", s.sprint_no AS "sprintNo", s.name AS "name",
                   s.goal AS "goal", s.start_date AS "startDate", s.end_date AS "endDate",
                   s.capacity_hours AS "capacityHours", s.status AS "status", s.created_at::text AS "createdAt",
                   (SELECT count(1) FROM devops_requirement r WHERE r.tenant_id = s.tenant_id
                      AND r.sprint_id = s.id AND r.deleted = 0) AS "requirementCount",
                   (SELECT count(1) FROM devops_task t WHERE t.tenant_id = s.tenant_id
                      AND t.sprint_id = s.id AND t.deleted = 0) AS "taskCount",
                   (SELECT count(1) FROM devops_task t WHERE t.tenant_id = s.tenant_id
                      AND t.sprint_id = s.id AND t.status = 'done' AND t.deleted = 0) AS "doneTaskCount"
            FROM devops_sprint s
            WHERE s.tenant_id = #{tenantId} AND s.product_id = #{productId} AND s.deleted = 0
              <if test="status != null">AND s.status = #{status}</if>
            ORDER BY s.start_date DESC, s.id DESC
            </script>
            """)
    List<Map<String, Object>> listSprints(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                          @Param("status") String status);

    @Update("""
            UPDATE devops_sprint SET status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0 AND status = #{expected}
            """)
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                     @Param("expected") String expected, @Param("userId") long userId);

    @Select("""
            SELECT COALESCE(sum(t.estimate_hours), 0) AS "totalHours",
                   COALESCE(sum(CASE WHEN t.status = 'done' THEN t.estimate_hours ELSE 0 END), 0) AS "doneHours",
                   count(1) AS "totalTasks"
            FROM devops_task t
            WHERE t.tenant_id = #{tenantId} AND t.sprint_id = #{sprintId} AND t.deleted = 0
            """)
    Map<String, Object> burndownSummary(@Param("tenantId") long tenantId, @Param("sprintId") long sprintId);

    @Select("""
            SELECT t.id AS "id", t.estimate_hours AS "estimateHours", t.actual_hours AS "actualHours",
                   t.status AS "status", t.completed_at::text AS "completedAt"
            FROM devops_task t
            WHERE t.tenant_id = #{tenantId} AND t.sprint_id = #{sprintId} AND t.deleted = 0
            ORDER BY t.id
            """)
    List<Map<String, Object>> listSprintTasks(@Param("tenantId") long tenantId, @Param("sprintId") long sprintId);
}
