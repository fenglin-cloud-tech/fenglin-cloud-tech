package com.fenglin.openapi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface OpenAppMapper {

    @Select("""
            SELECT 'APP' || to_char(now(), 'YYYY') || lpad(nextval('hub_open_app_code_seq')::text, 6, '0')
            """)
    String nextCode();

    @Insert("""
            INSERT INTO hub_open_app (id, tenant_id, code, name, owner_type, owner_id, scopes, redirect_uris,
                                      review_status, status, created_by, created_at, updated_by, updated_at,
                                      deleted, version)
            VALUES (#{id}, #{tenantId}, #{code}, #{name}, #{ownerType}, #{ownerId,jdbcType=BIGINT},
                    #{scopes,jdbcType=VARCHAR}, #{redirectUris,jdbcType=VARCHAR},
                    'PENDING', 'DISABLED', #{operatorId,jdbcType=BIGINT}, now(),
                    #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("code") String code,
               @Param("name") String name,
               @Param("ownerType") String ownerType,
               @Param("ownerId") Long ownerId,
               @Param("scopes") String scopes,
               @Param("redirectUris") String redirectUris,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, code, name, review_status, status
              FROM hub_open_app
             WHERE tenant_id = #{tenantId} AND name = #{name} AND deleted = 0
             LIMIT 1
            """)
    Map<String, Object> findByTenantAndName(@Param("tenantId") long tenantId, @Param("name") String name);

    @Select("""
            SELECT id, code, name, owner_type, owner_id, scopes, redirect_uris,
                   review_status, status, created_at
              FROM hub_open_app
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    /** 开发者门户：只返回当前用户创建的应用，服务端强制按 owner_id 收敛。 */
    @Select("""
            SELECT id, code, name, owner_type, review_status, status, created_at
              FROM hub_open_app
             WHERE tenant_id = #{tenantId} AND owner_id = #{ownerId} AND deleted = 0
             ORDER BY created_at DESC, id DESC
            """)
    List<Map<String, Object>> listByOwner(@Param("tenantId") long tenantId,
                                          @Param("ownerId") long ownerId);

    @Update("""
            UPDATE hub_open_app
               SET redirect_uris = #{redirectUris,jdbcType=VARCHAR},
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now()
             WHERE tenant_id = #{tenantId} AND id = #{id} AND owner_id = #{operatorId} AND deleted = 0
            """)
    int updateCallback(@Param("tenantId") long tenantId,
                       @Param("id") long id,
                       @Param("operatorId") long operatorId,
                       @Param("redirectUris") String redirectUris);

    @Update("""
            UPDATE hub_open_app
               SET scopes = #{scopes,jdbcType=VARCHAR},
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now()
             WHERE tenant_id = #{tenantId} AND id = #{id} AND owner_id = #{operatorId} AND deleted = 0
            """)
    int updateScopes(@Param("tenantId") long tenantId,
                     @Param("id") long id,
                     @Param("operatorId") long operatorId,
                     @Param("scopes") String scopes);

    @Update("""
            UPDATE hub_open_app
               SET review_status = #{reviewStatus}, status = #{status},
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now()
             WHERE tenant_id = #{tenantId} AND id = #{id}
               AND review_status = 'PENDING' AND deleted = 0
            """)
    int updateReview(@Param("tenantId") long tenantId,
                     @Param("id") long id,
                     @Param("reviewStatus") String reviewStatus,
                     @Param("status") String status,
                     @Param("operatorId") Long operatorId);

    @Select("""
            <script>
            SELECT id, code, name, owner_type, review_status, status, created_at
              FROM hub_open_app
             WHERE tenant_id = #{tenantId} AND deleted = 0
             <if test="status != null"> AND status = #{status} </if>
             <if test="reviewStatus != null"> AND review_status = #{reviewStatus} </if>
             ORDER BY created_at DESC, id DESC
             LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listAdmin(@Param("tenantId") long tenantId,
                                        @Param("status") String status,
                                        @Param("reviewStatus") String reviewStatus,
                                        @Param("offset") long offset,
                                        @Param("size") long size);

    @Select("""
            <script>
            SELECT count(1)
              FROM hub_open_app
             WHERE tenant_id = #{tenantId} AND deleted = 0
             <if test="status != null"> AND status = #{status} </if>
             <if test="reviewStatus != null"> AND review_status = #{reviewStatus} </if>
            </script>
            """)
    long countAdmin(@Param("tenantId") long tenantId,
                    @Param("status") String status,
                    @Param("reviewStatus") String reviewStatus);
}
