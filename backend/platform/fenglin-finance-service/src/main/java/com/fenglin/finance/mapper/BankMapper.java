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
 * D1 银行账户、流水、银企对账 Mapper。
 */
@Mapper
public interface BankMapper {

    @Insert("""
            INSERT INTO fin_bank_account
                (id, tenant_id, account_no_enc, account_no_hash, account_name, bank_name, bank_code,
                 currency_code, account_type, subject_code, balance, status, created_by)
            VALUES (#{id}, #{tenantId}, #{accountNoEnc}, #{accountNoHash}, #{accountName}, #{bankName},
                    #{bankCode,jdbcType=VARCHAR}, #{currencyCode}, #{accountType}, #{subjectCode}, #{balance},
                    'ENABLED', #{userId})
            """)
    void insertAccount(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("accountNoEnc") String accountNoEnc, @Param("accountNoHash") String accountNoHash,
                       @Param("accountName") String accountName, @Param("bankName") String bankName,
                       @Param("bankCode") String bankCode, @Param("currencyCode") String currencyCode,
                       @Param("accountType") String accountType, @Param("subjectCode") String subjectCode,
                       @Param("balance") BigDecimal balance, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", account_no_enc AS "accountNoEnc", account_name AS "accountName",
                   bank_name AS "bankName", bank_code AS "bankCode", currency_code AS "currencyCode",
                   account_type AS "accountType", subject_code AS "subjectCode", balance AS "balance",
                   status AS "status"
            FROM fin_bank_account
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findAccount(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", account_no_enc AS "accountNoEnc", account_name AS "accountName",
                   bank_name AS "bankName", currency_code AS "currencyCode", account_type AS "accountType",
                   subject_code AS "subjectCode", balance AS "balance", status AS "status"
            FROM fin_bank_account
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY id
            """)
    List<Map<String, Object>> listAccounts(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO fin_bank_transaction
                (id, tenant_id, account_id, txn_no, txn_date, direction, amount, balance, counterparty,
                 summary, reconcile_status, created_by)
            VALUES (#{id}, #{tenantId}, #{accountId}, #{txnNo}, #{txnDate}, #{direction}, #{amount},
                    #{balance,jdbcType=NUMERIC}, #{counterparty,jdbcType=VARCHAR}, #{summary,jdbcType=VARCHAR},
                    'UNMATCHED', #{userId})
            ON CONFLICT (tenant_id, account_id, txn_no) WHERE deleted = 0 DO NOTHING
            """)
    int insertTransaction(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("accountId") long accountId, @Param("txnNo") String txnNo,
                          @Param("txnDate") LocalDate txnDate, @Param("direction") String direction,
                          @Param("amount") BigDecimal amount, @Param("balance") BigDecimal balance,
                          @Param("counterparty") String counterparty, @Param("summary") String summary,
                          @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", account_id AS "accountId", txn_no AS "txnNo", txn_date::text AS "txnDate",
                   direction AS "direction", amount AS "amount", balance AS "balance",
                   counterparty AS "counterparty", summary AS "summary",
                   reconcile_status AS "reconcileStatus", recon_id AS "reconId"
            FROM fin_bank_transaction
            WHERE tenant_id = #{tenantId} AND account_id = #{accountId} AND deleted = 0
            ORDER BY txn_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listTransactions(@Param("tenantId") long tenantId,
                                               @Param("accountId") long accountId,
                                               @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE fin_bank_transaction t SET reconcile_status = 'MATCHED', recon_id = #{reconId},
                version = version + 1
            WHERE t.tenant_id = #{tenantId} AND t.account_id = #{accountId} AND t.deleted = 0
              AND t.reconcile_status = 'UNMATCHED'
              AND EXISTS (
                  SELECT 1 FROM fin_receipt r
                  WHERE r.tenant_id = t.tenant_id AND r.status = 'CONFIRMED'
                    AND r.bank_account_id = t.account_id AND t.direction = 'IN'
                    AND r.amount = t.amount AND abs(r.receipt_date - t.txn_date) <= 3 AND r.deleted = 0
                  UNION ALL
                  SELECT 1 FROM fin_payment p
                  WHERE p.tenant_id = t.tenant_id AND p.status = 'CONFIRMED'
                    AND p.bank_account_id = t.account_id AND t.direction = 'OUT'
                    AND p.amount = t.amount AND abs(p.payment_date - t.txn_date) <= 3 AND p.deleted = 0
              )
            """)
    int markMatched(@Param("tenantId") long tenantId, @Param("accountId") long accountId,
                    @Param("reconId") long reconId);

    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM fin_bank_transaction
            WHERE tenant_id = #{tenantId} AND account_id = #{accountId} AND direction = #{direction}
              AND reconcile_status = 'UNMATCHED' AND deleted = 0
            """)
    BigDecimal sumUnmatched(@Param("tenantId") long tenantId, @Param("accountId") long accountId,
                            @Param("direction") String direction);

    @Select("""
            SELECT count(1) FROM fin_bank_transaction
            WHERE tenant_id = #{tenantId} AND account_id = #{accountId} AND reconcile_status = 'UNMATCHED'
              AND deleted = 0
            """)
    long countUnmatched(@Param("tenantId") long tenantId, @Param("accountId") long accountId);

    @Insert("""
            INSERT INTO fin_reconciliation
                (id, tenant_id, recon_no, account_id, period_code, book_balance, bank_balance,
                 unsettled_in, unsettled_out, adjusted_balance, status, operator_id, created_by)
            VALUES (#{id}, #{tenantId}, #{reconNo}, #{accountId}, #{periodCode}, #{bookBalance}, #{bankBalance},
                    #{unsettledIn}, #{unsettledOut}, #{adjustedBalance}, #{status}, #{userId}, #{userId})
            """)
    void insertRecon(@Param("id") long id, @Param("tenantId") long tenantId,
                     @Param("reconNo") String reconNo, @Param("accountId") long accountId,
                     @Param("periodCode") String periodCode, @Param("bookBalance") BigDecimal bookBalance,
                     @Param("bankBalance") BigDecimal bankBalance, @Param("unsettledIn") BigDecimal unsettledIn,
                     @Param("unsettledOut") BigDecimal unsettledOut,
                     @Param("adjustedBalance") BigDecimal adjustedBalance, @Param("status") String status,
                     @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", recon_no AS "reconNo", account_id AS "accountId", period_code AS "periodCode",
                   book_balance AS "bookBalance", bank_balance AS "bankBalance", unsettled_in AS "unsettledIn",
                   unsettled_out AS "unsettledOut", adjusted_balance AS "adjustedBalance", status AS "status"
            FROM fin_reconciliation
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findRecon(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", recon_no AS "reconNo", account_id AS "accountId", period_code AS "periodCode",
                   book_balance AS "bookBalance", bank_balance AS "bankBalance", adjusted_balance AS "adjustedBalance",
                   status AS "status"
            FROM fin_reconciliation
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listRecons(@Param("tenantId") long tenantId,
                                         @Param("limit") int limit, @Param("offset") int offset);
}
