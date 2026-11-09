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
 * E1 任务与工时 Mapper。
 */
@Mapper
public interface TaskMapper {

    @Insert("""
            INSERT INTO devops_task
                (id, tenant_id, product_id, task_no, title, task_type, status, priority, sprint_id,
                 requirement_id, defect_id, parent_id, assignee_id, estimate_hours, due_date, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{taskNo}, #{title}, #{taskType}, 'todo', #{priority},
                    #{sprintId,jdbcType=BIGINT}, #{requirementId,jdbcType=BIGINT}, #{defectId,jdbcType=BIGINT},
                    #{parentId,jdbcType=BIGINT}, #{assigneeId,jdbcType=BIGINT},
                    #{estimateHours,jdbcType=NUMERIC}, #{dueDate,jdbcType=DATE}, #{userId})
            """)
    void insertTask(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                    @Param("taskNo") String taskNo, @Param("title") String title, @Param("taskType") String taskType,
                    @Param("priority") String priority, @Param("sprintId") Long sprintId,
                    @Param("requirementId") Long requirementId, @Param("defectId") Long defectId,
                    @Param("parentId") Long parentId, @Param("assigneeId") Long assigneeId,
                    @Param("estimateHours") BigDecimal estimateHours, @Param("dueDate") LocalDate dueDate,
                    @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", product_id AS "productId", task_no AS "taskNo", title AS "title",
                   task_type AS "taskType", status AS "status", priority AS "priority", sprint_id AS "sprintId",
                   requirement_id AS "requirementId", defect_id AS "defectId", assignee_id AS "assigneeId",
                   estimate_hours AS "estimateHours", actual_hours AS "actualHours", due_date AS "dueDate",
                   completed_at::text AS "completedAt", created_at::text AS "createdAt"
            FROM devops_task
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findTask(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id AS "id", product_id AS "productId", task_no AS "taskNo", title AS "title",
                   task_type AS "taskType", status AS "status", priority AS "priority", sprint_id AS "sprintId",
                   requirement_id AS "requirementId", defect_id AS "defectId", assignee_id AS "assigneeId",
                   estimate_hours AS "estimateHours", actual_hours AS "actualHours", due_date AS "dueDate",
                   completed_at::text AS "completedAt", created_at::text AS "createdAt"
            FROM devops_task
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND product_id = #{productId}
              <if test="sprintId != null">AND sprint_id = #{sprintId}</if>
              <if test="assigneeId != null">AND assignee_id = #{assigneeId}</if>
              <if test="status != null">AND status = #{status}</if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listTasks(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                        @Param("sprintId") Long sprintId, @Param("assigneeId") Long assigneeId,
                                        @Param("status") String status, @Param("limit") int limit,
                                        @Param("offset") int offset);

    @Update("""
            UPDATE devops_task
            SET status = #{status},
                completed_at = CASE WHEN #{status} = 'done' AND completed_at IS NULL THEN now()
                                    WHEN #{status} <> 'done' THEN NULL ELSE completed_at END,
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                     @Param("userId") long userId);

    @Update("""
            UPDATE devops_task SET actual_hours = (
                       SELECT COALESCE(sum(w.hours), 0) FROM devops_task_worklog w
                       WHERE w.tenant_id = #{tenantId} AND w.task_id = #{id} AND w.deleted = 0),
                   updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int refreshActualHours(@Param("tenantId") long tenantId, @Param("id") long taskId,
                           @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_task_worklog
                (id, tenant_id, task_id, defect_id, user_id, work_date, hours, remark, created_by)
            VALUES (#{id}, #{tenantId}, #{taskId,jdbcType=BIGINT}, #{defectId,jdbcType=BIGINT}, #{userId},
                    #{workDate}, #{hours}, #{remark,jdbcType=VARCHAR}, #{userId})
            """)
    void insertWorklog(@Param("id") long id, @Param("tenantId") long tenantId, @Param("taskId") Long taskId,
                       @Param("defectId") Long defectId, @Param("userId") long userId,
                       @Param("workDate") LocalDate workDate, @Param("hours") BigDecimal hours,
                       @Param("remark") String remark);

    @Select("""
            SELECT COALESCE(sum(hours), 0) FROM devops_task_worklog
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND work_date = #{workDate} AND deleted = 0
            """)
    BigDecimal sumUserHoursOnDate(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                  @Param("workDate") LocalDate workDate);

    @Select("""
            SELECT id AS "id", task_id AS "taskId", defect_id AS "defectId", user_id AS "userId",
                   work_date AS "workDate", hours AS "hours", remark AS "remark", created_at::text AS "createdAt"
            FROM devops_task_worklog
            WHERE tenant_id = #{tenantId} AND (#{taskId,jdbcType=BIGINT} IS NULL OR task_id = #{taskId,jdbcType=BIGINT})
              AND deleted = 0
            ORDER BY work_date DESC, id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listWorklogs(@Param("tenantId") long tenantId, @Param("taskId") Long taskId,
                                           @Param("limit") int limit);

    @Select("SELECT count(1) FROM devops_task WHERE tenant_id = #{tenantId} AND product_id = #{productId} "
            + "AND deleted = 0")
    long countByProduct(@Param("tenantId") long tenantId, @Param("productId") long productId);

    @Select("SELECT count(1) FROM devops_task WHERE tenant_id = #{tenantId} AND sprint_id = #{sprintId} "
            + "AND status = 'done' AND deleted = 0")
    long countDoneBySprint(@Param("tenantId") long tenantId, @Param("sprintId") long sprintId);
}
