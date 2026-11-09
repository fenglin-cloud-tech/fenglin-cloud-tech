package com.fenglin.crm.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface LeadMapper {

    String VIEW_COLUMNS = " id AS \"leadId\", lead_no AS \"leadNo\", name AS \"name\", "
            + "contact_name AS \"contactName\", mobile_enc AS \"mobileEnc\", mobile_tail AS \"mobileTail\", "
            + "email AS \"email\", "
            + "industry AS \"industry\", region_code AS \"regionCode\", source AS \"source\", score AS \"score\", "
            + "grade AS \"grade\", status AS \"status\", owner_id AS \"ownerId\", dept_id AS \"deptId\", "
            + "(EXTRACT(EPOCH FROM last_activity_at) * 1000)::bigint AS \"lastActivityMs\", "
            + "converted_customer_id AS \"convertedCustomerId\", "
            + "(EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS \"createdMs\" ";

    String FILTERS = " AND (CAST(#{status,jdbcType=VARCHAR} AS varchar) IS NULL OR status = #{status}) "
            + " AND (CAST(#{ownerId,jdbcType=BIGINT} AS bigint) IS NULL OR owner_id = #{ownerId}) "
            + " AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL "
            + "      OR name ILIKE '%' || CAST(#{keyword,jdbcType=VARCHAR} AS varchar) || '%' "
            + "      OR lead_no ILIKE '%' || CAST(#{keyword,jdbcType=VARCHAR} AS varchar) || '%') ";

    @Select("SELECT nextval('fenglin_crm.crm_biz_no_seq')")
    long nextBizSeq();

    @Insert("INSERT INTO crm_lead (id, tenant_id, lead_no, name, contact_name, mobile_enc, mobile_hash, mobile_tail, "
            + "email, industry, region_code, source, grade, status, owner_id, dept_id, remark, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{leadNo}, #{name}, #{contactName}, #{mobileEnc}, #{mobileHash}, "
            + "#{mobileTail}, #{email}, #{industry}, #{regionCode}, #{source}, #{grade}, 'NEW', #{ownerId}, "
            + "#{deptId}, #{remark}, #{operatorId}, #{operatorId})")
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("leadNo") String leadNo,
               @Param("name") String name, @Param("contactName") String contactName,
               @Param("mobileEnc") String mobileEnc, @Param("mobileHash") String mobileHash,
               @Param("mobileTail") String mobileTail, @Param("email") String email,
               @Param("industry") String industry, @Param("regionCode") String regionCode,
               @Param("source") String source, @Param("grade") String grade,
               @Param("ownerId") Long ownerId, @Param("deptId") Long deptId,
               @Param("remark") String remark, @Param("operatorId") long operatorId);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_lead WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_lead WHERE tenant_id = #{tenantId} AND deleted = 0"
            + FILTERS + " ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("status") String status,
                                   @Param("ownerId") Long ownerId, @Param("keyword") String keyword,
                                   @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT count(1) FROM crm_lead WHERE tenant_id = #{tenantId} AND deleted = 0" + FILTERS)
    long count(@Param("tenantId") long tenantId, @Param("status") String status,
               @Param("ownerId") Long ownerId, @Param("keyword") String keyword);

    @Select("SELECT id AS \"id\", lead_no AS \"no\", name AS \"name\", mobile_tail AS \"mobileTail\" "
            + "FROM crm_lead WHERE tenant_id = #{tenantId} AND mobile_hash = #{mobileHash} AND deleted = 0")
    List<Map<String, Object>> findExactByMobileHash(@Param("tenantId") long tenantId,
                                                    @Param("mobileHash") String mobileHash);

    @Select("SELECT id AS \"id\", lead_no AS \"no\", name AS \"name\", mobile_tail AS \"mobileTail\" "
            + "FROM crm_lead WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND (name ILIKE '%' || CAST(#{keyword,jdbcType=VARCHAR} AS varchar) || '%' "
            + "     OR email = CAST(#{email,jdbcType=VARCHAR} AS varchar)) "
            + "ORDER BY created_at DESC LIMIT 10")
    List<Map<String, Object>> findFuzzy(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
                                        @Param("email") String email);

    @Update("UPDATE crm_lead SET owner_id = #{ownerId}, dept_id = #{deptId}, "
            + "status = CASE WHEN status = 'NEW' THEN 'ASSIGNED' ELSE status END, "
            + "updated_by = #{operatorId}, "
            + "updated_at = now(), version = version + 1 "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int assign(@Param("tenantId") long tenantId, @Param("id") long id, @Param("ownerId") Long ownerId,
               @Param("deptId") Long deptId, @Param("operatorId") long operatorId);

    @Update("UPDATE crm_lead SET status = 'CONVERTED', converted_customer_id = #{customerId}, "
            + "updated_by = #{operatorId}, updated_at = now(), version = version + 1 "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int markConverted(@Param("tenantId") long tenantId, @Param("id") long id,
                      @Param("customerId") long customerId, @Param("operatorId") long operatorId);

    @Update("UPDATE crm_lead SET last_activity_at = #{occurredAt}, updated_by = #{operatorId}, "
            + "updated_at = now(), version = version + 1 "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int touchLastActivity(@Param("tenantId") long tenantId, @Param("id") long id,
                          @Param("occurredAt") Instant occurredAt, @Param("operatorId") long operatorId);

    @Select("SELECT count(1) FROM crm_lead WHERE tenant_id = #{tenantId} AND mobile_hash = #{mobileHash} "
            + "AND deleted = 0")
    long countByMobileHash(@Param("tenantId") long tenantId, @Param("mobileHash") String mobileHash);
}
