package com.fenglin.file.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件策略无专用表，按平台约定落在 hub_param（FL-HUB-DB-003 4.x hub_param）。
 */
@Mapper
public interface FilePolicyMapper {

    @Select("""
            SELECT param_value FROM hub_param
             WHERE param_key = #{paramKey} AND deleted = 0
               AND (tenant_id = 0 OR tenant_id = #{tenantId})
             ORDER BY tenant_id DESC
             LIMIT 1
            """)
    String findValue(@Param("tenantId") long tenantId, @Param("paramKey") String paramKey);

    @Insert("""
            INSERT INTO hub_param (id, tenant_id, param_key, param_value, scope, value_type,
                                   remark, created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{paramKey}, #{paramValue}, 'TENANT', #{valueType},
                    #{remark,jdbcType=VARCHAR}, #{operatorId,jdbcType=BIGINT}, now(),
                    #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            ON CONFLICT (tenant_id, param_key)
            DO UPDATE SET param_value = EXCLUDED.param_value,
                          value_type = EXCLUDED.value_type,
                          remark = EXCLUDED.remark,
                          deleted = 0,
                          updated_by = EXCLUDED.updated_by,
                          updated_at = now(),
                          version = hub_param.version + 1
            """)
    int upsert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("paramKey") String paramKey,
               @Param("paramValue") String paramValue,
               @Param("valueType") String valueType,
               @Param("remark") String remark,
               @Param("operatorId") Long operatorId);
}
