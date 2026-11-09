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
 * D1 账套/科目/期间/结账快照 Mapper。
 */
@Mapper
public interface LedgerMapper {

    @Insert("""
            INSERT INTO fin_ledger
                (id, tenant_id, ledger_code, ledger_name, company_name, base_currency,
                 fiscal_year_start_month, accounting_standard, tax_no_enc, tax_no_hash, status, created_by)
            VALUES (#{id}, #{tenantId}, #{ledgerCode}, #{ledgerName}, #{companyName}, #{baseCurrency},
                    #{fiscalYearStartMonth}, #{accountingStandard}, #{taxNoEnc,jdbcType=VARCHAR},
                    #{taxNoHash,jdbcType=VARCHAR}, 'ENABLED', #{userId})
            """)
    void insertLedger(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("ledgerCode") String ledgerCode, @Param("ledgerName") String ledgerName,
                      @Param("companyName") String companyName, @Param("baseCurrency") String baseCurrency,
                      @Param("fiscalYearStartMonth") int fiscalYearStartMonth,
                      @Param("accountingStandard") String accountingStandard,
                      @Param("taxNoEnc") String taxNoEnc, @Param("taxNoHash") String taxNoHash,
                      @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", ledger_code AS "ledgerCode", ledger_name AS "ledgerName",
                   company_name AS "companyName", base_currency AS "baseCurrency",
                   fiscal_year_start_month AS "fiscalYearStartMonth",
                   accounting_standard AS "accountingStandard", status AS "status",
                   tax_no_enc AS "taxNoEnc", version AS "version"
            FROM fin_ledger
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findLedger(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", ledger_code AS "ledgerCode", ledger_name AS "ledgerName",
                   company_name AS "companyName", base_currency AS "baseCurrency",
                   accounting_standard AS "accountingStandard", status AS "status"
            FROM fin_ledger
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY ledger_code
            """)
    List<Map<String, Object>> listLedgers(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id FROM fin_ledger
            WHERE tenant_id = #{tenantId} AND ledger_code = #{code} AND deleted = 0
            """)
    Long findLedgerIdByCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Insert("""
            INSERT INTO fin_accounting_subject
                (id, tenant_id, ledger_id, subject_code, subject_name, parent_id, subject_level,
                 subject_category, balance_direction, is_leaf, auxiliary_types, status, created_by)
            VALUES (#{id}, #{tenantId}, #{ledgerId}, #{subjectCode}, #{subjectName},
                    #{parentId,jdbcType=BIGINT}, #{subjectLevel}, #{subjectCategory}, #{balanceDirection},
                    #{isLeaf}, #{auxiliaryTypes,jdbcType=VARCHAR}, 'ENABLED', #{userId})
            """)
    void insertSubject(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("ledgerId") long ledgerId, @Param("subjectCode") String subjectCode,
                       @Param("subjectName") String subjectName, @Param("parentId") Long parentId,
                       @Param("subjectLevel") int subjectLevel, @Param("subjectCategory") String subjectCategory,
                       @Param("balanceDirection") String balanceDirection, @Param("isLeaf") boolean isLeaf,
                       @Param("auxiliaryTypes") String auxiliaryTypes, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", ledger_id AS "ledgerId", subject_code AS "subjectCode",
                   subject_name AS "subjectName", parent_id AS "parentId", subject_level AS "subjectLevel",
                   subject_category AS "subjectCategory", balance_direction AS "balanceDirection",
                   is_leaf AS "isLeaf", auxiliary_types AS "auxiliaryTypes", status AS "status"
            FROM fin_accounting_subject
            WHERE tenant_id = #{tenantId} AND ledger_id = #{ledgerId} AND subject_code = #{subjectCode}
              AND deleted = 0
            """)
    Map<String, Object> findSubject(@Param("tenantId") long tenantId, @Param("ledgerId") long ledgerId,
                                    @Param("subjectCode") String subjectCode);

    @Select("""
            SELECT id AS "id", ledger_id AS "ledgerId", subject_code AS "subjectCode",
                   subject_name AS "subjectName", parent_id AS "parentId", subject_level AS "subjectLevel",
                   subject_category AS "subjectCategory", balance_direction AS "balanceDirection",
                   is_leaf AS "isLeaf", auxiliary_types AS "auxiliaryTypes", status AS "status"
            FROM fin_accounting_subject
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findSubjectById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", subject_code AS "subjectCode", subject_name AS "subjectName",
                   parent_id AS "parentId", subject_level AS "subjectLevel",
                   subject_category AS "subjectCategory", balance_direction AS "balanceDirection",
                   is_leaf AS "isLeaf", auxiliary_types AS "auxiliaryTypes", status AS "status"
            FROM fin_accounting_subject
            WHERE tenant_id = #{tenantId} AND ledger_id = #{ledgerId} AND deleted = 0
            ORDER BY subject_code
            """)
    List<Map<String, Object>> listSubjects(@Param("tenantId") long tenantId,
                                           @Param("ledgerId") long ledgerId);

    @Select("""
            SELECT count(1) FROM fin_accounting_subject
            WHERE tenant_id = #{tenantId} AND ledger_id = #{ledgerId} AND subject_code LIKE #{prefix}
              AND deleted = 0
            """)
    long countSubjectPrefix(@Param("tenantId") long tenantId, @Param("ledgerId") long ledgerId,
                            @Param("prefix") String prefix);

    @Insert("""
            INSERT INTO fin_accounting_period
                (id, tenant_id, ledger_id, period_year, period_month, period_code, start_date, end_date,
                 status, created_by)
            VALUES (#{id}, #{tenantId}, #{ledgerId}, #{periodYear}, #{periodMonth}, #{periodCode},
                    #{startDate}, #{endDate}, #{status}, #{userId})
            """)
    void insertPeriod(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("ledgerId") long ledgerId, @Param("periodYear") int periodYear,
                      @Param("periodMonth") int periodMonth, @Param("periodCode") String periodCode,
                      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                      @Param("status") String status, @Param("userId") long userId);

    @Update("""
            UPDATE fin_accounting_subject
            SET subject_name = #{subjectName}, subject_category = #{subjectCategory},
                balance_direction = #{balanceDirection}, is_leaf = #{isLeaf},
                auxiliary_types = #{auxiliaryTypes,jdbcType=VARCHAR}, version = version + 1
            WHERE tenant_id = #{tenantId} AND ledger_id = #{ledgerId} AND id = #{id} AND deleted = 0
            """)
    int updateSubject(@Param("tenantId") long tenantId, @Param("ledgerId") long ledgerId,
                      @Param("id") long id, @Param("subjectName") String subjectName,
                      @Param("subjectCategory") String subjectCategory,
                      @Param("balanceDirection") String balanceDirection, @Param("isLeaf") boolean isLeaf,
                      @Param("auxiliaryTypes") String auxiliaryTypes);

    @Update("""
            UPDATE fin_accounting_subject SET status = 'DISABLED', version = version + 1
            WHERE tenant_id = #{tenantId} AND ledger_id = #{ledgerId} AND id = #{id} AND deleted = 0
            """)
    int disableSubject(@Param("tenantId") long tenantId, @Param("ledgerId") long ledgerId,
                       @Param("id") long id);

    @Select("""
            SELECT id AS "id", ledger_id AS "ledgerId", period_year AS "periodYear",
                   period_month AS "periodMonth", period_code AS "periodCode",
                   start_date::text AS "startDate", end_date::text AS "endDate", status AS "status"
            FROM fin_accounting_period
            WHERE tenant_id = #{tenantId} AND ledger_id = #{ledgerId} AND deleted = 0
            ORDER BY period_code
            """)
    List<Map<String, Object>> listPeriods(@Param("tenantId") long tenantId,
                                          @Param("ledgerId") long ledgerId);

    @Select("""
            SELECT id AS "id", ledger_id AS "ledgerId", period_year AS "periodYear",
                   period_month AS "periodMonth", period_code AS "periodCode",
                   start_date::text AS "startDate", end_date::text AS "endDate", status AS "status"
            FROM fin_accounting_period
            WHERE tenant_id = #{tenantId} AND ledger_id = #{ledgerId} AND period_code = #{periodCode}
              AND deleted = 0
            """)
    Map<String, Object> findPeriod(@Param("tenantId") long tenantId, @Param("ledgerId") long ledgerId,
                                   @Param("periodCode") String periodCode);

    @Update("""
            UPDATE fin_accounting_period
            SET status = #{status}, closed_by = #{userId,jdbcType=BIGINT},
                closed_at = CASE WHEN #{status} = 'CLOSED' THEN now() ELSE NULL END, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updatePeriodStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                           @Param("status") String status, @Param("userId") Long userId);

    @Select("""
            SELECT record_hash AS "recordHash"
            FROM fin_period_close_snapshot
            WHERE tenant_id = #{tenantId} AND ledger_id = #{ledgerId} AND deleted = 0
            ORDER BY closed_at DESC, id DESC
            LIMIT 1
            """)
    String findLastSnapshotHash(@Param("tenantId") long tenantId, @Param("ledgerId") long ledgerId);

    @Insert("""
            INSERT INTO fin_period_close_snapshot
                (id, tenant_id, ledger_id, period_code, snapshot_type, trial_debit, trial_credit,
                 snapshot_json, record_hash, prev_hash, closed_by, closed_at, created_by)
            VALUES (#{id}, #{tenantId}, #{ledgerId}, #{periodCode}, 'CLOSE', #{trialDebit}, #{trialCredit},
                    CAST(#{snapshotJson} AS jsonb), #{recordHash}, #{prevHash}, #{userId}, now(), #{userId})
            """)
    void insertSnapshot(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("ledgerId") long ledgerId, @Param("periodCode") String periodCode,
                        @Param("trialDebit") BigDecimal trialDebit, @Param("trialCredit") BigDecimal trialCredit,
                        @Param("snapshotJson") String snapshotJson, @Param("recordHash") String recordHash,
                        @Param("prevHash") String prevHash, @Param("userId") long userId);
}
