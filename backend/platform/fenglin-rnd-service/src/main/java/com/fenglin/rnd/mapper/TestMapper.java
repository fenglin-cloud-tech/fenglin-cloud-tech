package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * E1 测试用例、测试计划、测试执行 Mapper。
 */
@Mapper
public interface TestMapper {

    @Insert("""
            INSERT INTO devops_test_case
                (id, tenant_id, product_id, case_no, title, module, level, case_type, preconditions, steps,
                 requirement_id, review_status, status, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{caseNo}, #{title}, #{module,jdbcType=VARCHAR}, #{level},
                    #{caseType}, #{preconditions,jdbcType=VARCHAR}, CAST(#{steps} AS jsonb),
                    #{requirementId,jdbcType=BIGINT}, 'draft', 'active', #{userId})
            """)
    void insertCase(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                    @Param("caseNo") String caseNo, @Param("title") String title, @Param("module") String module,
                    @Param("level") String level, @Param("caseType") String caseType,
                    @Param("preconditions") String preconditions, @Param("steps") String steps,
                    @Param("requirementId") Long requirementId, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", product_id AS "productId", case_no AS "caseNo", title AS "title",
                   module AS "module", level AS "level", case_type AS "caseType",
                   preconditions AS "preconditions", steps::text AS "steps", requirement_id AS "requirementId",
                   review_status AS "reviewStatus", status AS "status", created_at::text AS "createdAt"
            FROM devops_test_case
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findCase(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id AS "id", product_id AS "productId", case_no AS "caseNo", title AS "title",
                   module AS "module", level AS "level", case_type AS "caseType",
                   preconditions AS "preconditions", steps::text AS "steps", requirement_id AS "requirementId",
                   review_status AS "reviewStatus", status AS "status", created_at::text AS "createdAt"
            FROM devops_test_case
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND deleted = 0
              <if test="reviewStatus != null">AND review_status = #{reviewStatus}</if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listCases(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                        @Param("reviewStatus") String reviewStatus, @Param("limit") int limit,
                                        @Param("offset") int offset);

    @Update("""
            UPDATE devops_test_case SET review_status = #{reviewStatus}, updated_by = #{userId},
                   version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateReviewStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                           @Param("reviewStatus") String reviewStatus, @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_test_plan
                (id, tenant_id, product_id, plan_no, name, sprint_id, build_id, status, owner_id,
                 start_date, end_date, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{planNo}, #{name}, #{sprintId,jdbcType=BIGINT},
                    #{buildId,jdbcType=BIGINT}, 'pending', #{ownerId}, #{startDate,jdbcType=DATE},
                    #{endDate,jdbcType=DATE}, #{userId})
            """)
    void insertPlan(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                    @Param("planNo") String planNo, @Param("name") String name, @Param("sprintId") Long sprintId,
                    @Param("buildId") Long buildId, @Param("ownerId") long ownerId,
                    @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                    @Param("userId") long userId);

    @Select("""
            SELECT p.id AS "id", p.product_id AS "productId", p.plan_no AS "planNo", p.name AS "name",
                   p.sprint_id AS "sprintId", p.build_id AS "buildId", p.status AS "status",
                   p.start_date AS "startDate", p.end_date AS "endDate", p.created_at::text AS "createdAt",
                   (SELECT count(1) FROM devops_test_execution_item i
                     JOIN devops_test_execution e ON e.tenant_id = i.tenant_id AND e.id = i.execution_id
                    WHERE e.tenant_id = p.tenant_id AND e.plan_id = p.id AND i.deleted = 0) AS "caseCount",
                   (SELECT count(1) FROM devops_test_execution_item i
                     JOIN devops_test_execution e ON e.tenant_id = i.tenant_id AND e.id = i.execution_id
                    WHERE e.tenant_id = p.tenant_id AND e.plan_id = p.id AND i.result = 'pass'
                      AND i.deleted = 0) AS "passCount",
                   (SELECT count(1) FROM devops_test_execution_item i
                     JOIN devops_test_execution e ON e.tenant_id = i.tenant_id AND e.id = i.execution_id
                    WHERE e.tenant_id = p.tenant_id AND e.plan_id = p.id AND i.result = 'fail'
                      AND i.deleted = 0) AS "failCount"
            FROM devops_test_plan p
            WHERE p.tenant_id = #{tenantId} AND p.id = #{id} AND p.deleted = 0
            """)
    Map<String, Object> findPlan(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE devops_test_plan SET status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updatePlanStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                         @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_test_execution
                (id, tenant_id, plan_id, executor_id, build_id, result, created_by)
            VALUES (#{id}, #{tenantId}, #{planId}, #{executorId}, #{buildId,jdbcType=BIGINT}, 'pending', #{userId})
            """)
    void insertExecution(@Param("id") long id, @Param("tenantId") long tenantId, @Param("planId") long planId,
                         @Param("executorId") long executorId, @Param("buildId") Long buildId,
                         @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", plan_id AS "planId", executor_id AS "executorId", result AS "result",
                   pass_count AS "passCount", fail_count AS "failCount", executed_at::text AS "executedAt"
            FROM devops_test_execution
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findExecution(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO devops_test_execution_item
                (id, tenant_id, execution_id, case_id, result, defect_id, remark, created_by)
            VALUES (#{id}, #{tenantId}, #{executionId}, #{caseId}, 'pending', #{defectId,jdbcType=BIGINT},
                    #{remark,jdbcType=VARCHAR}, #{userId})
            """)
    void insertExecutionItem(@Param("id") long id, @Param("tenantId") long tenantId,
                             @Param("executionId") long executionId, @Param("caseId") long caseId,
                             @Param("defectId") Long defectId, @Param("remark") String remark,
                             @Param("userId") long userId);

    @Update("""
            UPDATE devops_test_execution_item
            SET result = #{result}, defect_id = #{defectId,jdbcType=BIGINT}, remark = #{remark,jdbcType=VARCHAR},
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateExecutionItem(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("result") String result, @Param("defectId") Long defectId,
                            @Param("remark") String remark, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", execution_id AS "executionId", case_id AS "caseId", result AS "result",
                   defect_id AS "defectId", remark AS "remark"
            FROM devops_test_execution_item
            WHERE tenant_id = #{tenantId} AND execution_id = #{executionId} AND deleted = 0
            ORDER BY id
            """)
    List<Map<String, Object>> listExecutionItems(@Param("tenantId") long tenantId,
                                                 @Param("executionId") long executionId);

    @Select("""
            SELECT count(1) FROM devops_test_execution_item
            WHERE tenant_id = #{tenantId} AND execution_id = #{executionId} AND result = #{result} AND deleted = 0
            """)
    long countItems(@Param("tenantId") long tenantId, @Param("executionId") long executionId,
                    @Param("result") String result);

    @Select("""
            SELECT count(1) FROM devops_test_execution_item
            WHERE tenant_id = #{tenantId} AND execution_id = #{executionId} AND deleted = 0
            """)
    long countItemsTotal(@Param("tenantId") long tenantId, @Param("executionId") long executionId);

    @Update("""
            UPDATE devops_test_execution
            SET result = #{result}, pass_count = #{passCount}, fail_count = #{failCount},
                executed_at = CASE WHEN #{result} = 'pending' THEN executed_at ELSE now() END,
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateExecution(@Param("tenantId") long tenantId, @Param("id") long id, @Param("result") String result,
                        @Param("passCount") long passCount, @Param("failCount") long failCount,
                        @Param("userId") long userId);

    @Select("SELECT count(1) FROM devops_test_case WHERE tenant_id = #{tenantId} AND product_id = #{productId} "
            + "AND deleted = 0")
    long countCases(@Param("tenantId") long tenantId, @Param("productId") long productId);
}
