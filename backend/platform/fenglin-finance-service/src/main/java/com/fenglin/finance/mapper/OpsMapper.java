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
 * 云财收口：账簿、凭证模板、发票、币种汇率、业财规则与付款计划 Mapper。
 */
@Mapper
public interface OpsMapper {

    @Select("""
            SELECT COALESCE(SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount_base ELSE -e.amount_base END), 0)
            FROM fin_voucher_entry e
            JOIN fin_voucher v ON v.id = e.voucher_id AND v.tenant_id = e.tenant_id
                AND v.status = 'POSTED' AND v.deleted = 0
            WHERE e.tenant_id = #{tenantId} AND v.ledger_id = #{ledgerId}
              AND e.subject_code = #{subjectCode} AND e.period_code < #{periodCode} AND e.deleted = 0
            """)
    BigDecimal subjectOpening(@Param("tenantId") long tenantId, @Param("ledgerId") long ledgerId,
                              @Param("subjectCode") String subjectCode, @Param("periodCode") String periodCode);

    @Select("""
            SELECT COALESCE(SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount_base ELSE -e.amount_base END), 0)
            FROM fin_voucher_entry e
            JOIN fin_voucher v ON v.id = e.voucher_id AND v.tenant_id = e.tenant_id
                AND v.status = 'POSTED' AND v.deleted = 0
            WHERE e.tenant_id = #{tenantId} AND v.ledger_id = #{ledgerId}
              AND e.subject_code = #{subjectCode} AND e.period_code <= #{periodCode} AND e.deleted = 0
            """)
    BigDecimal subjectClosing(@Param("tenantId") long tenantId, @Param("ledgerId") long ledgerId,
                              @Param("subjectCode") String subjectCode, @Param("periodCode") String periodCode);

    @Select("""
            SELECT v.voucher_no AS "voucherNo", v.voucher_date::text AS "voucherDate",
                   e.line_no AS "lineNo", e.summary AS "summary", e.direction AS "direction",
                   e.amount_base AS "amount"
            FROM fin_voucher_entry e
            JOIN fin_voucher v ON v.id = e.voucher_id AND v.tenant_id = e.tenant_id
                AND v.status = 'POSTED' AND v.deleted = 0
            WHERE e.tenant_id = #{tenantId} AND v.ledger_id = #{ledgerId}
              AND e.subject_code = #{subjectCode} AND e.period_code <= #{periodCode} AND e.deleted = 0
            ORDER BY v.voucher_date, v.voucher_no, e.line_no
            """)
    List<Map<String, Object>> listSubjectEntries(@Param("tenantId") long tenantId,
                                                 @Param("ledgerId") long ledgerId,
                                                 @Param("subjectCode") String subjectCode,
                                                 @Param("periodCode") String periodCode);

    @Select("""
            SELECT count(1)
            FROM fin_voucher_entry e
            JOIN fin_voucher v ON v.id = e.voucher_id AND v.tenant_id = e.tenant_id AND v.deleted = 0
            WHERE e.tenant_id = #{tenantId} AND v.ledger_id = #{ledgerId}
              AND e.subject_code = #{subjectCode} AND e.deleted = 0
              AND v.status IN ('DRAFT', 'SUBMITTED', 'AUDITED')
            """)
    long countUnpostedBySubject(@Param("tenantId") long tenantId, @Param("ledgerId") long ledgerId,
                                @Param("subjectCode") String subjectCode);

    // ---------- 凭证模板 ----------

    @Insert("""
            INSERT INTO fin_voucher_template
                (id, tenant_id, template_code, template_name, biz_type, debit_subject_code,
                 credit_subject_code, auxiliary_mapping, condition_expr, status, created_by)
            VALUES (#{id}, #{tenantId}, #{templateCode}, #{templateName}, #{bizType}, #{debitSubjectCode},
                    #{creditSubjectCode}, CAST(#{auxiliaryMapping} AS jsonb), #{conditionExpr,jdbcType=VARCHAR},
                    'ENABLED', #{userId})
            """)
    void insertTemplate(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("templateCode") String templateCode, @Param("templateName") String templateName,
                        @Param("bizType") String bizType, @Param("debitSubjectCode") String debitSubjectCode,
                        @Param("creditSubjectCode") String creditSubjectCode,
                        @Param("auxiliaryMapping") String auxiliaryMapping,
                        @Param("conditionExpr") String conditionExpr, @Param("userId") long userId);

