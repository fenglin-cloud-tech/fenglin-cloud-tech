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
 * 薪酬：薪资项 / 周期 / 核算明细 / 发放单访问。
 * 周期与发放单状态流转均带前置状态条件，避免并发下越权流转。
 */
@Mapper
public interface PayrollMapper {

    // ---------- 薪资项 ----------

    @Select("""
            SELECT id AS "id", item_code AS "itemCode", item_name AS "itemName", item_type AS "itemType",
                   calc_type AS "calcType", amount AS "amount", taxable AS "taxable", sort_no AS "sortNo",
                   item_status AS "itemStatus", version AS "version"
            FROM fenglin_talent.talent_pay_item
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY sort_no, id
            """)
    List<Map<String, Object>> listItems(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id AS "id", item_code AS "itemCode", item_name AS "itemName", item_type AS "itemType",
                   calc_type AS "calcType", amount AS "amount", taxable AS "taxable", sort_no AS "sortNo",
                   item_status AS "itemStatus", version AS "version"
            FROM fenglin_talent.talent_pay_item
            WHERE tenant_id = #{tenantId} AND item_code = #{code} AND deleted = 0
            """)
    Map<String, Object> findItemByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Insert("""
            INSERT INTO fenglin_talent.talent_pay_item
                (id, tenant_id, item_code, item_name, item_type, calc_type, amount, taxable, sort_no,
                 item_status, remark, created_by)
            VALUES (#{id}, #{tenantId}, #{itemCode}, #{itemName}, #{itemType}, #{calcType}, #{amount},
                    #{taxable}, #{sortNo}, #{itemStatus}, #{remark}, #{userId})
            """)
    void insertItem(@Param("id") long id, @Param("tenantId") long tenantId, @Param("itemCode") String itemCode,
                    @Param("itemName") String itemName, @Param("itemType") String itemType,
                    @Param("calcType") String calcType, @Param("amount") BigDecimal amount,
                    @Param("taxable") int taxable, @Param("sortNo") int sortNo,
                    @Param("itemStatus") String itemStatus, @Param("remark") String remark,
                    @Param("userId") long userId);

    // ---------- 薪资周期 ----------

    @Select("""
            SELECT id AS "id", period_code AS "periodCode",
                   to_char(start_date, 'YYYY-MM-DD') AS "startDate",
                   to_char(end_date, 'YYYY-MM-DD') AS "endDate",
                   to_char(pay_date, 'YYYY-MM-DD') AS "payDate",
                   period_status AS "periodStatus", employee_count AS "employeeCount",
                   gross_total AS "grossTotal", net_total AS "netTotal", version AS "version"
            FROM fenglin_talent.talent_pay_period
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findPeriod(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id" FROM fenglin_talent.talent_pay_period
            WHERE tenant_id = #{tenantId} AND period_code = #{code} AND deleted = 0
            """)
    Map<String, Object> findPeriodByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Insert("""
            INSERT INTO fenglin_talent.talent_pay_period
                (id, tenant_id, period_code, start_date, end_date, pay_date, period_status, created_by)
            VALUES (#{id}, #{tenantId}, #{periodCode}, #{startDate}, #{endDate}, #{payDate}, 'DRAFT', #{userId})
            """)
    void insertPeriod(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("periodCode") String periodCode, @Param("startDate") LocalDate startDate,
                      @Param("endDate") LocalDate endDate, @Param("payDate") LocalDate payDate,
                      @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_pay_period
            SET period_status = 'CALCULATED', employee_count = #{employeeCount}, gross_total = #{grossTotal},
                net_total = #{netTotal}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND period_status = #{expected} AND deleted = 0
            """)
    int updateCalculated(@Param("tenantId") long tenantId, @Param("id") long id,
                         @Param("expected") String expectedStatus, @Param("employeeCount") int employeeCount,
                         @Param("grossTotal") BigDecimal grossTotal, @Param("netTotal") BigDecimal netTotal,
                         @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_pay_period
            SET period_status = #{status}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND period_status = #{expected} AND deleted = 0
            """)
    int updatePeriodStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                           @Param("expected") String expectedStatus, @Param("status") String status,
                           @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id AS "id", period_code AS "periodCode",
                   to_char(start_date, 'YYYY-MM-DD') AS "startDate",
                   to_char(end_date, 'YYYY-MM-DD') AS "endDate",
                   to_char(pay_date, 'YYYY-MM-DD') AS "payDate",
                   period_status AS "periodStatus", employee_count AS "employeeCount",
                   gross_total AS "grossTotal", net_total AS "netTotal", version AS "version"
            FROM fenglin_talent.talent_pay_period
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY period_code DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listPeriods(@Param("tenantId") long tenantId,
                                          @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_pay_period
            WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    long countPeriods(@Param("tenantId") long tenantId);

    // ---------- 核算 ----------

    @Select("""
            <script>
            SELECT id AS "id", name AS "name", coalesce(base_salary, 0) AS "baseSalary"
            FROM fenglin_talent.talent_employee
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND emp_status IN ('ACTIVE', 'PROBATION')
              AND hire_date &lt;= #{endDate}
              AND (leave_date IS NULL OR leave_date &gt;= #{startDate})
            ORDER BY id
            </script>
            """)
    List<Map<String, Object>> employeesForPeriod(@Param("tenantId") long tenantId,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    @Update("""
            UPDATE fenglin_talent.talent_payroll_record
            SET deleted = 1, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND period_id = #{periodId} AND deleted = 0
            """)
    int deleteRecords(@Param("tenantId") long tenantId, @Param("periodId") long periodId,
                      @Param("userId") long userId);

    @Insert("""
            INSERT INTO fenglin_talent.talent_payroll_record
                (id, tenant_id, period_id, employee_id, base_salary, earning_total, deduction_total,
                 gross_pay, net_pay, calc_detail, rec_status, created_by)
            VALUES (#{id}, #{tenantId}, #{periodId}, #{employeeId}, #{baseSalary}, #{earningTotal},
                    #{deductionTotal}, #{grossPay}, #{netPay}, CAST(#{calcDetail} AS jsonb), 'CALCULATED',
                    #{userId})
            """)
    void insertRecord(@Param("id") long id, @Param("tenantId") long tenantId, @Param("periodId") long periodId,
                      @Param("employeeId") long employeeId, @Param("baseSalary") BigDecimal baseSalary,
                      @Param("earningTotal") BigDecimal earningTotal,
                      @Param("deductionTotal") BigDecimal deductionTotal,
                      @Param("grossPay") BigDecimal grossPay, @Param("netPay") BigDecimal netPay,
                      @Param("calcDetail") String calcDetail, @Param("userId") long userId);

    @Select("""
            SELECT r.id AS "id", r.period_id AS "periodId", r.employee_id AS "employeeId",
                   e.name AS "employeeName", r.base_salary AS "baseSalary", r.earning_total AS "earningTotal",
                   r.deduction_total AS "deductionTotal", r.gross_pay AS "grossPay", r.net_pay AS "netPay",
                   r.rec_status AS "recStatus"
            FROM fenglin_talent.talent_payroll_record r
            LEFT JOIN fenglin_talent.talent_employee e ON e.id = r.employee_id AND e.tenant_id = r.tenant_id
                AND e.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.period_id = #{periodId} AND r.deleted = 0
            ORDER BY r.employee_id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listRecords(@Param("tenantId") long tenantId, @Param("periodId") long periodId,
                                          @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_payroll_record
            WHERE tenant_id = #{tenantId} AND period_id = #{periodId} AND deleted = 0
            """)
    long countRecords(@Param("tenantId") long tenantId, @Param("periodId") long periodId);

    // ---------- 发放单 ----------

    @Select("""
            SELECT 'PS' || to_char(now(), 'YYYYMM')
                   || lpad(nextval('fenglin_talent.talent_payslip_no_seq')::text, 6, '0')
            """)
    String nextSlipNo();

    @Select("""
            SELECT s.id AS "id", s.slip_no AS "slipNo", s.period_id AS "periodId", p.period_code AS "periodCode",
                   s.employee_count AS "employeeCount", s.gross_total AS "grossTotal", s.net_total AS "netTotal",
                   s.slip_status AS "slipStatus", s.process_instance_id AS "processInstanceId",
                   s.approve_opinion AS "approveOpinion",
                   extract(epoch FROM s.created_at) * 1000 AS "createdAt"
            FROM fenglin_talent.talent_payslip s
            JOIN fenglin_talent.talent_pay_period p ON p.id = s.period_id AND p.tenant_id = s.tenant_id
            WHERE s.tenant_id = #{tenantId} AND s.id = #{id} AND s.deleted = 0
            """)
    Map<String, Object> findSlip(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id" FROM fenglin_talent.talent_payslip
            WHERE tenant_id = #{tenantId} AND period_id = #{periodId} AND deleted = 0
            """)
    Map<String, Object> findSlipByPeriod(@Param("tenantId") long tenantId, @Param("periodId") long periodId);

    @Insert("""
            INSERT INTO fenglin_talent.talent_payslip
                (id, tenant_id, period_id, slip_no, employee_count, gross_total, net_total, slip_status,
                 process_instance_id, created_by)
            VALUES (#{id}, #{tenantId}, #{periodId}, #{slipNo}, #{employeeCount}, #{grossTotal}, #{netTotal},
                    'APPROVING', #{processInstanceId}, #{userId})
            """)
    void insertSlip(@Param("id") long id, @Param("tenantId") long tenantId, @Param("periodId") long periodId,
                    @Param("slipNo") String slipNo, @Param("employeeCount") int employeeCount,
                    @Param("grossTotal") BigDecimal grossTotal, @Param("netTotal") BigDecimal netTotal,
                    @Param("processInstanceId") String processInstanceId, @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_payslip
            SET slip_status = #{status}, approved_by = #{userId}, approved_at = now(),
                approve_opinion = #{opinion}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND slip_status = 'APPROVING' AND deleted = 0
            """)
    int updateSlipApproval(@Param("tenantId") long tenantId, @Param("id") long id,
                           @Param("status") String status, @Param("opinion") String opinion,
                           @Param("userId") long userId);

    @Update("""
            UPDATE fenglin_talent.talent_payslip
            SET slip_status = 'PAID', paid_at = now(), updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND slip_status = 'APPROVED' AND deleted = 0
            """)
    int markPaid(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT s.id AS "id", s.slip_no AS "slipNo", s.period_id AS "periodId", p.period_code AS "periodCode",
                   s.employee_count AS "employeeCount", s.gross_total AS "grossTotal", s.net_total AS "netTotal",
                   s.slip_status AS "slipStatus", s.process_instance_id AS "processInstanceId",
                   s.approve_opinion AS "approveOpinion",
                   extract(epoch FROM s.created_at) * 1000 AS "createdAt"
            FROM fenglin_talent.talent_payslip s
            JOIN fenglin_talent.talent_pay_period p ON p.id = s.period_id AND p.tenant_id = s.tenant_id
            WHERE s.tenant_id = #{tenantId} AND s.deleted = 0
            ORDER BY s.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listSlips(@Param("tenantId") long tenantId,
                                        @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM fenglin_talent.talent_payslip
            WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    long countSlips(@Param("tenantId") long tenantId);
}
