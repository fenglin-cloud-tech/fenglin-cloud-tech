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
 * D1 凭证、分录、审计链、报表取数 Mapper。
 */
@Mapper
public interface VoucherMapper {

    @Insert("""
            INSERT INTO fin_voucher
                (id, tenant_id, ledger_id, voucher_word, voucher_no, period_code, voucher_date, summary,
                 currency_code, total_debit, total_credit, source_type, source_biz_id, status, created_by)
            VALUES (#{id}, #{tenantId}, #{ledgerId}, #{voucherWord}, #{voucherNo}, #{periodCode},
                    #{voucherDate}, #{summary}, #{currencyCode}, #{totalDebit}, #{totalCredit},
                    #{sourceType}, #{sourceBizId,jdbcType=BIGINT}, #{status}, #{userId})
            """)
    void insertVoucher(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("ledgerId") long ledgerId, @Param("voucherWord") String voucherWord,
                       @Param("voucherNo") String voucherNo, @Param("periodCode") String periodCode,
                       @Param("voucherDate") LocalDate voucherDate, @Param("summary") String summary,
                       @Param("currencyCode") String currencyCode, @Param("totalDebit") BigDecimal totalDebit,
                       @Param("totalCredit") BigDecimal totalCredit, @Param("sourceType") String sourceType,
                       @Param("sourceBizId") Long sourceBizId, @Param("status") String status,
                       @Param("userId") long userId);

