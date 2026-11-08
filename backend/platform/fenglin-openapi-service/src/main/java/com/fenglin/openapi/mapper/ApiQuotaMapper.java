package com.fenglin.openapi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface ApiQuotaMapper {

    @Insert("""
            INSERT INTO hub_api_quota (id, tenant_id, app_id, api_path, qps_limit, daily_limit,
                                       used_today, used_date, created_by, created_at, updated_by, updated_at,
                                       deleted, version)
            VALUES (#{id}, #{tenantId}, #{appId}, #{apiPath}, #{qpsLimit,jdbcType=INTEGER},
                    #{dailyLimit,jdbcType=BIGINT}, 0, CURRENT_DATE, #{operatorId,jdbcType=BIGINT}, now(),
                    #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            ON CONFLICT (app_id, api_path) DO UPDATE
               SET qps_limit = EXCLUDED.qps_limit,
                   daily_limit = EXCLUDED.daily_limit,
                   updated_by = EXCLUDED.updated_by,
                   updated_at = now()
            """)
    int upsert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("appId") long appId,
               @Param("apiPath") String apiPath,
               @Param("qpsLimit") Integer qpsLimit,
               @Param("dailyLimit") Long dailyLimit,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, api_path, qps_limit, daily_limit, used_today
              FROM hub_api_quota
             WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
               AND (api_path = #{apiPath} OR api_path = '*')
             ORDER BY CASE WHEN api_path = #{apiPath} THEN 0 ELSE 1 END
             LIMIT 1
            """)
    Map<String, Object> findBestMatch(@Param("tenantId") long tenantId,
                                      @Param("appId") long appId,
                                      @Param("apiPath") String apiPath);

    @Select("""
            SELECT max(daily_limit)
              FROM hub_api_quota
             WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
            """)
    Long maxDailyLimit(@Param("tenantId") long tenantId, @Param("appId") long appId);

    /**
     * 原子累加当日用量：跨日自动重置；达到 daily_limit 时更新失败（返回 0）。
     */
    @Update("""
            UPDATE hub_api_quota
               SET used_today = CASE WHEN used_date = CURRENT_DATE THEN used_today + 1 ELSE 1 END,
                   used_date = CURRENT_DATE,
                   updated_at = now()
             WHERE id = #{id} AND deleted = 0
               AND (daily_limit IS NULL OR used_date <> CURRENT_DATE OR used_today < daily_limit)
            """)
    int incrementDaily(@Param("id") long id);
}
