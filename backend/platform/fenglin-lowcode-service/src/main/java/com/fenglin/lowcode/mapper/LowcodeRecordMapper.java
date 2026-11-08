package com.fenglin.lowcode.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LowcodeRecordMapper {

    @Insert("""
            INSERT INTO hub_lowcode_record (id, tenant_id, app_id, record_no, idempotency_key, data,
                                            status, submitted_by, created_by, created_at, updated_by,
                                            updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{appId}, #{recordNo}, #{idempotencyKey,jdbcType=VARCHAR},
                    CAST(#{data} AS jsonb), 'SUBMITTED', #{operatorId}, #{operatorId}, now(),
                    #{operatorId}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("appId") long appId,
               @Param("recordNo") String recordNo,
               @Param("idempotencyKey") String idempotencyKey,
               @Param("data") String data,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, record_no, status, submitted_by, created_at, CAST(data AS text) AS data_text
              FROM hub_lowcode_record
             WHERE tenant_id = #{tenantId} AND idempotency_key = #{idempotencyKey} AND deleted = 0
             LIMIT 1
            """)
    Map<String, Object> findByIdempotencyKey(@Param("tenantId") long tenantId,
                                             @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT id, record_no, status, submitted_by, created_at, CAST(data AS text) AS data_text
              FROM hub_lowcode_record
             WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
             ORDER BY created_at DESC
             LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId,
                                   @Param("appId") long appId,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    @Select("""
            SELECT count(*)
              FROM hub_lowcode_record
             WHERE tenant_id = #{tenantId} AND app_id = #{appId} AND deleted = 0
            """)
    long count(@Param("tenantId") long tenantId, @Param("appId") long appId);
}