    @Update("""
            UPDATE fin_voucher_template
            SET template_name = #{templateName}, biz_type = #{bizType},
                debit_subject_code = #{debitSubjectCode}, credit_subject_code = #{creditSubjectCode},
                auxiliary_mapping = CAST(#{auxiliaryMapping} AS jsonb),
                condition_expr = #{conditionExpr,jdbcType=VARCHAR}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateTemplate(@Param("tenantId") long tenantId, @Param("id") long id,
                       @Param("templateName") String templateName, @Param("bizType") String bizType,
                       @Param("debitSubjectCode") String debitSubjectCode,
                       @Param("creditSubjectCode") String creditSubjectCode,
                       @Param("auxiliaryMapping") String auxiliaryMapping,
                       @Param("conditionExpr") String conditionExpr);

    @Select("""
            SELECT id AS "id", template_code AS "templateCode", template_name AS "templateName",
                   biz_type AS "bizType", debit_subject_code AS "debitSubjectCode",
                   credit_subject_code AS "creditSubjectCode",
                   auxiliary_mapping::text AS "auxiliaryMapping", condition_expr AS "conditionExpr",
                   status AS "status"
            FROM fin_voucher_template
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findTemplate(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", template_code AS "templateCode", template_name AS "templateName",
                   biz_type AS "bizType", debit_subject_code AS "debitSubjectCode",
                   credit_subject_code AS "creditSubjectCode",
                   auxiliary_mapping::text AS "auxiliaryMapping", condition_expr AS "conditionExpr",
                   status AS "status"
            FROM fin_voucher_template
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY template_code
            """)
    List<Map<String, Object>> listTemplates(@Param("tenantId") long tenantId);

    @Update("""
            UPDATE fin_voucher_template SET status = #{status}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateTemplateStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                             @Param("status") String status);

    // ---------- 发票 ----------

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

    @Update("""
            UPDATE fin_invoice SET verify_status = 'VERIFIED', status = 'VERIFIED', version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int markInvoiceVerified(@Param("tenantId") long tenantId, @Param("id") long id);

    // ---------- 币种与汇率 ----------

    @Select("""
            SELECT id AS "id", currency_code AS "currencyCode", currency_name AS "currencyName",
                   symbol AS "symbol", decimal_places AS "decimalPlaces", status AS "status"
            FROM fin_currency
            WHERE tenant_id = #{tenantId} AND currency_code = #{currencyCode} AND deleted = 0
            """)
    Map<String, Object> findCurrency(@Param("tenantId") long tenantId,
                                     @Param("currencyCode") String currencyCode);

    @Insert("""
            INSERT INTO fin_currency
                (id, tenant_id, currency_code, currency_name, symbol, decimal_places, status, created_by)
            VALUES (#{id}, #{tenantId}, #{currencyCode}, #{currencyName}, #{symbol,jdbcType=VARCHAR},
                    #{decimalPlaces}, 'ENABLED', #{userId})
            """)
    void insertCurrency(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("currencyCode") String currencyCode, @Param("currencyName") String currencyName,
                        @Param("symbol") String symbol, @Param("decimalPlaces") int decimalPlaces,
                        @Param("userId") long userId);

    @Update("""
            UPDATE fin_currency SET currency_name = #{currencyName}, symbol = #{symbol,jdbcType=VARCHAR},
                decimal_places = #{decimalPlaces}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateCurrency(@Param("tenantId") long tenantId, @Param("id") long id,
                       @Param("currencyName") String currencyName, @Param("symbol") String symbol,
                       @Param("decimalPlaces") int decimalPlaces);

    @Select("""
            SELECT id AS "id", currency_code AS "currencyCode", currency_name AS "currencyName",
                   symbol AS "symbol", decimal_places AS "decimalPlaces", status AS "status"
            FROM fin_currency
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY currency_code
            """)
    List<Map<String, Object>> listCurrencies(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id AS "id", from_currency AS "fromCurrency", to_currency AS "toCurrency",
                   rate AS "rate", effective_date::text AS "effectiveDate", source AS "source"
            FROM fin_exchange_rate
            WHERE tenant_id = #{tenantId} AND from_currency = #{fromCurrency}
              AND to_currency = #{toCurrency} AND effective_date = #{effectiveDate} AND deleted = 0
            """)
    Map<String, Object> findExchangeRate(@Param("tenantId") long tenantId,
                                         @Param("fromCurrency") String fromCurrency,
                                         @Param("toCurrency") String toCurrency,
                                         @Param("effectiveDate") LocalDate effectiveDate);

    @Insert("""
            INSERT INTO fin_exchange_rate
                (id, tenant_id, from_currency, to_currency, rate, effective_date, source, created_by)
            VALUES (#{id}, #{tenantId}, #{fromCurrency}, #{toCurrency}, #{rate}, #{effectiveDate},
                    #{source,jdbcType=VARCHAR}, #{userId})
            """)
    void insertExchangeRate(@Param("id") long id, @Param("tenantId") long tenantId,
                            @Param("fromCurrency") String fromCurrency, @Param("toCurrency") String toCurrency,
                            @Param("rate") BigDecimal rate, @Param("effectiveDate") LocalDate effectiveDate,
                            @Param("source") String source, @Param("userId") long userId);

    @Update("""
            UPDATE fin_exchange_rate SET rate = #{rate}, source = #{source,jdbcType=VARCHAR},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateExchangeRate(@Param("tenantId") long tenantId, @Param("id") long id,
                           @Param("rate") BigDecimal rate, @Param("source") String source);

    @Select("""
            SELECT id AS "id", from_currency AS "fromCurrency", to_currency AS "toCurrency",
                   rate AS "rate", effective_date::text AS "effectiveDate", source AS "source"
            FROM fin_exchange_rate
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY from_currency, to_currency, effective_date DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listExchangeRates(@Param("tenantId") long tenantId,
                                                @Param("limit") int limit);

    // ---------- 业财规则 ----------

    @Select("""
            SELECT id AS "id", rule_code AS "ruleCode", rule_name AS "ruleName", rule_type AS "ruleType",
                   config_json::text AS "configJson", status AS "status"
            FROM fin_business_rule
            WHERE tenant_id = #{tenantId} AND rule_code = #{ruleCode} AND deleted = 0
            """)
    Map<String, Object> findRule(@Param("tenantId") long tenantId, @Param("ruleCode") String ruleCode);

    @Insert("""
            INSERT INTO fin_business_rule
                (id, tenant_id, rule_code, rule_name, rule_type, config_json, status, created_by)
            VALUES (#{id}, #{tenantId}, #{ruleCode}, #{ruleName}, #{ruleType},
                    CAST(#{configJson} AS jsonb), 'ENABLED', #{userId})
            """)
    void insertRule(@Param("id") long id, @Param("tenantId") long tenantId,
                    @Param("ruleCode") String ruleCode, @Param("ruleName") String ruleName,
                    @Param("ruleType") String ruleType, @Param("configJson") String configJson,
                    @Param("userId") long userId);

    @Update("""
            UPDATE fin_business_rule SET rule_name = #{ruleName}, rule_type = #{ruleType},
                config_json = CAST(#{configJson} AS jsonb), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateRule(@Param("tenantId") long tenantId, @Param("id") long id,
                   @Param("ruleName") String ruleName, @Param("ruleType") String ruleType,
                   @Param("configJson") String configJson);

    @Select("""
            SELECT id AS "id", rule_code AS "ruleCode", rule_name AS "ruleName", rule_type AS "ruleType",
                   config_json::text AS "configJson", status AS "status"
            FROM fin_business_rule
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY rule_code
            """)
    List<Map<String, Object>> listRules(@Param("tenantId") long tenantId);

    // ---------- 付款计划 ----------

    @Select("""
            SELECT id AS "payableId", ap_no AS "apNo", supplier_name AS "supplierName",
                   currency_code AS "currencyCode", due_date::text AS "dueDate",
                   planned_pay_date::text AS "plannedPayDate", amount AS "amount",
                   settled_amount AS "settledAmount", (amount - settled_amount) AS "outstanding"
            FROM fin_payable
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND status IN ('OPEN', 'PARTIAL')
              AND (amount - settled_amount) > 0
            ORDER BY COALESCE(planned_pay_date, due_date), id
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listPaymentPlan(@Param("tenantId") long tenantId,
                                              @Param("limit") int limit);
}
