package com.fenglin.legal.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 云法管理端配置 Mapper：提醒、合同字段、字典、CA 服务商。
 */
@Mapper
public interface LegalConfigMapper {

    @Select("SELECT offset_days AS \"offset_days\", channels::text AS \"channels\" "
            + "FROM legal_reminder_config WHERE tenant_id = #{tenantId} AND deleted = 0")
    Map<String, Object> findReminderConfig(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO legal_reminder_config
                (id, tenant_id, offset_days, channels, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{offsetDays}, CAST(#{channels} AS jsonb), #{userId}, #{userId})
            ON CONFLICT (tenant_id) DO UPDATE
            SET offset_days = EXCLUDED.offset_days,
                channels = EXCLUDED.channels,
                updated_by = EXCLUDED.updated_by,
                updated_at = now(),
                version = legal_reminder_config.version + 1
            """)
    void upsertReminderConfig(@Param("id") long id, @Param("tenantId") long tenantId,
                              @Param("offsetDays") int offsetDays, @Param("channels") String channels,
                              @Param("userId") long userId);

    @Select("SELECT fields::text AS \"fields\" FROM legal_contract_field_config "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0")
    Map<String, Object> findContractFieldConfig(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO legal_contract_field_config
                (id, tenant_id, fields, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, CAST(#{fields} AS jsonb), #{userId}, #{userId})
            ON CONFLICT (tenant_id) DO UPDATE
            SET fields = EXCLUDED.fields,
                updated_by = EXCLUDED.updated_by,
                updated_at = now(),
                version = legal_contract_field_config.version + 1
            """)
    void upsertContractFieldConfig(@Param("id") long id, @Param("tenantId") long tenantId,
                                   @Param("fields") String fields, @Param("userId") long userId);

    @Select("SELECT dict_type AS \"type\", items::text AS \"items\" FROM legal_dict_config "
            + "WHERE tenant_id = #{tenantId} AND dict_type = #{type} AND deleted = 0")
    Map<String, Object> findDictionary(@Param("tenantId") long tenantId, @Param("type") String type);

    @Insert("""
            INSERT INTO legal_dict_config
                (id, tenant_id, dict_type, items, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{type}, CAST(#{items} AS jsonb), #{userId}, #{userId})
            ON CONFLICT (tenant_id, dict_type) DO UPDATE
            SET items = EXCLUDED.items,
                updated_by = EXCLUDED.updated_by,
                updated_at = now(),
                version = legal_dict_config.version + 1
            """)
    void upsertDictionary(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("type") String type, @Param("items") String items,
                          @Param("userId") long userId);

    @Select("SELECT provider_code AS \"provider_code\", name AS \"name\", endpoint AS \"endpoint\", "
            + "status AS \"status\" FROM legal_ca_provider "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY id")
    List<Map<String, Object>> listCaProviders(@Param("tenantId") long tenantId);

    @Select("SELECT provider_code AS \"provider_code\" FROM legal_ca_provider "
            + "WHERE tenant_id = #{tenantId} AND provider_code = #{providerCode} AND deleted = 0")
    Map<String, Object> findCaProvider(@Param("tenantId") long tenantId,
                                       @Param("providerCode") String providerCode);

    @Insert("""
            INSERT INTO legal_ca_provider
                (id, tenant_id, provider_code, name, endpoint, secret_ref, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{providerCode}, #{name}, #{endpoint}, #{secretRef}, #{status},
                    #{userId}, #{userId})
            ON CONFLICT (tenant_id, provider_code) DO UPDATE
            SET name = COALESCE(EXCLUDED.name, legal_ca_provider.name),
                endpoint = COALESCE(EXCLUDED.endpoint, legal_ca_provider.endpoint),
                secret_ref = EXCLUDED.secret_ref,
                status = EXCLUDED.status,
                updated_by = EXCLUDED.updated_by,
                updated_at = now(),
                version = legal_ca_provider.version + 1
            """)
    void upsertCaProvider(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("providerCode") String providerCode, @Param("name") String name,
                          @Param("endpoint") String endpoint, @Param("secretRef") String secretRef,
                          @Param("status") String status, @Param("userId") long userId);
}
