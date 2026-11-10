package com.fenglin.finance.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * D1 报销、预算、发票 Mapper。
 */
@Mapper
public interface ExpenseMapper {

    @Insert("""
            INSERT INTO fin_expense_claim
                (id, tenant_id, claim_no, claim_date, applicant_id, applicant_name, dept_id, claim_type,
                 currency_code, total_amount, tax_amount, project_id, status, idempotency_key, created_by)
            VALUES (#{id}, #{tenantId}, #{claimNo}, #{claimDate}, #{applicantId}, #{applicantName},
                    #{deptId,jdbcType=BIGINT}, #{claimType}, #{currencyCode}, #{totalAmount}, #{taxAmount},
                    #{projectId,jdbcType=BIGINT}, 'DRAFT', #{idempotencyKey,jdbcType=VARCHAR}, #{userId})
            """)
    void insertClaim(@Param("id") long id, @Param("tenantId") long tenantId,
                     @Param("claimNo") String claimNo, @Param("claimDate") LocalDate claimDate,
                     @Param("applicantId") long applicantId, @Param("applicantName") String applicantName,
                     @Param("deptId") Long deptId, @Param("claimType") String claimType,
                     @Param("currencyCode") String currencyCode, @Param("totalAmount") BigDecimal totalAmount,
                     @Param("taxAmount") BigDecimal taxAmount, @Param("projectId") Long projectId,
                     @Param("idempotencyKey") String idempotencyKey, @Param("userId") long userId);

