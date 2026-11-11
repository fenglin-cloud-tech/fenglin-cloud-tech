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
 * 绩效：周期 / 目标 / 评估 / 结果访问。
 * 周期、目标、评估的状态流转均带前置状态条件，避免并发下越权流转。
 */
@Mapper
public interface PerformanceMapper {

    // ---------- 周期 ----------

    @Select("""
            SELECT id AS "id", cycle_code AS "cycleCode", cycle_name AS "cycleName", cycle_type AS "cycleType",
                   to_char(start_date, 'YYYY-MM-DD') AS "startDate",
                   to_char(end_date, 'YYYY-MM-DD') AS "endDate",
                   cycle_status AS "cycleStatus", evaluation_count AS "evaluationCount",
                   avg_score AS "avgScore", version AS "version"
            FROM fenglin_talent.talent_perf_cycle
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findCycle(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id" FROM fenglin_talent.talent_perf_cycle
            WHERE tenant_id = #{tenantId} AND cycle_code = #{code} AND deleted = 0
            """)
    Map<String, Object> findCycleByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Insert("""
            INSERT INTO fenglin_talent.talent_perf_cycle
                (id, tenant_id, cycle_code, cycle_name, cycle_type, start_date, end_date, cycle_status, created_by)
            VALUES (#{id}, #{tenantId}, #{cycleCode}, #{cycleName}, #{cycleType}, #{startDate}, #{endDate},
                    'DRAFT', #{userId})
            """)
    void insertCycle(@Param("id") long id, @Param("tenantId") long tenantId,
                     @Param("cycleCode") String cycleCode, @Param("cycleName") String cycleName,
                     @Param("cycleType") String cycleType, @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate, @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_perf_cycle
            SET cycle_status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND cycle_status = #{expected} AND deleted = 0
            """)
    int updateCycleStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                          @Param("expected") String expectedStatus, @Param("status") String status,
                          @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_perf_cycle
            SET evaluation_count = #{evaluationCount}, avg_score = #{avgScore}, updated_by = #{userId},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateCycleStats(@Param("tenantId") long tenantId, @Param("id") long id,
                         @Param("evaluationCount") int evaluationCount, @Param("avgScore") BigDecimal avgScore,
                         @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id AS "id", cycle_code AS "cycleCode", cycle_name AS "cycleName", cycle_type AS "cycleType",
                   to_char(start_date, 'YYYY-MM-DD') AS "startDate",
                   to_char(end_date, 'YYYY-MM-DD') AS "endDate",
                   cycle_status AS "cycleStatus", evaluation_count AS "evaluationCount",
                   avg_score AS "avgScore", version AS "version"
            FROM fenglin_talent.talent_perf_cycle
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY cycle_code DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listCycles(@Param("tenantId") long tenantId,
                                        @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_perf_cycle
            WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    long countCycles(@Param("tenantId") long tenantId);

    // ---------- 员工 ----------

    @Select("""
            SELECT id AS "id", name AS "name", emp_status AS "empStatus"
            FROM fenglin_talent.talent_employee
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findEmployee(@Param("tenantId") long tenantId, @Param("id") long id);

    // ---------- 目标 ----------

    @Insert("""
            INSERT INTO fenglin_talent.talent_perf_goal
                (id, tenant_id, cycle_id, employee_id, goal_name, goal_desc, weight, measure_type, target_value,
                 goal_status, created_by)
            VALUES (#{id}, #{tenantId}, #{cycleId}, #{employeeId}, #{goalName}, #{goalDesc}, #{weight},
                    #{measureType}, #{targetValue}, 'DRAFT', #{userId})
            """)
    void insertGoal(@Param("id") long id, @Param("tenantId") long tenantId, @Param("cycleId") long cycleId,
                    @Param("employeeId") long employeeId, @Param("goalName") String goalName,
                    @Param("goalDesc") String goalDesc, @Param("weight") BigDecimal weight,
                    @Param("measureType") String measureType, @Param("targetValue") String targetValue,
                    @Param("userId") long userId);

    @Select("""
            SELECT g.id AS "id", g.cycle_id AS "cycleId", g.employee_id AS "employeeId",
                   e.name AS "employeeName", g.goal_name AS "goalName", g.goal_desc AS "goalDesc",
                   g.weight AS "weight", g.measure_type AS "measureType", g.target_value AS "targetValue",
                   g.goal_status AS "goalStatus", g.approve_opinion AS "approveOpinion",
                   extract(epoch FROM g.created_at) * 1000 AS "createdAt"
            FROM fenglin_talent.talent_perf_goal g
            LEFT JOIN fenglin_talent.talent_employee e ON e.id = g.employee_id AND e.tenant_id = g.tenant_id
                AND e.deleted = 0
            WHERE g.tenant_id = #{tenantId} AND g.id = #{id} AND g.deleted = 0
            """)
    Map<String, Object> findGoal(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT g.id AS "id", g.cycle_id AS "cycleId", g.employee_id AS "employeeId",
                   e.name AS "employeeName", g.goal_name AS "goalName", g.goal_desc AS "goalDesc",
                   g.weight AS "weight", g.measure_type AS "measureType", g.target_value AS "targetValue",
                   g.goal_status AS "goalStatus", g.approve_opinion AS "approveOpinion",
                   extract(epoch FROM g.created_at) * 1000 AS "createdAt"
            FROM fenglin_talent.talent_perf_goal g
            LEFT JOIN fenglin_talent.talent_employee e ON e.id = g.employee_id AND e.tenant_id = g.tenant_id
                AND e.deleted = 0
            WHERE g.tenant_id = #{tenantId} AND g.cycle_id = #{cycleId} AND g.deleted = 0
            ORDER BY g.employee_id, g.id
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listGoals(@Param("tenantId") long tenantId, @Param("cycleId") long cycleId,
                                        @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_perf_goal
            WHERE tenant_id = #{tenantId} AND cycle_id = #{cycleId} AND deleted = 0
            """)
    long countGoals(@Param("tenantId") long tenantId, @Param("cycleId") long cycleId);

    @Update("""
            UPDATE fenglin_talent.talent_perf_goal
            SET goal_status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND goal_status = #{expected} AND deleted = 0
            """)
    int updateGoalStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                         @Param("expected") String expectedStatus, @Param("status") String status,
                         @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_perf_goal
            SET goal_status = #{status}, approved_by = #{userId}, approved_at = now(),
                approve_opinion = #{opinion}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND goal_status = 'SUBMITTED' AND deleted = 0
            """)
    int updateGoalApproval(@Param("tenantId") long tenantId, @Param("id") long id,
                           @Param("status") String status, @Param("opinion") String opinion,
                           @Param("userId") long userId);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_perf_goal
            WHERE tenant_id = #{tenantId} AND cycle_id = #{cycleId} AND goal_status = 'APPROVED' AND deleted = 0
            """)
    long countApprovedGoals(@Param("tenantId") long tenantId, @Param("cycleId") long cycleId);

    @Select("""
            SELECT coalesce(sum(weight), 0) FROM fenglin_talent.talent_perf_goal
            WHERE tenant_id = #{tenantId} AND cycle_id = #{cycleId} AND employee_id = #{employeeId}
              AND deleted = 0
            """)
    BigDecimal sumGoalWeight(@Param("tenantId") long tenantId, @Param("cycleId") long cycleId,
                             @Param("employeeId") long employeeId);

    // ---------- 评估 ----------

    @Insert("""
            INSERT INTO fenglin_talent.talent_perf_evaluation
                (id, tenant_id, cycle_id, employee_id, evaluator_id, review_type, eval_status, score, grade,
                 summary, created_by)
            VALUES (#{id}, #{tenantId}, #{cycleId}, #{employeeId}, #{evaluatorId}, #{reviewType}, 'DRAFT',
                    #{score}, #{grade}, #{summary}, #{userId})
            """)
    void insertEvaluation(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("cycleId") long cycleId, @Param("employeeId") long employeeId,
                          @Param("evaluatorId") long evaluatorId, @Param("reviewType") String reviewType,
                          @Param("score") BigDecimal score, @Param("grade") String grade,
                          @Param("summary") String summary, @Param("userId") long userId);

    @Select("""
            SELECT v.id AS "id", v.cycle_id AS "cycleId", v.employee_id AS "employeeId",
                   e.name AS "employeeName", v.evaluator_id AS "evaluatorId", v.review_type AS "reviewType",
                   v.score AS "score", v.grade AS "grade", v.eval_status AS "evalStatus",
                   v.process_instance_id AS "processInstanceId", v.approve_opinion AS "approveOpinion",
                   extract(epoch FROM v.created_at) * 1000 AS "createdAt"
            FROM fenglin_talent.talent_perf_evaluation v
            LEFT JOIN fenglin_talent.talent_employee e ON e.id = v.employee_id AND e.tenant_id = v.tenant_id
                AND e.deleted = 0
            WHERE v.tenant_id = #{tenantId} AND v.id = #{id} AND v.deleted = 0
            """)
    Map<String, Object> findEvaluation(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id" FROM fenglin_talent.talent_perf_evaluation
            WHERE tenant_id = #{tenantId} AND cycle_id = #{cycleId} AND employee_id = #{employeeId}
              AND deleted = 0
            """)
    Map<String, Object> findEvaluationByEmployee(@Param("tenantId") long tenantId,
                                                 @Param("cycleId") long cycleId,
                                                 @Param("employeeId") long employeeId);

    @Select("""
            <script>
            SELECT v.id AS "id", v.cycle_id AS "cycleId", v.employee_id AS "employeeId",
                   e.name AS "employeeName", v.evaluator_id AS "evaluatorId", v.review_type AS "reviewType",
                   v.score AS "score", v.grade AS "grade", v.eval_status AS "evalStatus",
                   v.process_instance_id AS "processInstanceId", v.approve_opinion AS "approveOpinion",
                   extract(epoch FROM v.created_at) * 1000 AS "createdAt"
            FROM fenglin_talent.talent_perf_evaluation v
            LEFT JOIN fenglin_talent.talent_employee e ON e.id = v.employee_id AND e.tenant_id = v.tenant_id
                AND e.deleted = 0
            WHERE v.tenant_id = #{tenantId} AND v.cycle_id = #{cycleId} AND v.deleted = 0
            ORDER BY v.employee_id, v.id
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listEvaluations(@Param("tenantId") long tenantId,
                                              @Param("cycleId") long cycleId,
                                              @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_perf_evaluation
            WHERE tenant_id = #{tenantId} AND cycle_id = #{cycleId} AND deleted = 0
            """)
    long countEvaluations(@Param("tenantId") long tenantId, @Param("cycleId") long cycleId);

    @Update("""
            UPDATE fenglin_talent.talent_perf_evaluation
            SET eval_status = 'APPROVING', process_instance_id = #{processInstanceId}, updated_by = #{userId},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND eval_status = 'DRAFT' AND deleted = 0
            """)
    int markEvaluationApproving(@Param("tenantId") long tenantId, @Param("id") long id,
                                @Param("processInstanceId") String processInstanceId,
                                @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_perf_evaluation
            SET eval_status = #{status}, approved_by = #{userId}, approved_at = now(),
                approve_opinion = #{opinion}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND eval_status = 'APPROVING' AND deleted = 0
            """)
    int updateEvaluationApproval(@Param("tenantId") long tenantId, @Param("id") long id,
                                 @Param("status") String status, @Param("opinion") String opinion,
                                 @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", employee_id AS "employeeId", score AS "score", grade AS "grade"
            FROM fenglin_talent.talent_perf_evaluation
            WHERE tenant_id = #{tenantId} AND cycle_id = #{cycleId} AND eval_status = 'APPROVED'
              AND deleted = 0
            ORDER BY score DESC, employee_id
            """)
    List<Map<String, Object>> approvedEvaluations(@Param("tenantId") long tenantId,
                                                  @Param("cycleId") long cycleId);

    // ---------- 结果 ----------

    @Update("""
            UPDATE fenglin_talent.talent_perf_result
            SET deleted = 1, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND cycle_id = #{cycleId} AND deleted = 0
            """)
    int deleteResults(@Param("tenantId") long tenantId, @Param("cycleId") long cycleId,
                      @Param("userId") long userId);

    @Insert("""
            INSERT INTO fenglin_talent.talent_perf_result
                (id, tenant_id, cycle_id, employee_id, final_score, grade, rank_no, bonus_coefficient,
                 result_status, created_by)
            VALUES (#{id}, #{tenantId}, #{cycleId}, #{employeeId}, #{finalScore}, #{grade}, #{rankNo},
                    #{bonusCoefficient}, 'CALCULATED', #{userId})
            """)
    void insertResult(@Param("id") long id, @Param("tenantId") long tenantId, @Param("cycleId") long cycleId,
                      @Param("employeeId") long employeeId, @Param("finalScore") BigDecimal finalScore,
                      @Param("grade") String grade, @Param("rankNo") int rankNo,
                      @Param("bonusCoefficient") BigDecimal bonusCoefficient, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT r.id AS "id", r.cycle_id AS "cycleId", r.employee_id AS "employeeId",
                   e.name AS "employeeName", r.final_score AS "finalScore", r.grade AS "grade",
                   r.rank_no AS "rankNo", r.bonus_coefficient AS "bonusCoefficient",
                   r.result_status AS "resultStatus", extract(epoch FROM r.published_at) * 1000 AS "publishedAt"
            FROM fenglin_talent.talent_perf_result r
            LEFT JOIN fenglin_talent.talent_employee e ON e.id = r.employee_id AND e.tenant_id = r.tenant_id
                AND e.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.cycle_id = #{cycleId} AND r.deleted = 0
            ORDER BY r.rank_no, r.id
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listResults(@Param("tenantId") long tenantId, @Param("cycleId") long cycleId,
                                          @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_perf_result
            WHERE tenant_id = #{tenantId} AND cycle_id = #{cycleId} AND deleted = 0
            """)
    long countResults(@Param("tenantId") long tenantId, @Param("cycleId") long cycleId);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_perf_result
            WHERE tenant_id = #{tenantId} AND cycle_id = #{cycleId} AND result_status = #{status}
              AND deleted = 0
            """)
    long countResultsByStatus(@Param("tenantId") long tenantId, @Param("cycleId") long cycleId,
                              @Param("status") String status);

    @Update("""
            UPDATE fenglin_talent.talent_perf_result
            SET result_status = 'PUBLISHED', published_at = now(), updated_by = #{userId},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND cycle_id = #{cycleId} AND result_status = 'CALCULATED'
              AND deleted = 0
            """)
    int publishResults(@Param("tenantId") long tenantId, @Param("cycleId") long cycleId,
                       @Param("userId") long userId);

    // ---------- 员工自助（按 employeeId 收敛） ----------

    @Select("""
            <script>
            SELECT c.id AS "id", c.cycle_code AS "cycleCode", c.cycle_name AS "cycleName",
                   c.cycle_type AS "cycleType", to_char(c.start_date, 'YYYY-MM-DD') AS "startDate",
                   to_char(c.end_date, 'YYYY-MM-DD') AS "endDate", c.cycle_status AS "cycleStatus",
                   c.evaluation_count AS "evaluationCount", c.avg_score AS "avgScore", c.version AS "version"
            FROM fenglin_talent.talent_perf_cycle c
            WHERE c.tenant_id = #{tenantId} AND c.deleted = 0
              AND (EXISTS (SELECT 1 FROM fenglin_talent.talent_perf_goal g
                            WHERE g.tenant_id = c.tenant_id AND g.cycle_id = c.id
                              AND g.employee_id = #{employeeId} AND g.deleted = 0)
                OR EXISTS (SELECT 1 FROM fenglin_talent.talent_perf_evaluation v
                            WHERE v.tenant_id = c.tenant_id AND v.cycle_id = c.id
                              AND v.employee_id = #{employeeId} AND v.deleted = 0)
                OR EXISTS (SELECT 1 FROM fenglin_talent.talent_perf_result r
                            WHERE r.tenant_id = c.tenant_id AND r.cycle_id = c.id
                              AND r.employee_id = #{employeeId} AND r.result_status = 'PUBLISHED'
                              AND r.deleted = 0))
            ORDER BY c.cycle_code DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listCyclesByEmployee(@Param("tenantId") long tenantId,
                                                   @Param("employeeId") long employeeId,
                                                   @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_perf_cycle c
            WHERE c.tenant_id = #{tenantId} AND c.deleted = 0
              AND (EXISTS (SELECT 1 FROM fenglin_talent.talent_perf_goal g
                            WHERE g.tenant_id = c.tenant_id AND g.cycle_id = c.id
                              AND g.employee_id = #{employeeId} AND g.deleted = 0)
                OR EXISTS (SELECT 1 FROM fenglin_talent.talent_perf_evaluation v
                            WHERE v.tenant_id = c.tenant_id AND v.cycle_id = c.id
                              AND v.employee_id = #{employeeId} AND v.deleted = 0)
                OR EXISTS (SELECT 1 FROM fenglin_talent.talent_perf_result r
                            WHERE r.tenant_id = c.tenant_id AND r.cycle_id = c.id
                              AND r.employee_id = #{employeeId} AND r.result_status = 'PUBLISHED'
                              AND r.deleted = 0))
            """)
    long countCyclesByEmployee(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId);

    @Select("""
            SELECT g.id AS "id", g.cycle_id AS "cycleId", g.employee_id AS "employeeId",
                   e.name AS "employeeName", g.goal_name AS "goalName", g.goal_desc AS "goalDesc",
                   g.weight AS "weight", g.measure_type AS "measureType", g.target_value AS "targetValue",
                   g.goal_status AS "goalStatus", g.approve_opinion AS "approveOpinion",
                   extract(epoch FROM g.created_at) * 1000 AS "createdAt"
            FROM fenglin_talent.talent_perf_goal g
            LEFT JOIN fenglin_talent.talent_employee e ON e.id = g.employee_id AND e.tenant_id = g.tenant_id
                AND e.deleted = 0
            WHERE g.tenant_id = #{tenantId} AND g.cycle_id = #{cycleId} AND g.employee_id = #{employeeId}
              AND g.deleted = 0
            ORDER BY g.id
            """)
    List<Map<String, Object>> listGoalsByEmployee(@Param("tenantId") long tenantId,
                                                  @Param("cycleId") long cycleId,
                                                  @Param("employeeId") long employeeId);

    @Select("""
            SELECT v.id AS "id", v.cycle_id AS "cycleId", v.employee_id AS "employeeId",
                   e.name AS "employeeName", v.evaluator_id AS "evaluatorId", v.review_type AS "reviewType",
                   v.score AS "score", v.grade AS "grade", v.eval_status AS "evalStatus",
                   v.process_instance_id AS "processInstanceId", v.approve_opinion AS "approveOpinion",
                   extract(epoch FROM v.created_at) * 1000 AS "createdAt"
            FROM fenglin_talent.talent_perf_evaluation v
            LEFT JOIN fenglin_talent.talent_employee e ON e.id = v.employee_id AND e.tenant_id = v.tenant_id
                AND e.deleted = 0
            WHERE v.tenant_id = #{tenantId} AND v.cycle_id = #{cycleId} AND v.employee_id = #{employeeId}
              AND v.deleted = 0
            ORDER BY v.id
            """)
    List<Map<String, Object>> listEvaluationsByEmployee(@Param("tenantId") long tenantId,
                                                        @Param("cycleId") long cycleId,
                                                        @Param("employeeId") long employeeId);

    @Select("""
            SELECT r.id AS "id", r.cycle_id AS "cycleId", r.employee_id AS "employeeId",
                   e.name AS "employeeName", r.final_score AS "finalScore", r.grade AS "grade",
                   r.rank_no AS "rankNo", r.bonus_coefficient AS "bonusCoefficient",
                   r.result_status AS "resultStatus", extract(epoch FROM r.published_at) * 1000 AS "publishedAt"
            FROM fenglin_talent.talent_perf_result r
            LEFT JOIN fenglin_talent.talent_employee e ON e.id = r.employee_id AND e.tenant_id = r.tenant_id
                AND e.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.cycle_id = #{cycleId} AND r.employee_id = #{employeeId}
              AND r.result_status = 'PUBLISHED' AND r.deleted = 0
            ORDER BY r.id
            """)
    List<Map<String, Object>> listPublishedResultsByEmployee(@Param("tenantId") long tenantId,
                                                             @Param("cycleId") long cycleId,
                                                             @Param("employeeId") long employeeId);
}
