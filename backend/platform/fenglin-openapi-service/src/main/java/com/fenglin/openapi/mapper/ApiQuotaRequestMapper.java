package com.fenglin.openapi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * OPEN-03：配额自助申请单。
 */
@Mapper
public interface ApiQuotaRequestMapper {

    @Insert("""
            INSERT INTO hub_api_quota_request
                (id, tenant_id, app_id, api_path, requested_qps, requested_daily, reason, status,
                 applied_by, created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{appId}, #{apiPath}, #{qpsLimit,jdbcType=INTEGER},
                    #{dailyLimit,jdbcType=BIGINT}, #{reason}, 'PENDING', #{userId},
                    #{userId}, now(), #{userId}, now(), 0, 0)
            """)
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("appId") long appId,
               @Param("apiPath") String apiPath, @Param("qpsLimit") Integer qpsLimit,
               @Param("dailyLimit") Long dailyLimit, @Param("reason") String reason,
               @Param("userId") long userId);

    @Select("""
            SELECT COUNT(1)
              FROM hub_api_quota_request
             WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND api_path = #{apiPath}
               AND status = 'PENDING' AND deleted = 0
            """)
    long countPending(@Param("tenantId") long tenantId, @Param("appId") long appId,
                      @Param("apiPath") String apiPath);

    @Select("""
            SELECT id, tenant_id, app_id, api_path, requested_qps, requested_daily, reason, status,
                   applied_by, decided_by, decided_at, decision_comment, created_at
              FROM hub_api_quota_request
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, app_id, api_path, requested_qps, requested_daily, reason, status,
                   applied_by, decided_by, decided_at, decision_comment, created_at
              FROM hub_api_quota_request
             WHERE tenant_id = #{tenantId} AND applied_by = #{userId} AND deleted = 0
             ORDER BY created_at DESC
             LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listByApplicant(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                              @Param("offset") int offset, @Param("size") int size);

    @Select("""
            SELECT COUNT(1)
              FROM hub_api_quota_request
             WHERE tenant_id = #{tenantId} AND applied_by = #{userId} AND deleted = 0
            """)
    long countByApplicant(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, app_id, api_path, requested_qps, requested_daily, reason, status,
                   applied_by, decided_by, decided_at, decision_comment, created_at
              FROM hub_api_quota_request
             WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null and status != ''">AND status = #{status}</if>
             ORDER BY CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END, created_at DESC
             LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listForReview(@Param("tenantId") long tenantId, @Param("status") String status,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(1)
              FROM hub_api_quota_request
             WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null and status != ''">AND status = #{status}</if>
            </script>
            """)
    long countForReview(@Param("tenantId") long tenantId, @Param("status") String status);

    /** 仅待审批申请可被处理，返回 0 表示已被处理过。 */
    @Update("""
            UPDATE hub_api_quota_request
               SET status = #{status}, decided_by = #{userId}, decided_at = now(),
                   decision_comment = #{comment,jdbcType=VARCHAR}, updated_by = #{userId}, updated_at = now(),
                   version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'PENDING' AND deleted = 0
            """)
    int decide(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
               @Param("comment") String comment, @Param("userId") long userId);
}
