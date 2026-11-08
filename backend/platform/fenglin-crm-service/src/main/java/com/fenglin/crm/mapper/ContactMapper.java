package com.fenglin.crm.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ContactMapper {

    String VIEW_COLUMNS = " id AS \"contactId\", customer_id AS \"customerId\", name AS \"name\", "
            + "position AS \"position\", role_type AS \"roleType\", mobile_tail AS \"mobileTail\", "
            + "email AS \"email\", wechat AS \"wechat\", influence AS \"influence\", is_key AS \"isKey\" ";

    @Insert("INSERT INTO crm_contact (id, tenant_id, customer_id, name, position, role_type, mobile_enc, "
            + "mobile_hash, mobile_tail, email, wechat, influence, is_key, status, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{customerId}, #{name}, #{position}, #{roleType}, #{mobileEnc}, "
            + "#{mobileHash}, #{mobileTail}, #{email}, #{wechat}, #{influence}, #{isKey}, 'ACTIVE', "
            + "#{operatorId}, #{operatorId})")
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("customerId") long customerId,
               @Param("name") String name, @Param("position") String position, @Param("roleType") String roleType,
               @Param("mobileEnc") String mobileEnc, @Param("mobileHash") String mobileHash,
               @Param("mobileTail") String mobileTail, @Param("email") String email,
               @Param("wechat") String wechat, @Param("influence") int influence,
               @Param("isKey") boolean isKey, @Param("operatorId") long operatorId);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_contact WHERE id = #{id} AND tenant_id = #{tenantId} "
            + "AND deleted = 0")
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_contact WHERE tenant_id = #{tenantId} "
            + "AND customer_id = #{customerId} AND deleted = 0 ORDER BY is_key DESC, influence DESC, id")
    List<Map<String, Object>> listByCustomer(@Param("tenantId") long tenantId,
                                             @Param("customerId") long customerId);

    @Select("SELECT count(1) FROM crm_contact WHERE tenant_id = #{tenantId} AND customer_id = #{customerId} "
            + "AND is_key = true AND status = 'ACTIVE' AND deleted = 0")
    long countKeyContacts(@Param("tenantId") long tenantId, @Param("customerId") long customerId);
}