    @Insert("""
            INSERT INTO fin_expense_claim_item
                (id, tenant_id, claim_id, line_no, expense_date, expense_category, subject_code, amount,
                 tax_amount, invoice_id, attachment_id, remark, created_by)
            VALUES (#{id}, #{tenantId}, #{claimId}, #{lineNo}, #{expenseDate}, #{expenseCategory}, #{subjectCode},
                    #{amount}, #{taxAmount}, #{invoiceId,jdbcType=BIGINT}, #{attachmentId,jdbcType=BIGINT},
                    #{remark,jdbcType=VARCHAR}, #{userId})
            """)
    void insertClaimItem(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("claimId") long claimId, @Param("lineNo") int lineNo,
                         @Param("expenseDate") LocalDate expenseDate,
                         @Param("expenseCategory") String expenseCategory,
                         @Param("subjectCode") String subjectCode, @Param("amount") BigDecimal amount,
                         @Param("taxAmount") BigDecimal taxAmount, @Param("invoiceId") Long invoiceId,
                         @Param("attachmentId") Long attachmentId, @Param("remark") String remark,
                         @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", claim_no AS "claimNo", claim_date::text AS "claimDate",
                   applicant_id AS "applicantId", applicant_name AS "applicantName", dept_id AS "deptId",
                   claim_type AS "claimType", currency_code AS "currencyCode", total_amount AS "totalAmount",
                   tax_amount AS "taxAmount", budget_id AS "budgetId", budget_occupied AS "budgetOccupied",
                   project_id AS "projectId", status AS "status", payable_id AS "payableId",
                   voucher_id AS "voucherId",
                   (extract(epoch FROM submitted_at) * 1000)::bigint AS "submittedAt",
                   (extract(epoch FROM paid_at) * 1000)::bigint AS "paidAt"
            FROM fin_expense_claim
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findClaim(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", claim_no AS "claimNo", claim_date::text AS "claimDate",
                   applicant_id AS "applicantId", applicant_name AS "applicantName", claim_type AS "claimType",
                   total_amount AS "totalAmount", status AS "status", budget_id AS "budgetId",
                   budget_occupied AS "budgetOccupied", payable_id AS "payableId", voucher_id AS "voucherId"
            FROM fin_expense_claim
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY claim_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listClaims(@Param("tenantId") long tenantId,
                                         @Param("status") String status,
                                         @Param("limit") int limit, @Param("offset") int offset);

    /**
     * 员工自助：只返回申请人本人的报销单，tenant_id + applicant_id 双重收敛。
     */
    @Select("""
            SELECT id AS "id", claim_no AS "claimNo", claim_date::text AS "claimDate",
                   applicant_id AS "applicantId", applicant_name AS "applicantName", dept_id AS "deptId",
                   claim_type AS "claimType", currency_code AS "currencyCode", total_amount AS "totalAmount",
                   tax_amount AS "taxAmount", budget_id AS "budgetId", budget_occupied AS "budgetOccupied",
                   project_id AS "projectId", status AS "status", payable_id AS "payableId",
                   voucher_id AS "voucherId",
                   (extract(epoch FROM submitted_at) * 1000)::bigint AS "submittedAt",
                   (extract(epoch FROM paid_at) * 1000)::bigint AS "paidAt"
            FROM fin_expense_claim
            WHERE tenant_id = #{tenantId} AND applicant_id = #{applicantId} AND deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY claim_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listClaimsByApplicant(@Param("tenantId") long tenantId,
                                                    @Param("applicantId") long applicantId,
                                                    @Param("status") String status,
                                                    @Param("limit") int limit,
                                                    @Param("offset") int offset);

    @Select("""
            SELECT id AS "id", line_no AS "lineNo", expense_date::text AS "expenseDate",
                   expense_category AS "expenseCategory", subject_code AS "subjectCode",
                   amount AS "amount", tax_amount AS "taxAmount", invoice_id AS "invoiceId",
                   attachment_id AS "attachmentId", remark AS "remark"
            FROM fin_expense_claim_item
            WHERE tenant_id = #{tenantId} AND claim_id = #{claimId} AND deleted = 0
            ORDER BY line_no
            """)
    List<Map<String, Object>> listClaimItems(@Param("tenantId") long tenantId,
                                             @Param("claimId") long claimId);

    @Update("""
            UPDATE fin_expense_claim
            SET status = 'SUBMITTED', budget_id = #{budgetId,jdbcType=BIGINT},
                budget_occupied = #{occupied}, submitted_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'DRAFT' AND deleted = 0
            """)
    int markClaimSubmitted(@Param("tenantId") long tenantId, @Param("id") long id,
                           @Param("budgetId") Long budgetId, @Param("occupied") BigDecimal occupied);

    @Update("""
            UPDATE fin_expense_claim SET status = 'APPROVED', payable_id = #{payableId},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'SUBMITTED' AND deleted = 0
            """)
    int markClaimApproved(@Param("tenantId") long tenantId, @Param("id") long id,
                          @Param("payableId") long payableId);

    @Update("""
            UPDATE fin_expense_claim SET status = 'REJECTED', budget_occupied = 0, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status IN ('SUBMITTED', 'APPROVING')
              AND deleted = 0
            """)
    int markClaimRejected(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE fin_expense_claim SET status = 'PAID', voucher_id = #{voucherId}, paid_at = now(),
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'APPROVED' AND deleted = 0
            """)
    int markClaimPaid(@Param("tenantId") long tenantId, @Param("id") long id,
                      @Param("voucherId") Long voucherId);

    @Insert("""
            INSERT INTO fin_budget
                (id, tenant_id, budget_no, budget_name, period_year, dimension, dimension_id, subject_code,
                 currency_code, budget_amount, occupied_amount, executed_amount, control_mode, status, created_by)
            VALUES (#{id}, #{tenantId}, #{budgetNo}, #{budgetName}, #{periodYear}, #{dimension},
                    #{dimensionId,jdbcType=BIGINT}, #{subjectCode,jdbcType=VARCHAR}, #{currencyCode},
                    #{budgetAmount}, 0, 0, #{controlMode}, 'DRAFT', #{userId})
            """)
    void insertBudget(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("budgetNo") String budgetNo, @Param("budgetName") String budgetName,
                      @Param("periodYear") int periodYear, @Param("dimension") String dimension,
                      @Param("dimensionId") Long dimensionId, @Param("subjectCode") String subjectCode,
                      @Param("currencyCode") String currencyCode, @Param("budgetAmount") BigDecimal budgetAmount,
                      @Param("controlMode") String controlMode, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", budget_no AS "budgetNo", budget_name AS "budgetName",
                   period_year AS "periodYear", dimension AS "dimension", dimension_id AS "dimensionId",
                   subject_code AS "subjectCode", currency_code AS "currencyCode",
                   budget_amount AS "budgetAmount", occupied_amount AS "occupiedAmount",
                   executed_amount AS "executedAmount",
                   (budget_amount - occupied_amount - executed_amount) AS "available",
                   control_mode AS "controlMode", status AS "status"
            FROM fin_budget
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findBudget(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", budget_no AS "budgetNo", budget_name AS "budgetName",
                   period_year AS "periodYear", dimension AS "dimension", dimension_id AS "dimensionId",
                   subject_code AS "subjectCode", currency_code AS "currencyCode",
                   budget_amount AS "budgetAmount", occupied_amount AS "occupiedAmount",
                   executed_amount AS "executedAmount",
                   (budget_amount - occupied_amount - executed_amount) AS "available",
                   control_mode AS "controlMode", status AS "status"
            FROM fin_budget
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{periodYear,jdbcType=INTEGER} AS integer) IS NULL OR period_year = #{periodYear})
              AND (CAST(#{dimension,jdbcType=VARCHAR} AS text) IS NULL OR dimension = #{dimension})
            ORDER BY period_year DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listBudgets(@Param("tenantId") long tenantId,
                                          @Param("periodYear") Integer periodYear,
                                          @Param("dimension") String dimension,
                                          @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT id AS "id", budget_amount AS "budgetAmount", occupied_amount AS "occupiedAmount",
                   executed_amount AS "executedAmount",
                   (budget_amount - occupied_amount - executed_amount) AS "available",
                   control_mode AS "controlMode", status AS "status"
            FROM fin_budget
            WHERE tenant_id = #{tenantId} AND period_year = #{periodYear} AND dimension = #{dimension}
              AND (dimension_id = #{dimensionId,jdbcType=BIGINT} OR dimension_id IS NULL)
              AND (subject_code IS NULL OR #{subjectCode} LIKE subject_code || '%')
              AND status = 'RELEASED' AND deleted = 0
            ORDER BY (subject_code IS NOT NULL) DESC, dimension_id NULLS LAST, id
            LIMIT 1
            """)
    Map<String, Object> findBudgetForUse(@Param("tenantId") long tenantId,
                                         @Param("periodYear") int periodYear,
                                         @Param("dimension") String dimension,
                                         @Param("dimensionId") Long dimensionId,
                                         @Param("subjectCode") String subjectCode);

    @Update("""
            UPDATE fin_budget SET status = 'RELEASED', version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status IN ('DRAFT', 'APPROVING')
              AND deleted = 0
            """)
    int releaseBudget(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE fin_budget SET occupied_amount = occupied_amount + #{amount}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
              AND occupied_amount + executed_amount + #{amount} <= budget_amount
            """)
    int occupyBudget(@Param("tenantId") long tenantId, @Param("id") long id,
                     @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE fin_budget SET occupied_amount = occupied_amount - #{amount}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND occupied_amount >= #{amount} AND deleted = 0
            """)
    int releaseBudgetAmount(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE fin_budget SET occupied_amount = occupied_amount - #{amount},
                executed_amount = executed_amount + #{amount}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND occupied_amount >= #{amount} AND deleted = 0
            """)
    int consumeBudget(@Param("tenantId") long tenantId, @Param("id") long id,
                      @Param("amount") BigDecimal amount);

    @Insert("""
            INSERT INTO fin_budget_control
                (id, tenant_id, budget_id, biz_type, biz_id, action, amount, balance_after, occurred_at,
                 created_by)
            SELECT #{id}, #{tenantId}, #{budgetId}, #{bizType}, #{bizId}, #{action}, #{amount},
                   budget_amount - occupied_amount - executed_amount, #{occurredAt}, #{userId}
            FROM fin_budget
            WHERE tenant_id = #{tenantId} AND id = #{budgetId}
            """)
    void insertBudgetControl(@Param("id") long id, @Param("tenantId") long tenantId,
                             @Param("budgetId") long budgetId, @Param("bizType") String bizType,
                             @Param("bizId") long bizId, @Param("action") String action,
                             @Param("amount") BigDecimal amount,
                             @Param("occurredAt") OffsetDateTime occurredAt, @Param("userId") long userId);

    @Insert("""
            INSERT INTO fin_invoice
                (id, tenant_id, invoice_code, invoice_number, invoice_type, buyer_name, seller_name,
                 amount, tax_rate, tax_amount, total_amount, invoice_date, verify_status, status,
                 source_biz_id, file_id, created_by)
            VALUES (#{id}, #{tenantId}, #{invoiceCode,jdbcType=VARCHAR}, #{invoiceNumber}, #{invoiceType},
                    #{buyerName,jdbcType=VARCHAR}, #{sellerName,jdbcType=VARCHAR}, #{amount}, #{taxRate},
                    #{taxAmount}, #{totalAmount}, #{invoiceDate}, 'UNVERIFIED', #{status},
                    #{sourceBizId,jdbcType=BIGINT}, #{fileId,jdbcType=BIGINT}, #{userId})
            """)
    void insertInvoice(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("invoiceCode") String invoiceCode, @Param("invoiceNumber") String invoiceNumber,
                       @Param("invoiceType") String invoiceType, @Param("buyerName") String buyerName,
                       @Param("sellerName") String sellerName, @Param("amount") BigDecimal amount,
                       @Param("taxRate") BigDecimal taxRate, @Param("taxAmount") BigDecimal taxAmount,
                       @Param("totalAmount") BigDecimal totalAmount, @Param("invoiceDate") LocalDate invoiceDate,
                       @Param("status") String status, @Param("sourceBizId") Long sourceBizId,
                       @Param("fileId") Long fileId, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", invoice_code AS "invoiceCode", invoice_number AS "invoiceNumber",
                   invoice_type AS "invoiceType", buyer_name AS "buyerName", seller_name AS "sellerName",
                   amount AS "amount", tax_rate AS "taxRate", tax_amount AS "taxAmount",
                   total_amount AS "totalAmount", invoice_date::text AS "invoiceDate",
                   verify_status AS "verifyStatus", status AS "status", source_biz_id AS "sourceBizId"
            FROM fin_invoice
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findInvoice(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", invoice_code AS "invoiceCode", invoice_number AS "invoiceNumber",
                   invoice_type AS "invoiceType", buyer_name AS "buyerName", seller_name AS "sellerName",
                   amount AS "amount", tax_amount AS "taxAmount", total_amount AS "totalAmount",
                   invoice_date::text AS "invoiceDate", verify_status AS "verifyStatus", status AS "status"
            FROM fin_invoice
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{invoiceType,jdbcType=VARCHAR} AS text) IS NULL OR invoice_type = #{invoiceType})
            ORDER BY invoice_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listInvoices(@Param("tenantId") long tenantId,
                                           @Param("invoiceType") String invoiceType,
                                           @Param("limit") int limit, @Param("offset") int offset);
}