    @Insert("""
            INSERT INTO fin_voucher_entry
                (id, tenant_id, voucher_id, period_code, line_no, summary, subject_code, subject_name,
                 direction, amount, currency_code, exchange_rate, amount_base, auxiliary, quantity, price,
                 created_by)
            VALUES (#{id}, #{tenantId}, #{voucherId}, #{periodCode}, #{lineNo}, #{summary}, #{subjectCode},
                    #{subjectName}, #{direction}, #{amount}, #{currencyCode}, #{exchangeRate}, #{amountBase},
                    CAST(#{auxiliary,jdbcType=VARCHAR} AS jsonb), #{quantity,jdbcType=NUMERIC},
                    #{price,jdbcType=NUMERIC}, #{userId})
            """)
    void insertEntry(@Param("id") long id, @Param("tenantId") long tenantId,
                     @Param("voucherId") long voucherId, @Param("periodCode") String periodCode,
                     @Param("lineNo") int lineNo, @Param("summary") String summary,
                     @Param("subjectCode") String subjectCode, @Param("subjectName") String subjectName,
                     @Param("direction") String direction, @Param("amount") BigDecimal amount,
                     @Param("currencyCode") String currencyCode, @Param("exchangeRate") BigDecimal exchangeRate,
                     @Param("amountBase") BigDecimal amountBase, @Param("auxiliary") String auxiliary,
                     @Param("quantity") BigDecimal quantity, @Param("price") BigDecimal price,
                     @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", ledger_id AS "ledgerId", voucher_word AS "voucherWord",
                   voucher_no AS "voucherNo", period_code AS "periodCode",
                   voucher_date::text AS "voucherDate", summary AS "summary", currency_code AS "currencyCode",
                   total_debit AS "totalDebit", total_credit AS "totalCredit", status AS "status",
                   source_type AS "sourceType", source_biz_id AS "sourceBizId",
                   audited_by AS "auditedBy", audited_at AS "auditedAt", posted_by AS "postedBy",
                   posted_at AS "postedAt", reverse_of AS "reverseOf", reversed_by AS "reversedBy",
                   chain_hash AS "chainHash", created_by AS "createdBy", version AS "version"
            FROM fin_voucher
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findVoucher(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", line_no AS "lineNo", summary AS "summary", subject_code AS "subjectCode",
                   subject_name AS "subjectName", direction AS "direction", amount AS "amount",
                   currency_code AS "currencyCode", exchange_rate AS "exchangeRate",
                   amount_base AS "amountBase", auxiliary::text AS "auxiliary",
                   quantity AS "quantity", price AS "price"
            FROM fin_voucher_entry
            WHERE tenant_id = #{tenantId} AND voucher_id = #{voucherId} AND deleted = 0
            ORDER BY line_no
            """)
    List<Map<String, Object>> listEntries(@Param("tenantId") long tenantId,
                                          @Param("voucherId") long voucherId);

    @Select("""
            SELECT id AS "id", voucher_no AS "voucherNo", period_code AS "periodCode",
                   voucher_date::text AS "voucherDate", summary AS "summary", total_debit AS "totalDebit",
                   total_credit AS "totalCredit", status AS "status", source_type AS "sourceType",
                   chain_hash AS "chainHash"
            FROM fin_voucher
            WHERE tenant_id = #{tenantId} AND ledger_id = #{ledgerId} AND deleted = 0
              AND (CAST(#{periodCode,jdbcType=VARCHAR} AS text) IS NULL OR period_code = #{periodCode})
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR status = #{status})
            ORDER BY voucher_date DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listVouchers(@Param("tenantId") long tenantId,
                                           @Param("ledgerId") long ledgerId,
                                           @Param("periodCode") String periodCode,
                                           @Param("status") String status,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM fin_voucher
            WHERE tenant_id = #{tenantId} AND ledger_id = #{ledgerId} AND period_code = #{periodCode}
              AND status IN ('DRAFT', 'SUBMITTED', 'AUDITED') AND deleted = 0
            """)
    long countUnposted(@Param("tenantId") long tenantId, @Param("ledgerId") long ledgerId,
                       @Param("periodCode") String periodCode);

    @Update("""
            UPDATE fin_voucher SET status = 'SUBMITTED', version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'DRAFT' AND deleted = 0
            """)
    int markSubmitted(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE fin_voucher SET status = 'AUDITED', audited_by = #{userId}, audited_at = now(),
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'SUBMITTED' AND deleted = 0
            """)
    int markAudited(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Update("""
            UPDATE fin_voucher SET status = 'POSTED', posted_by = #{userId}, posted_at = now(),
                chain_hash = #{chainHash,jdbcType=VARCHAR}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'AUDITED' AND deleted = 0
            """)
    int markPosted(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId,
                   @Param("chainHash") String chainHash);

    @Update("""
            UPDATE fin_voucher SET status = 'REVERSED', reversed_by = #{reverseId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'POSTED' AND deleted = 0
            """)
    int markReversed(@Param("tenantId") long tenantId, @Param("id") long id,
                     @Param("reverseId") long reverseId);

    @Update("""
            UPDATE fin_voucher SET chain_hash = #{chainHash}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateChainHash(@Param("tenantId") long tenantId, @Param("id") long id,
                        @Param("chainHash") String chainHash);

    @Insert("""
            INSERT INTO fin_voucher_audit_log
                (id, tenant_id, voucher_id, chain_seq, action, operator_id, operator_name, occurred_at,
                 record_hash, prev_hash, signature, created_by)
            VALUES (#{id}, #{tenantId}, #{voucherId}, #{chainSeq}, #{action}, #{operatorId}, #{operatorName},
                    #{occurredAt}, #{recordHash}, #{prevHash}, #{signature}, #{operatorId})
            """)
    void insertAuditLog(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("voucherId") long voucherId, @Param("chainSeq") long chainSeq,
                        @Param("action") String action, @Param("operatorId") long operatorId,
                        @Param("operatorName") String operatorName, @Param("occurredAt") OffsetDateTime occurredAt,
                        @Param("recordHash") String recordHash, @Param("prevHash") String prevHash,
                        @Param("signature") String signature);

    @Select("""
            SELECT record_hash AS "recordHash" FROM fin_voucher_audit_log
            WHERE tenant_id = #{tenantId} AND voucher_id = #{voucherId} AND deleted = 0
            ORDER BY chain_seq DESC LIMIT 1
            """)
    Map<String, Object> findLastAuditLog(@Param("tenantId") long tenantId,
                                         @Param("voucherId") long voucherId);

    @Select("""
            SELECT id AS "id", chain_seq AS "chainSeq", action AS "action", operator_id AS "operatorId",
                   operator_name AS "operatorName",
                   (extract(epoch FROM occurred_at) * 1000)::bigint AS "occurredAt",
                   record_hash AS "recordHash", prev_hash AS "prevHash", signature AS "signature"
            FROM fin_voucher_audit_log
            WHERE tenant_id = #{tenantId} AND voucher_id = #{voucherId} AND deleted = 0
            ORDER BY chain_seq
            """)
    List<Map<String, Object>> listAuditLogs(@Param("tenantId") long tenantId,
                                            @Param("voucherId") long voucherId);

    @Select("""
            SELECT s.subject_code AS "subjectCode", s.subject_name AS "subjectName",
                   s.subject_category AS "subjectCategory", s.balance_direction AS "balanceDirection",
                   COALESCE(SUM(CASE WHEN e.period_code < #{periodCode}
                       THEN CASE WHEN e.direction = 'DEBIT' THEN e.amount_base ELSE -e.amount_base END
                       ELSE 0 END), 0) AS "opening",
                   COALESCE(SUM(CASE WHEN e.period_code = #{periodCode} AND e.direction = 'DEBIT'
                       THEN e.amount_base ELSE 0 END), 0) AS "periodDebit",
                   COALESCE(SUM(CASE WHEN e.period_code = #{periodCode} AND e.direction = 'CREDIT'
                       THEN e.amount_base ELSE 0 END), 0) AS "periodCredit"
            FROM fin_accounting_subject s
            LEFT JOIN fin_voucher_entry e
                ON e.tenant_id = s.tenant_id AND e.subject_code = s.subject_code AND e.deleted = 0
               AND EXISTS (SELECT 1 FROM fin_voucher v WHERE v.id = e.voucher_id AND v.tenant_id = e.tenant_id
                           AND v.status = 'POSTED' AND v.ledger_id = s.ledger_id AND v.deleted = 0)
            WHERE s.tenant_id = #{tenantId} AND s.ledger_id = #{ledgerId} AND s.deleted = 0
            GROUP BY s.subject_code, s.subject_name, s.subject_category, s.balance_direction
            ORDER BY s.subject_code
            """)
    List<Map<String, Object>> trialBalance(@Param("tenantId") long tenantId,
                                           @Param("ledgerId") long ledgerId,
                                           @Param("periodCode") String periodCode);

    @Select("""
            SELECT s.subject_code AS "subjectCode", s.subject_name AS "subjectName",
                   s.subject_category AS "subjectCategory", s.balance_direction AS "balanceDirection",
                   COALESCE(SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount_base ELSE -e.amount_base END), 0)
                       AS "balance"
            FROM fin_voucher_entry e
            JOIN fin_voucher v ON v.id = e.voucher_id AND v.tenant_id = e.tenant_id
                AND v.status = 'POSTED' AND v.deleted = 0
            JOIN fin_accounting_subject s ON s.tenant_id = e.tenant_id AND s.ledger_id = v.ledger_id
                AND s.subject_code = e.subject_code AND s.deleted = 0
            WHERE e.tenant_id = #{tenantId} AND v.ledger_id = #{ledgerId} AND e.period_code <= #{periodCode}
              AND e.deleted = 0
            GROUP BY s.subject_code, s.subject_name, s.subject_category, s.balance_direction
            ORDER BY s.subject_code
            """)
    List<Map<String, Object>> subjectBalances(@Param("tenantId") long tenantId,
                                              @Param("ledgerId") long ledgerId,
                                              @Param("periodCode") String periodCode);

    @Select("""
            SELECT s.subject_code AS "subjectCode", s.subject_name AS "subjectName",
                   s.subject_category AS "subjectCategory", s.balance_direction AS "balanceDirection",
                   COALESCE(SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount_base ELSE 0 END), 0)
                       AS "periodDebit",
                   COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount_base ELSE 0 END), 0)
                       AS "periodCredit"
            FROM fin_voucher_entry e
            JOIN fin_voucher v ON v.id = e.voucher_id AND v.tenant_id = e.tenant_id
                AND v.status = 'POSTED' AND v.deleted = 0
            JOIN fin_accounting_subject s ON s.tenant_id = e.tenant_id AND s.ledger_id = v.ledger_id
                AND s.subject_code = e.subject_code AND s.deleted = 0
            WHERE e.tenant_id = #{tenantId} AND v.ledger_id = #{ledgerId} AND e.period_code = #{periodCode}
              AND e.deleted = 0
            GROUP BY s.subject_code, s.subject_name, s.subject_category, s.balance_direction
            ORDER BY s.subject_code
            """)
    List<Map<String, Object>> periodSubjectSums(@Param("tenantId") long tenantId,
                                                @Param("ledgerId") long ledgerId,
                                                @Param("periodCode") String periodCode);
}
