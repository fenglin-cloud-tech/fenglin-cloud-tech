package com.fenglin.openapi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface AppKeyMapper {

    @Insert("""
            INSERT INTO hub_app_key (id, tenant_id, app_id, app_key, app_secret_enc, secret_fingerprint,
                                     status, expire_at, created_by, created_at, updated_by, updated_at,
                                     deleted, version)
            VALUES (#{id}, #{tenantId}, #{appId}, #{appKey}, #{appSecretEnc}, #{secretFingerprint},
                    'ACTIVE', NULL, #{operatorId,jdbcType=BIGINT}, now(),
                    #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("appId") long appId,
               @Param("appKey") String appKey,
               @Param("appSecretEnc") String appSecretEnc,
               @Param("secretFingerprint") String secretFingerprint,
               @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_app_key
               SET status = 'REVOKED', updated_at = now()
             WHERE tenant_id = #{tenantId} AND app_id = #{appId}
               AND status = 'ACTIVE' AND deleted = 0
            """)
    int revokeActive(@Param("tenantId") long tenantId, @Param("appId") long appId);

    @Update("""
            UPDATE hub_app_key
               SET expire_at = now() + make_interval(hours => #{graceHours}), updated_at = now()
             WHERE tenant_id = #{tenantId} AND app_id = #{appId}
               AND status = 'ACTIVE' AND deleted = 0
            """)
    int expireActiveAfter(@Param("tenantId") long tenantId,
                          @Param("appId") long appId,
                          @Param("graceHours") int graceHours);

    /**
     * 无租户上下文时通过 SECURITY DEFINER 函数定位 AppKey 归属租户。
     */
    @Select("""
            SELECT tenant_id, app_id, app_secret_enc, key_status, expire_at, app_status
              FROM resolve_open_app_key(#{appKey})
            """)
    Map<String, Object> resolve(@Param("appKey") String appKey);
}
