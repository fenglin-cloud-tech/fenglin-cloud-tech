package com.fenglin.message.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * PLAT-02 消息通道配置 Mapper。
 */
@Mapper
public interface MessageChannelMapper {

    @Select("""
            SELECT id, channel_type, name, config::text AS config, enabled, is_default, remark
            FROM hub_message_channel
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY channel_type, is_default DESC, id
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id, channel_type, name, config::text AS config, enabled, is_default, remark
            FROM hub_message_channel
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> find(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id FROM hub_message_channel
            WHERE tenant_id = #{tenantId} AND channel_type = #{channelType} AND name = #{name}
              AND deleted = 0
              AND (CAST(#{excludeId,jdbcType=BIGINT} AS bigint) IS NULL OR id <> #{excludeId})
            """)
    Long findDuplicate(@Param("tenantId") long tenantId, @Param("channelType") String channelType,
                       @Param("name") String name, @Param("excludeId") Long excludeId);

    @Insert("""
            INSERT INTO hub_message_channel
                (id, tenant_id, channel_type, name, config, enabled, is_default, remark, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{channelType}, #{name}, CAST(#{config,jdbcType=VARCHAR} AS jsonb),
                    #{enabled}, #{isDefault}, #{remark,jdbcType=VARCHAR}, #{operatorId}, #{operatorId})
            """)
    void insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("channelType") String channelType,
                @Param("name") String name, @Param("config") String config, @Param("enabled") int enabled,
                @Param("isDefault") int isDefault, @Param("remark") String remark,
                @Param("operatorId") long operatorId);

    @Update("""
            UPDATE hub_message_channel
            SET channel_type = #{channelType}, name = #{name}, config = CAST(#{config,jdbcType=VARCHAR} AS jsonb),
                enabled = #{enabled}, is_default = #{isDefault}, remark = #{remark,jdbcType=VARCHAR},
                updated_by = #{operatorId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int update(@Param("tenantId") long tenantId, @Param("id") long id, @Param("channelType") String channelType,
               @Param("name") String name, @Param("config") String config, @Param("enabled") int enabled,
               @Param("isDefault") int isDefault, @Param("remark") String remark,
               @Param("operatorId") long operatorId);

    @Update("""
            UPDATE hub_message_channel
            SET is_default = 0, updated_by = #{operatorId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND channel_type = #{channelType} AND is_default = 1
              AND deleted = 0 AND id <> #{excludeId}
            """)
    void clearDefault(@Param("tenantId") long tenantId, @Param("channelType") String channelType,
                      @Param("excludeId") long excludeId, @Param("operatorId") long operatorId);

    @Update("""
            UPDATE hub_message_channel
            SET enabled = #{enabled}, updated_by = #{operatorId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int toggle(@Param("tenantId") long tenantId, @Param("id") long id, @Param("enabled") int enabled,
               @Param("operatorId") long operatorId);

    @Update("""
            UPDATE hub_message_channel
            SET deleted = 1, updated_by = #{operatorId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int softDelete(@Param("tenantId") long tenantId, @Param("id") long id, @Param("operatorId") long operatorId);
}
