package com.fenglin.openapi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ApiCallLogMapper {

    @Insert("""
            INSERT INTO hub_api_call_log (id, tenant_id, app_id, app_key, api_path, method,
                                          http_status, biz_code, latency_ms, request_id, called_at, deleted)
            VALUES (#{id}, #{tenantId}, #{appId}, #{appKey,jdbcType=VARCHAR}, #{apiPath}, #{method,jdbcType=VARCHAR},
                    #{httpStatus,jdbcType=INTEGER}, #{bizCode,jdbcType=VARCHAR}, #{latencyMs,jdbcType=INTEGER},
                    #{requestId,jdbcType=VARCHAR}, now(), 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("appId") long appId,
               @Param("appKey") String appKey,
               @Param("apiPath") String apiPath,
               @Param("method") String method,
               @Param("httpStatus") Integer httpStatus,
               @Param("bizCode") String bizCode,
               @Param("latencyMs") Integer latencyMs,
               @Param("requestId") String requestId);

    @Select("""
            <script>
            SELECT id, app_id, app_key, api_path, method, http_status, biz_code, latency_ms, request_id, called_at
              FROM hub_api_call_log
             WHERE tenant_id = #{tenantId}
            <if test="appId != null"> AND app_id = #{appId}</if>
            <if test="apiPath != null"> AND api_path = #{apiPath}</if>
            <if test="status != null"> AND http_status = #{status}</if>
            <if test="from != null"> AND called_at &gt;= #{from}</if>
            <if test="to != null"> AND called_at &lt;= #{to}</if>
             ORDER BY called_at DESC, id DESC
             LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> search(@Param("tenantId") long tenantId,
                                     @Param("appId") Long appId,
                                     @Param("apiPath") String apiPath,
                                     @Param("status") Integer status,
                                     @Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to,
                                     @Param("offset") long offset,
                                     @Param("size") long size);

    @Select("""
            <script>
            SELECT count(*)
              FROM hub_api_call_log
             WHERE tenant_id = #{tenantId}
            <if test="appId != null"> AND app_id = #{appId}</if>
            <if test="apiPath != null"> AND api_path = #{apiPath}</if>
            <if test="status != null"> AND http_status = #{status}</if>
            <if test="from != null"> AND called_at &gt;= #{from}</if>
            <if test="to != null"> AND called_at &lt;= #{to}</if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId,
               @Param("appId") Long appId,
               @Param("apiPath") String apiPath,
               @Param("status") Integer status,
               @Param("from") OffsetDateTime from,
               @Param("to") OffsetDateTime to);

    @Select("""
            SELECT count(*)
              FROM hub_api_call_log
             WHERE tenant_id = #{tenantId} AND app_id = #{appId}
               AND called_at >= date_trunc('day', now())
            """)
    long countToday(@Param("tenantId") long tenantId, @Param("appId") long appId);
}
