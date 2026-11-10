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

    /**
     * 员工自助：跨产品返回指派给本人的任务，tenant_id + assignee_id 双重收敛。
     */
    @Select("""
            <script>
            SELECT t.id AS "id", t.product_id AS "productId", p.name AS "productName", t.task_no AS "taskNo",
                   t.title AS "title", t.task_type AS "taskType", t.status AS "status", t.priority AS "priority",
                   t.sprint_id AS "sprintId", t.requirement_id AS "requirementId", t.assignee_id AS "assigneeId",
                   t.estimate_hours AS "estimateHours", t.actual_hours AS "actualHours", t.due_date AS "dueDate",
                   t.completed_at::text AS "completedAt", t.created_at::text AS "createdAt"
            FROM devops_task t
            LEFT JOIN devops_product p ON p.id = t.product_id AND p.tenant_id = t.tenant_id AND p.deleted = 0
            WHERE t.tenant_id = #{tenantId} AND t.assignee_id = #{assigneeId} AND t.deleted = 0
              <if test="status != null">AND t.status = #{status}</if>
            ORDER BY t.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listTasksByAssignee(@Param("tenantId") long tenantId,
                                                  @Param("assigneeId") long assigneeId,
                                                  @Param("status") String status,
                                                  @Param("limit") int limit,
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

    /**
     * 员工自助：只返回本人填报的工时，tenant_id + user_id 双重收敛，支持工作日期区间。
     */
    @Select("""
            <script>
            SELECT id AS "id", task_id AS "taskId", defect_id AS "defectId", user_id AS "userId",
                   work_date AS "workDate", hours AS "hours", remark AS "remark", created_at::text AS "createdAt"
            FROM devops_task_worklog
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = 0
              <if test="from != null">AND work_date &gt;= #{from}</if>
              <if test="to != null">AND work_date &lt;= #{to}</if>
            ORDER BY work_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listWorklogsByUser(@Param("tenantId") long tenantId,
                                                 @Param("userId") long userId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to,
                                                 @Param("limit") int limit,
                                                 @Param("offset") int offset);

    @Select("SELECT count(1) FROM devops_task WHERE tenant_id = #{tenantId} AND product_id = #{productId} "
            + "AND deleted = 0")
    long countByProduct(@Param("tenantId") long tenantId, @Param("productId") long productId);

    @Select("SELECT count(1) FROM devops_task WHERE tenant_id = #{tenantId} AND sprint_id = #{sprintId} "
            + "AND status = 'done' AND deleted = 0")
    long countDoneBySprint(@Param("tenantId") long tenantId, @Param("sprintId") long sprintId);
}
