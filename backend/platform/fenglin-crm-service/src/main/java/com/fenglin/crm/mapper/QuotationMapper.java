package com.fenglin.crm.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface QuotationMapper {

    String VIEW_COLUMNS = " id AS \"quotationId\", quotation_no AS \"quotationNo\", "
            + "opportunity_id AS \"opportunityId\", customer_id AS \"customerId\", version_no AS \"versionNo\", "
            + "total_amount AS \"totalAmount\", discount_rate AS \"discountRate\", tax_rate AS \"taxRate\", "
            + "to_char(valid_until, 'YYYY-MM-DD') AS \"validUntil\", status AS \"status\", "
            + "approve_instance_id AS \"approveInstanceId\", "
            + "(EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS \"createdMs\" ";

    @Select("SELECT nextval('fenglin_crm.crm_quotation_no_seq')")
    long nextQuotationSeq();

    @Insert("INSERT INTO crm_quotation (id, tenant_id, quotation_no, opportunity_id, customer_id, version_no, "
            + "total_amount, discount_rate, tax_rate, currency, valid_until, status, owner_id, remark, "
            + "created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{quotationNo}, #{opportunityId}, #{customerId}, #{versionNo}, "
            + "#{totalAmount}, #{discountRate}, #{taxRate}, 'CNY', #{validUntil}, 'DRAFT', #{ownerId}, #{remark}, "
            + "#{operatorId}, #{operatorId})")
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("quotationNo") String quotationNo,
               @Param("opportunityId") Long opportunityId, @Param("customerId") long customerId,
               @Param("versionNo") int versionNo, @Param("totalAmount") BigDecimal totalAmount,
               @Param("discountRate") BigDecimal discountRate, @Param("taxRate") BigDecimal taxRate,
               @Param("validUntil") LocalDate validUntil, @Param("ownerId") Long ownerId,
               @Param("remark") String remark, @Param("operatorId") long operatorId);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_quotation WHERE id = #{id} AND tenant_id = #{tenantId} "
            + "AND deleted = 0")
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT COALESCE(max(version_no), 0) FROM crm_quotation WHERE tenant_id = #{tenantId} "
            + "AND opportunity_id = #{opportunityId} AND deleted = 0")
    int maxVersion(@Param("tenantId") long tenantId, @Param("opportunityId") long opportunityId);

    @Update("UPDATE crm_quotation SET status = #{status}, "
            + "approve_instance_id = COALESCE(CAST(#{approveInstanceId,jdbcType=VARCHAR} AS varchar), approve_instance_id), "
            + "updated_by = #{operatorId}, updated_at = now(), version = version + 1 "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                     @Param("approveInstanceId") String approveInstanceId, @Param("operatorId") long operatorId);

    @Select("SELECT count(1) FROM crm_quotation WHERE tenant_id = #{tenantId} AND opportunity_id = #{opportunityId} "
            + "AND status IN ('APPROVED', 'PENDING') AND deleted = 0")
    long countApprovedOrPending(@Param("tenantId") long tenantId, @Param("opportunityId") long opportunityId);

    // ---------- 列表（扩展） ----------

    String LIST_COLUMNS = " q.id AS \"quotationId\", q.quotation_no AS \"quotationNo\", "
            + "q.opportunity_id AS \"opportunityId\", q.customer_id AS \"customerId\", cu.name AS \"customerName\", "
            + "q.version_no AS \"versionNo\", q.total_amount AS \"totalAmount\", q.status AS \"status\", "
            + "q.owner_id AS \"ownerId\", (EXTRACT(EPOCH FROM q.created_at) * 1000)::bigint AS \"createdMs\" ";

    String LIST_FROM = " FROM crm_quotation q "
            + "JOIN crm_customer cu ON cu.id = q.customer_id AND cu.tenant_id = q.tenant_id AND cu.deleted = 0 ";

    String LIST_FILTERS = " AND (CAST(#{status,jdbcType=VARCHAR} AS varchar) IS NULL OR q.status = #{status}) "
            + " AND (CAST(#{ownerId,jdbcType=BIGINT} AS bigint) IS NULL OR q.owner_id = #{ownerId}) "
            + " AND (CAST(#{customerId,jdbcType=BIGINT} AS bigint) IS NULL OR q.customer_id = #{customerId}) "
            + " AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL "
            + "      OR q.quotation_no ILIKE '%' || CAST(#{keyword,jdbcType=VARCHAR} AS varchar) || '%' "
            + "      OR cu.name ILIKE '%' || CAST(#{keyword,jdbcType=VARCHAR} AS varchar) || '%') ";

    @Select("SELECT " + LIST_COLUMNS + LIST_FROM
            + "WHERE q.tenant_id = #{tenantId} AND q.deleted = 0" + LIST_FILTERS
            + "ORDER BY q.created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("status") String status,
                                   @Param("ownerId") Long ownerId, @Param("customerId") Long customerId,
                                   @Param("keyword") String keyword, @Param("offset") int offset,
                                   @Param("size") int size);

    @Select("SELECT count(1)" + LIST_FROM
            + "WHERE q.tenant_id = #{tenantId} AND q.deleted = 0" + LIST_FILTERS)
    long count(@Param("tenantId") long tenantId, @Param("status") String status,
               @Param("ownerId") Long ownerId, @Param("customerId") Long customerId,
               @Param("keyword") String keyword);

    // ---------- 明细 ----------

    @Insert("INSERT INTO crm_quotation_item (id, tenant_id, quotation_id, product_code, product_name, unit, "
            + "quantity, unit_price, discount_rate, amount, line_no, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{quotationId}, #{productCode}, #{productName}, #{unit}, #{quantity}, "
            + "#{unitPrice}, #{discountRate}, #{amount}, #{lineNo}, #{operatorId}, #{operatorId})")
    int insertItem(@Param("id") long id, @Param("tenantId") long tenantId, @Param("quotationId") long quotationId,
                   @Param("productCode") String productCode, @Param("productName") String productName,
                   @Param("unit") String unit, @Param("quantity") BigDecimal quantity,
                   @Param("unitPrice") BigDecimal unitPrice, @Param("discountRate") BigDecimal discountRate,
                   @Param("amount") BigDecimal amount, @Param("lineNo") int lineNo,
                   @Param("operatorId") long operatorId);

    @Select("SELECT line_no AS \"lineNo\", product_code AS \"productCode\", product_name AS \"productName\", "
            + "unit AS \"unit\", quantity AS \"quantity\", unit_price AS \"unitPrice\", "
            + "discount_rate AS \"discountRate\", amount AS \"amount\" "
            + "FROM crm_quotation_item WHERE tenant_id = #{tenantId} AND quotation_id = #{quotationId} "
            + "AND deleted = 0 ORDER BY line_no")
    List<Map<String, Object>> listItems(@Param("tenantId") long tenantId, @Param("quotationId") long quotationId);
}
