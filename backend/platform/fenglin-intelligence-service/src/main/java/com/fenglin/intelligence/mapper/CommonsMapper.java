package com.fenglin.intelligence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 云智公共表访问：租户盐、幂等记录。
 */
@Mapper
public interface CommonsMapper {

    @Select("SELECT salt FROM ai_tenant_key WHERE tenant_id = #{tenantId} AND deleted = 0 LIMIT 1")
    String findTenantSalt(@Param("tenantId") long tenantId);

    @Insert("INSERT INTO ai_tenant_key (id, tenant_id, salt, created_by) "
            + "VALUES (#{id}, #{tenantId}, #{salt}, #{userId})")
    void insertTenantSalt(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("salt") String salt, @Param("userId") long userId);

    @Select("SELECT biz_id FROM ai_idempotency_record "
            + "WHERE tenant_id = #{tenantId} AND api_path = #{apiPath} AND idem_key = #{idemKey} AND deleted = 0 "
            + "ORDER BY id DESC LIMIT 1")
    Long findIdempotentBizId(@Param("tenantId") long tenantId, @Param("apiPath") String apiPath,
                             @Param("idemKey") String idemKey);

    @Insert("INSERT INTO ai_idempotency_record (id, tenant_id, idem_key, api_path, biz_id, created_by) "
            + "VALUES (#{id}, #{tenantId}, #{idemKey}, #{apiPath}, #{bizId}, #{userId})")
    void insertIdempotencyRecord(@Param("id") long id, @Param("tenantId") long tenantId,
                                 @Param("apiPath") String apiPath, @Param("idemKey") String idemKey,
                                 @Param("bizId") long bizId, @Param("userId") long userId);
}
