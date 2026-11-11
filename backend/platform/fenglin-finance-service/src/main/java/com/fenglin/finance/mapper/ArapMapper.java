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
 * D1 应收、应付、收付款、核销 Mapper。
 */
@Mapper
public interface ArapMapper {

    @Insert("""
            INSERT INTO fin_receivable
                (id, tenant_id, ar_no, customer_id, customer_name, source_type, source_biz_id, project_id,
                 contract_no, currency_code, amount, settled_amount, bill_date, due_date, status, created_by)
            VALUES (#{id}, #{tenantId}, #{arNo}, #{customerId,jdbcType=BIGINT}, #{customerName,jdbcType=VARCHAR},
                    #{sourceType}, #{sourceBizId,jdbcType=BIGINT}, #{projectId,jdbcType=BIGINT},
                    #{contractNo,jdbcType=VARCHAR}, #{currencyCode}, #{amount}, 0, #{billDate}, #{dueDate},
                    'OPEN', #{userId})
            """)
    void insertReceivable(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("arNo") String arNo, @Param("customerId") Long customerId,
                          @Param("customerName") String customerName, @Param("sourceType") String sourceType,
                          @Param("sourceBizId") Long sourceBizId, @Param("projectId") Long projectId,
                          @Param("contractNo") String contractNo, @Param("currencyCode") String currencyCode,
                          @Param("amount") BigDecimal amount, @Param("billDate") LocalDate billDate,
                          @Param("dueDate") LocalDate dueDate, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", ar_no AS "arNo", customer_id AS "customerId", customer_name AS "customerName",
                   source_type AS "sourceType", source_biz_id AS "sourceBizId", project_id AS "projectId",
                   contract_no AS "contractNo", currency_code AS "currencyCode", amount AS "amount",
                   settled_amount AS "settledAmount", (amount - settled_amount) AS "outstanding",
                   bill_date::text AS "billDate", due_date::text AS "dueDate", aging_bucket AS "agingBucket",
                   status AS "status"
            FROM fin_receivable
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findReceivable(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", ar_no AS "arNo", customer_id AS "customerId", customer_name AS "customerName",
                   source_type AS "sourceType", project_id AS "projectId", currency_code AS "currencyCode",
                   amount AS "amount", settled_amount AS "settledAmount", (amount - settled_amount) AS "outstanding",
                   bill_date::text AS "billDate", due_date::text AS "dueDate", aging_bucket AS "agingBucket",
                   status AS "status"
            FROM fin_receivable
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY due_date, id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listReceivables(@Param("tenantId") long tenantId,
                                              @Param("status") String status,
                                              @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT id AS "id", ar_no AS "arNo", customer_id AS "customerId", customer_name AS "customerName",
                   currency_code AS "currencyCode", amount AS "amount", settled_amount AS "settledAmount",
                   (amount - settled_amount) AS "outstanding", status AS "status"
            FROM fin_receivable
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            FOR UPDATE
            """)
    Map<String, Object> lockReceivable(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE fin_receivable
            SET settled_amount = settled_amount + #{delta}, status = #{status}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateReceivableSettled(@Param("tenantId") long tenantId, @Param("id") long id,
                                @Param("delta") BigDecimal delta, @Param("status") String status);

    @Insert("""
            INSERT INTO fin_payable
                (id, tenant_id, ap_no, supplier_id, supplier_name, source_type, source_biz_id, project_id,
                 currency_code, amount, settled_amount, bill_date, due_date, planned_pay_date, status, created_by)
            VALUES (#{id}, #{tenantId}, #{apNo}, #{supplierId,jdbcType=BIGINT}, #{supplierName,jdbcType=VARCHAR},
                    #{sourceType}, #{sourceBizId,jdbcType=BIGINT}, #{projectId,jdbcType=BIGINT},
                    #{currencyCode}, #{amount}, 0, #{billDate}, #{dueDate}, #{plannedPayDate,jdbcType=DATE},
                    'OPEN', #{userId})
            """)
    void insertPayable(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("apNo") String apNo, @Param("supplierId") Long supplierId,
                       @Param("supplierName") String supplierName, @Param("sourceType") String sourceType,
                       @Param("sourceBizId") Long sourceBizId, @Param("projectId") Long projectId,
                       @Param("currencyCode") String currencyCode, @Param("amount") BigDecimal amount,
                       @Param("billDate") LocalDate billDate, @Param("dueDate") LocalDate dueDate,
                       @Param("plannedPayDate") LocalDate plannedPayDate, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", ap_no AS "apNo", supplier_id AS "supplierId", supplier_name AS "supplierName",
                   source_type AS "sourceType", source_biz_id AS "sourceBizId", project_id AS "projectId",
                   currency_code AS "currencyCode", amount AS "amount", settled_amount AS "settledAmount",
                   (amount - settled_amount) AS "outstanding", bill_date::text AS "billDate",
                   due_date::text AS "dueDate", status AS "status"
            FROM fin_payable
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findPayable(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", ap_no AS "apNo", supplier_id AS "supplierId", supplier_name AS "supplierName",
                   source_type AS "sourceType", project_id AS "projectId", currency_code AS "currencyCode",
                   amount AS "amount", settled_amount AS "settledAmount", (amount - settled_amount) AS "outstanding",
                   bill_date::text AS "billDate", due_date::text AS "dueDate", status AS "status"
            FROM fin_payable
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY due_date, id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listPayables(@Param("tenantId") long tenantId,
                                           @Param("status") String status,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT id AS "id", ap_no AS "apNo", supplier_id AS "supplierId", supplier_name AS "supplierName",
                   currency_code AS "currencyCode", amount AS "amount", settled_amount AS "settledAmount",
                   (amount - settled_amount) AS "outstanding", status AS "status"
            FROM fin_payable
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            FOR UPDATE
            """)
    Map<String, Object> lockPayable(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE fin_payable
            SET settled_amount = settled_amount + #{delta}, status = #{status}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updatePayableSettled(@Param("tenantId") long tenantId, @Param("id") long id,
                             @Param("delta") BigDecimal delta, @Param("status") String status);

    @Insert("""
            INSERT INTO fin_receipt
                (id, tenant_id, receipt_no, customer_id, currency_code, amount, pay_method, bank_account_id,
                 receipt_date, status, idempotency_key, created_by)
            VALUES (#{id}, #{tenantId}, #{receiptNo}, #{customerId,jdbcType=BIGINT}, #{currencyCode}, #{amount},
                    #{payMethod}, #{bankAccountId,jdbcType=BIGINT}, #{receiptDate}, 'DRAFT',
                    #{idempotencyKey,jdbcType=VARCHAR}, #{userId})
            """)
    void insertReceipt(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("receiptNo") String receiptNo, @Param("customerId") Long customerId,
                       @Param("currencyCode") String currencyCode, @Param("amount") BigDecimal amount,
                       @Param("payMethod") String payMethod, @Param("bankAccountId") Long bankAccountId,
                       @Param("receiptDate") LocalDate receiptDate, @Param("idempotencyKey") String idempotencyKey,
                       @Param("userId") long userId);

    @Update("""
            UPDATE fin_receipt SET status = 'CONFIRMED', voucher_id = #{voucherId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int confirmReceipt(@Param("tenantId") long tenantId, @Param("id") long id,
                       @Param("voucherId") Long voucherId);

    @Select("""
            SELECT id AS "id", receipt_no AS "receiptNo", customer_id AS "customerId",
                   currency_code AS "currencyCode", amount AS "amount", pay_method AS "payMethod",
                   receipt_date::text AS "receiptDate", voucher_id AS "voucherId", status AS "status"
            FROM fin_receipt WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findReceipt(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO fin_payment
                (id, tenant_id, payment_no, supplier_id, currency_code, amount, pay_method, bank_account_id,
                 payment_date, status, idempotency_key, created_by)
            VALUES (#{id}, #{tenantId}, #{paymentNo}, #{supplierId,jdbcType=BIGINT}, #{currencyCode}, #{amount},
                    #{payMethod}, #{bankAccountId,jdbcType=BIGINT}, #{paymentDate}, 'DRAFT',
                    #{idempotencyKey,jdbcType=VARCHAR}, #{userId})
            """)
    void insertPayment(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("paymentNo") String paymentNo, @Param("supplierId") Long supplierId,
                       @Param("currencyCode") String currencyCode, @Param("amount") BigDecimal amount,
                       @Param("payMethod") String payMethod, @Param("bankAccountId") Long bankAccountId,
                       @Param("paymentDate") LocalDate paymentDate, @Param("idempotencyKey") String idempotencyKey,
                       @Param("userId") long userId);

    @Update("""
            UPDATE fin_payment SET status = 'CONFIRMED', voucher_id = #{voucherId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int confirmPayment(@Param("tenantId") long tenantId, @Param("id") long id,
                       @Param("voucherId") Long voucherId);

    @Select("""
            SELECT id AS "id", payment_no AS "paymentNo", supplier_id AS "supplierId",
                   currency_code AS "currencyCode", amount AS "amount", pay_method AS "payMethod",
                   payment_date::text AS "paymentDate", voucher_id AS "voucherId", status AS "status"
            FROM fin_payment WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findPayment(@Param("tenantId") long tenantId, @Param("id") long id);

    @Insert("""
            INSERT INTO fin_arap_write_off
                (id, tenant_id, write_off_no, biz_type, source_id, settle_doc_type, settle_doc_id,
                 currency_code, amount, write_off_date, operator_id, created_by)
            VALUES (#{id}, #{tenantId}, #{writeOffNo}, #{bizType}, #{sourceId}, #{settleDocType}, #{settleDocId},
                    #{currencyCode}, #{amount}, #{writeOffDate}, #{userId}, #{userId})
            """)
    void insertWriteOff(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("writeOffNo") String writeOffNo, @Param("bizType") String bizType,
                        @Param("sourceId") long sourceId, @Param("settleDocType") String settleDocType,
                        @Param("settleDocId") long settleDocId, @Param("currencyCode") String currencyCode,
                        @Param("amount") BigDecimal amount, @Param("writeOffDate") LocalDate writeOffDate,
                        @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", write_off_no AS "writeOffNo", biz_type AS "bizType", source_id AS "sourceId",
                   settle_doc_type AS "settleDocType", settle_doc_id AS "settleDocId",
                   amount AS "amount", write_off_date::text AS "writeOffDate"
            FROM fin_arap_write_off
            WHERE tenant_id = #{tenantId} AND biz_type = #{bizType} AND source_id = #{sourceId}
              AND deleted = 0
            ORDER BY id
            """)
    List<Map<String, Object>> listWriteOffs(@Param("tenantId") long tenantId,
                                            @Param("bizType") String bizType,
                                            @Param("sourceId") long sourceId);

    @Select("""
            <script>
            SELECT r.id AS "id", r.receipt_no AS "receiptNo", r.customer_id AS "customerId",
                   r.currency_code AS "currencyCode", r.amount AS "amount", r.pay_method AS "payMethod",
                   to_char(r.receipt_date, 'YYYY-MM-DD') AS "receiptDate", r.voucher_id AS "voucherId",
                   r.status AS "status", extract(epoch FROM r.created_at) * 1000 AS "createdAt"
            FROM fin_receipt r
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR r.status = #{status})
              AND (CAST(#{fromDate,jdbcType=VARCHAR} AS text) IS NULL OR r.receipt_date >= CAST(#{fromDate} AS date))
              AND (CAST(#{toDate,jdbcType=VARCHAR} AS text) IS NULL OR r.receipt_date &lt;= CAST(#{toDate} AS date))
            ORDER BY r.receipt_date DESC, r.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> listReceipts(@Param("tenantId") long tenantId, @Param("status") String status,
                                           @Param("fromDate") String fromDate, @Param("toDate") String toDate,
                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.id AS "id", p.payment_no AS "paymentNo", p.supplier_id AS "supplierId",
                   p.currency_code AS "currencyCode", p.amount AS "amount", p.pay_method AS "payMethod",
                   to_char(p.payment_date, 'YYYY-MM-DD') AS "paymentDate", p.voucher_id AS "voucherId",
                   p.status AS "status", extract(epoch FROM p.created_at) * 1000 AS "createdAt"
            FROM fin_payment p
            WHERE p.tenant_id = #{tenantId} AND p.deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR p.status = #{status})
              AND (CAST(#{fromDate,jdbcType=VARCHAR} AS text) IS NULL OR p.payment_date >= CAST(#{fromDate} AS date))
              AND (CAST(#{toDate,jdbcType=VARCHAR} AS text) IS NULL OR p.payment_date &lt;= CAST(#{toDate} AS date))
            ORDER BY p.payment_date DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> listPayments(@Param("tenantId") long tenantId, @Param("status") String status,
                                           @Param("fromDate") String fromDate, @Param("toDate") String toDate,
                                           @Param("limit") int limit);

    @Select("""
            SELECT COALESCE(customer_name, '未指定客户') AS "partyName",
                   CASE
                       WHEN due_date >= current_date THEN 'LE_30'
                       WHEN current_date - due_date <= 30 THEN 'LE_30'
                       WHEN current_date - due_date <= 60 THEN 'B31_60'
                       WHEN current_date - due_date <= 90 THEN 'B61_90'
                       WHEN current_date - due_date <= 180 THEN 'B91_180'
                       ELSE 'GT_180'
                   END AS "bucket",
                   SUM(amount - settled_amount) AS "amount"
            FROM fin_receivable
            WHERE tenant_id = #{tenantId} AND status IN ('OPEN', 'PARTIAL') AND deleted = 0
            GROUP BY 1, 2
            ORDER BY 1, 2
            """)
    List<Map<String, Object>> receivableAging(@Param("tenantId") long tenantId);

    @Select("""
            SELECT COALESCE(supplier_name, '未指定供应商') AS "partyName",
                   CASE
                       WHEN due_date >= current_date THEN 'LE_30'
                       WHEN current_date - due_date <= 30 THEN 'LE_30'
                       WHEN current_date - due_date <= 60 THEN 'B31_60'
                       WHEN current_date - due_date <= 90 THEN 'B61_90'
                       WHEN current_date - due_date <= 180 THEN 'B91_180'
                       ELSE 'GT_180'
                   END AS "bucket",
                   SUM(amount - settled_amount) AS "amount"
            FROM fin_payable
            WHERE tenant_id = #{tenantId} AND status IN ('OPEN', 'PARTIAL') AND deleted = 0
            GROUP BY 1, 2
            ORDER BY 1, 2
            """)
    List<Map<String, Object>> payableAging(@Param("tenantId") long tenantId);

    @Select("""
            SELECT count(1) FROM fin_receivable
            WHERE tenant_id = #{tenantId} AND status IN ('OPEN', 'PARTIAL') AND deleted = 0
            """)
    long countOpenReceivables(@Param("tenantId") long tenantId);

    @Select("""
            SELECT count(1) FROM fin_payable
            WHERE tenant_id = #{tenantId} AND status IN ('OPEN', 'PARTIAL') AND deleted = 0
            """)
    long countOpenPayables(@Param("tenantId") long tenantId);
}
