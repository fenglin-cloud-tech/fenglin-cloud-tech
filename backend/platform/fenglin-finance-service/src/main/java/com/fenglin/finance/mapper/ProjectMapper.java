package com.fenglin.finance.mapper;

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
 * D2 项目、成员、里程碑、工时、成本、结算 Mapper。
 */
@Mapper
public interface ProjectMapper {

    @Insert("""
            INSERT INTO fin_project
                (id, tenant_id, project_no, project_name, project_type, customer_id, customer_name,
                 contract_no, contract_amount, budget_amount, estimated_total_cost, start_date, end_date,
                 manager_id, dept_id, currency_code, status, description, created_by)
            VALUES (#{id}, #{tenantId}, #{projectNo}, #{projectName}, #{projectType},
                    #{customerId,jdbcType=BIGINT}, #{customerName,jdbcType=VARCHAR}, #{contractNo,jdbcType=VARCHAR},
                    #{contractAmount}, #{budgetAmount}, #{estimatedTotalCost}, #{startDate,jdbcType=DATE},
                    #{endDate,jdbcType=DATE}, #{managerId,jdbcType=BIGINT}, #{deptId,jdbcType=BIGINT},
                    #{currencyCode}, #{status}, #{description,jdbcType=VARCHAR}, #{userId})
            """)
    void insertProject(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("projectNo") String projectNo, @Param("projectName") String projectName,
                       @Param("projectType") String projectType, @Param("customerId") Long customerId,
                       @Param("customerName") String customerName, @Param("contractNo") String contractNo,
                       @Param("contractAmount") BigDecimal contractAmount,
                       @Param("budgetAmount") BigDecimal budgetAmount,
                       @Param("estimatedTotalCost") BigDecimal estimatedTotalCost,
                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                       @Param("managerId") Long managerId, @Param("deptId") Long deptId,
                       @Param("currencyCode") String currencyCode, @Param("status") String status,
                       @Param("description") String description, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", project_no AS "projectNo", project_name AS "projectName",
                   project_type AS "projectType", customer_id AS "customerId",
                   customer_name AS "customerName", contract_no AS "contractNo",
                   contract_amount AS "contractAmount", budget_amount AS "budgetAmount",
                   estimated_total_cost AS "estimatedTotalCost", actual_cost AS "actualCost",
                   recognized_revenue AS "recognizedRevenue", start_date::text AS "startDate",
                   end_date::text AS "endDate", manager_id AS "managerId", dept_id AS "deptId",
                   currency_code AS "currencyCode", status AS "status", description AS "description"
            FROM fin_project
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findProject(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", project_no AS "projectNo", project_name AS "projectName",
                   project_type AS "projectType", customer_name AS "customerName",
                   contract_amount AS "contractAmount", budget_amount AS "budgetAmount",
                   actual_cost AS "actualCost", recognized_revenue AS "recognizedRevenue",
                   start_date::text AS "startDate", end_date::text AS "endDate",
                   manager_id AS "managerId", status AS "status"
            FROM fin_project
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listProjects(@Param("tenantId") long tenantId,
                                           @Param("status") String status,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE fin_project SET status = #{status}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
              AND (CAST(#{expected,jdbcType=VARCHAR} AS text) IS NULL OR status = #{expected})
            """)
    int updateProjectStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("status") String status, @Param("expected") String expected);

    @Update("""
            UPDATE fin_project SET actual_cost = actual_cost + #{amount}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int addActualCost(@Param("tenantId") long tenantId, @Param("id") long id,
                      @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE fin_project SET recognized_revenue = #{amount}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateRecognizedRevenue(@Param("tenantId") long tenantId, @Param("id") long id,
                                @Param("amount") BigDecimal amount);

    @Insert("""
            INSERT INTO fin_project_member
                (id, tenant_id, project_id, employee_id, employee_name, member_role, allocation_pct,
                 cost_rate, join_date, status, created_by)
            VALUES (#{id}, #{tenantId}, #{projectId}, #{employeeId}, #{employeeName,jdbcType=VARCHAR},
                    #{memberRole,jdbcType=VARCHAR}, #{allocationPct}, #{costRate,jdbcType=NUMERIC},
                    #{joinDate}, 'ACTIVE', #{userId})
            """)
    void insertMember(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("projectId") long projectId, @Param("employeeId") long employeeId,
                      @Param("employeeName") String employeeName, @Param("memberRole") String memberRole,
                      @Param("allocationPct") BigDecimal allocationPct, @Param("costRate") BigDecimal costRate,
                      @Param("joinDate") LocalDate joinDate, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", employee_id AS "employeeId", employee_name AS "employeeName",
                   member_role AS "memberRole", allocation_pct AS "allocationPct", cost_rate AS "costRate",
                   join_date::text AS "joinDate", leave_date::text AS "leaveDate", status AS "status"
            FROM fin_project_member
            WHERE tenant_id = #{tenantId} AND project_id = #{projectId} AND deleted = 0
            ORDER BY id
            """)
    List<Map<String, Object>> listMembers(@Param("tenantId") long tenantId,
                                          @Param("projectId") long projectId);

    @Select("""
            SELECT id AS "id", employee_id AS "employeeId", employee_name AS "employeeName",
                   member_role AS "memberRole", cost_rate AS "costRate", status AS "status"
            FROM fin_project_member
            WHERE tenant_id = #{tenantId} AND project_id = #{projectId} AND employee_id = #{employeeId}
              AND status = 'ACTIVE' AND deleted = 0
            """)
    Map<String, Object> findMember(@Param("tenantId") long tenantId, @Param("projectId") long projectId,
                                   @Param("employeeId") long employeeId);

    @Insert("""
            INSERT INTO fin_project_milestone
                (id, tenant_id, project_id, milestone_name, plan_date, deliverable, weight, status, created_by)
            VALUES (#{id}, #{tenantId}, #{projectId}, #{milestoneName}, #{planDate},
                    #{deliverable,jdbcType=VARCHAR}, #{weight}, 'PENDING', #{userId})
            """)
    void insertMilestone(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("projectId") long projectId, @Param("milestoneName") String milestoneName,
                         @Param("planDate") LocalDate planDate, @Param("deliverable") String deliverable,
                         @Param("weight") BigDecimal weight, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", milestone_name AS "milestoneName", plan_date::text AS "planDate",
                   actual_date::text AS "actualDate", deliverable AS "deliverable", weight AS "weight",
                   status AS "status"
            FROM fin_project_milestone
            WHERE tenant_id = #{tenantId} AND project_id = #{projectId} AND deleted = 0
            ORDER BY plan_date, id
            """)
    List<Map<String, Object>> listMilestones(@Param("tenantId") long tenantId,
                                             @Param("projectId") long projectId);

    @Insert("""
            INSERT INTO fin_labor_rate
                (id, tenant_id, employee_id, member_role, cost_rate, effective_from, effective_to, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId,jdbcType=BIGINT}, #{memberRole,jdbcType=VARCHAR},
                    #{costRate}, #{effectiveFrom}, #{effectiveTo,jdbcType=DATE}, #{userId})
            """)
    void insertLaborRate(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("employeeId") Long employeeId, @Param("memberRole") String memberRole,
                         @Param("costRate") BigDecimal costRate, @Param("effectiveFrom") LocalDate effectiveFrom,
                         @Param("effectiveTo") LocalDate effectiveTo, @Param("userId") long userId);

    @Select("""
            SELECT cost_rate AS "costRate" FROM fin_labor_rate
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (employee_id = #{employeeId} OR member_role = #{memberRole,jdbcType=VARCHAR})
              AND effective_from <= #{workDate}
              AND (effective_to IS NULL OR effective_to >= #{workDate})
            ORDER BY (employee_id IS NOT NULL) DESC, effective_from DESC
            LIMIT 1
            """)
    BigDecimal findLaborRate(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId,
                             @Param("memberRole") String memberRole, @Param("workDate") LocalDate workDate);

    @Select("""
            SELECT COALESCE(SUM(hours), 0) FROM fin_timesheet
            WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND work_date = #{workDate}
              AND deleted = 0
            """)
    BigDecimal sumHours(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId,
                        @Param("workDate") LocalDate workDate);

    @Insert("""
            INSERT INTO fin_timesheet
                (id, tenant_id, timesheet_no, employee_id, employee_name, project_id, milestone_id,
                 work_date, hours, task_desc, cost_rate, cost_amount, status, created_by)
            VALUES (#{id}, #{tenantId}, #{timesheetNo}, #{employeeId}, #{employeeName,jdbcType=VARCHAR},
                    #{projectId}, #{milestoneId,jdbcType=BIGINT}, #{workDate}, #{hours},
                    #{taskDesc,jdbcType=VARCHAR}, #{costRate}, #{costAmount}, 'SUBMITTED', #{userId})
            """)
    void insertTimesheet(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("timesheetNo") String timesheetNo, @Param("employeeId") long employeeId,
                         @Param("employeeName") String employeeName, @Param("projectId") long projectId,
                         @Param("milestoneId") Long milestoneId, @Param("workDate") LocalDate workDate,
                         @Param("hours") BigDecimal hours, @Param("taskDesc") String taskDesc,
                         @Param("costRate") BigDecimal costRate, @Param("costAmount") BigDecimal costAmount,
                         @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", timesheet_no AS "timesheetNo", employee_id AS "employeeId",
                   employee_name AS "employeeName", project_id AS "projectId", milestone_id AS "milestoneId",
                   work_date::text AS "workDate", hours AS "hours", task_desc AS "taskDesc",
                   cost_rate AS "costRate", cost_amount AS "costAmount", status AS "status",
                   approver_id AS "approverId", reject_reason AS "rejectReason"
            FROM fin_timesheet
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findTimesheet(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", timesheet_no AS "timesheetNo", employee_id AS "employeeId",
                   employee_name AS "employeeName", project_id AS "projectId", work_date::text AS "workDate",
                   hours AS "hours", cost_amount AS "costAmount", status AS "status"
            FROM fin_timesheet
            WHERE tenant_id = #{tenantId} AND project_id = #{projectId} AND deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY work_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listTimesheets(@Param("tenantId") long tenantId,
                                             @Param("projectId") long projectId,
                                             @Param("status") String status,
                                             @Param("limit") int limit, @Param("offset") int offset);

    /**
     * 员工自助：跨项目返回本人工时，tenant_id + employee_id 双重收敛。
     */
    @Select("""
            SELECT id AS "id", timesheet_no AS "timesheetNo", employee_id AS "employeeId",
                   employee_name AS "employeeName", project_id AS "projectId", work_date::text AS "workDate",
                   hours AS "hours", cost_rate AS "costRate", cost_amount AS "costAmount",
                   status AS "status", reject_reason AS "rejectReason"
            FROM fin_timesheet
            WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY work_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listTimesheetsByEmployee(@Param("tenantId") long tenantId,
                                                       @Param("employeeId") long employeeId,
                                                       @Param("status") String status,
                                                       @Param("limit") int limit,
                                                       @Param("offset") int offset);

    @Update("""
            UPDATE fin_timesheet SET status = 'APPROVED', approver_id = #{userId}, approved_at = now(),
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'SUBMITTED' AND deleted = 0
            """)
    int approveTimesheet(@Param("tenantId") long tenantId, @Param("id") long id,
                         @Param("userId") long userId);

    @Update("""
            UPDATE fin_timesheet SET status = 'REJECTED', approver_id = #{userId}, reject_reason = #{reason},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'SUBMITTED' AND deleted = 0
            """)
    int rejectTimesheet(@Param("tenantId") long tenantId, @Param("id") long id,
                        @Param("userId") long userId, @Param("reason") String reason);

    @Insert("""
            INSERT INTO fin_cost_collection
                (id, tenant_id, cost_no, project_id, cost_type, source_biz_id, subject_code, amount,
                 cost_date, description, created_by)
            VALUES (#{id}, #{tenantId}, #{costNo}, #{projectId}, #{costType}, #{sourceBizId,jdbcType=BIGINT},
                    #{subjectCode,jdbcType=VARCHAR}, #{amount}, #{costDate}, #{description,jdbcType=VARCHAR},
                    #{userId})
            """)
    int insertCost(@Param("id") long id, @Param("tenantId") long tenantId,
                   @Param("costNo") String costNo, @Param("projectId") long projectId,
                   @Param("costType") String costType, @Param("sourceBizId") Long sourceBizId,
                   @Param("subjectCode") String subjectCode, @Param("amount") BigDecimal amount,
                   @Param("costDate") LocalDate costDate, @Param("description") String description,
                   @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", cost_no AS "costNo", project_id AS "projectId", cost_type AS "costType",
                   source_biz_id AS "sourceBizId", subject_code AS "subjectCode", amount AS "amount",
                   cost_date::text AS "costDate", description AS "description"
            FROM fin_cost_collection
            WHERE tenant_id = #{tenantId} AND project_id = #{projectId} AND deleted = 0
            ORDER BY cost_date, id
            """)
    List<Map<String, Object>> listCosts(@Param("tenantId") long tenantId, @Param("projectId") long projectId);

    @Insert("""
            INSERT INTO fin_project_settlement
                (id, tenant_id, settlement_no, project_id, settlement_amount, settled_amount,
                 currency_code, status, created_by)
            VALUES (#{id}, #{tenantId}, #{settlementNo}, #{projectId}, #{settlementAmount}, 0,
                    #{currencyCode}, 'APPROVING', #{userId})
            """)
    void insertSettlement(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("settlementNo") String settlementNo, @Param("projectId") long projectId,
                          @Param("settlementAmount") BigDecimal settlementAmount,
                          @Param("currencyCode") String currencyCode, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", settlement_no AS "settlementNo", project_id AS "projectId",
                   settlement_amount AS "settlementAmount", settled_amount AS "settledAmount",
                   currency_code AS "currencyCode", receivable_id AS "receivableId", voucher_id AS "voucherId",
                   status AS "status", (extract(epoch FROM settled_at) * 1000)::bigint AS "settledAt"
            FROM fin_project_settlement
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findSettlement(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", settlement_no AS "settlementNo", settlement_amount AS "settlementAmount",
                   status AS "status", receivable_id AS "receivableId", settled_at AS "settledAt"
            FROM fin_project_settlement
            WHERE tenant_id = #{tenantId} AND project_id = #{projectId} AND deleted = 0
            ORDER BY id DESC
            """)
    List<Map<String, Object>> listSettlements(@Param("tenantId") long tenantId,
                                              @Param("projectId") long projectId);

    @Update("""
            UPDATE fin_project_settlement
            SET status = 'SETTLED', settled_amount = settlement_amount, receivable_id = #{receivableId},
                voucher_id = #{voucherId}, settled_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'APPROVING' AND deleted = 0
            """)
    int settleSettlement(@Param("tenantId") long tenantId, @Param("id") long id,
                         @Param("receivableId") long receivableId, @Param("voucherId") Long voucherId);

    @Select("""
            SELECT COALESCE(SUM(settled_amount), 0) FROM fin_project_settlement
            WHERE tenant_id = #{tenantId} AND project_id = #{projectId} AND status = 'SETTLED'
              AND deleted = 0
            """)
    BigDecimal sumSettled(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
}
