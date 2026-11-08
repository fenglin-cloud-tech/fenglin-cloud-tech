package com.fenglin.crm.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface CustomerMapper {

    String VIEW_COLUMNS = " id AS \"customerId\", customer_no AS \"customerNo\", name AS \"name\", "
            + "short_name AS \"shortName\", uscc AS \"uscc\", industry AS \"industry\", scale AS \"scale\", "
            + "region_code AS \"regionCode\", grade AS \"grade\", segment AS \"segment\", owner_id AS \"ownerId\", "
            + "csm_id AS \"csmId\", dept_id AS \"deptId\", mobile_tail AS \"mobileTail\", "
            + "annual_revenue AS \"annualRevenue\", arr AS \"arr\", status AS \"status\", "
            + "(EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS \"createdMs\" ";

    String FILTERS = " AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL "
            + "      OR name ILIKE '%' || CAST(#{keyword,jdbcType=VARCHAR} AS varchar) || '%' "
            + "      OR customer_no ILIKE '%' || CAST(#{keyword,jdbcType=VARCHAR} AS varchar) || '%' "
            + "      OR uscc = CAST(#{keyword,jdbcType=VARCHAR} AS varchar)) "
            + " AND (CAST(#{ownerId,jdbcType=BIGINT} AS bigint) IS NULL OR owner_id = #{ownerId}) "
            + " AND (CAST(#{segment,jdbcType=VARCHAR} AS varchar) IS NULL OR segment = #{segment}) "
            + " AND (CAST(#{status,jdbcType=VARCHAR} AS varchar) IS NULL OR status = #{status}) ";

    @Select("SELECT nextval('fenglin_crm.crm_customer_no_seq')")
    long nextCustomerSeq();

    @Insert("INSERT INTO crm_customer (id, tenant_id, customer_no, name, short_name, uscc, industry, scale, "
            + "region_code, grade, segment, owner_id, csm_id, dept_id, mobile_enc, mobile_hash, mobile_tail, "
            + "annual_revenue, arr, status, source_lead_id, remark, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{customerNo}, #{name}, #{shortName}, #{uscc}, #{industry}, #{scale}, "
            + "#{regionCode}, #{grade}, #{segment}, #{ownerId}, #{csmId}, #{deptId}, #{mobileEnc}, #{mobileHash}, "
            + "#{mobileTail}, #{annualRevenue}, #{arr}, 'ACTIVE', #{sourceLeadId}, #{remark}, #{operatorId}, "
            + "#{operatorId})")
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("customerNo") String customerNo,
               @Param("name") String name, @Param("shortName") String shortName, @Param("uscc") String uscc,
               @Param("industry") String industry, @Param("scale") String scale,
               @Param("regionCode") String regionCode, @Param("grade") String grade,
               @Param("segment") String segment, @Param("ownerId") Long ownerId, @Param("csmId") Long csmId,
               @Param("deptId") Long deptId, @Param("mobileEnc") String mobileEnc,
               @Param("mobileHash") String mobileHash, @Param("mobileTail") String mobileTail,
               @Param("annualRevenue") BigDecimal annualRevenue,
               @Param("arr") BigDecimal arr, @Param("sourceLeadId") Long sourceLeadId,
               @Param("remark") String remark, @Param("operatorId") long operatorId);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_customer WHERE id = #{id} AND tenant_id = #{tenantId} "
            + "AND deleted = 0")
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_customer WHERE tenant_id = #{tenantId} AND deleted = 0"
            + FILTERS + " ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
                                   @Param("ownerId") Long ownerId, @Param("segment") String segment,
                                   @Param("status") String status, @Param("offset") int offset,
                                   @Param("size") int size);

    @Select("SELECT count(1) FROM crm_customer WHERE tenant_id = #{tenantId} AND deleted = 0" + FILTERS)
    long count(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
               @Param("ownerId") Long ownerId, @Param("segment") String segment,
               @Param("status") String status);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_customer WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND (mobile_hash = CAST(#{mobileHash,jdbcType=VARCHAR} AS varchar) "
            + "     OR uscc = CAST(#{uscc,jdbcType=VARCHAR} AS varchar) "
            + "     OR lower(name) = lower(CAST(#{name,jdbcType=VARCHAR} AS varchar))) "
            + "ORDER BY created_at DESC LIMIT 10")
    List<Map<String, Object>> findDuplicates(@Param("tenantId") long tenantId,
                                             @Param("mobileHash") String mobileHash,
                                             @Param("uscc") String uscc,
                                             @Param("name") String name);

    @Select("SELECT count(1) FROM crm_customer WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND mobile_hash = #{mobileHash}")
    long countByMobileHash(@Param("tenantId") long tenantId, @Param("mobileHash") String mobileHash);

    @Select("SELECT count(1) FROM crm_customer WHERE tenant_id = #{tenantId} AND deleted = 0 AND uscc = #{uscc}")
    long countByUscc(@Param("tenantId") long tenantId, @Param("uscc") String uscc);

    @Update("UPDATE crm_customer SET owner_id = #{ownerId}, updated_by = #{operatorId}, "
            + "updated_at = now(), version = version + 1 "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int updateOwner(@Param("tenantId") long tenantId, @Param("id") long id, @Param("ownerId") Long ownerId,
                    @Param("operatorId") long operatorId);
}
