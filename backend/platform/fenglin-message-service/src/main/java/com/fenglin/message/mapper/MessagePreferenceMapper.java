package com.fenglin.message.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 消息偏好沿用 P0 缺口补表 hub_message_preference（tenant, user, event_type, channel 维度）。
 */
@Mapper
public interface MessagePreferenceMapper {

    @Select("""
            SELECT event_type, channel, enabled, quiet_hours
              FROM hub_message_preference
             WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND deleted = 0
             ORDER BY event_type, channel
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("userId") long userId);

    @Select("""
            SELECT event_type, channel, enabled, quiet_hours
              FROM hub_message_preference
             WHERE tenant_id = #{tenantId} AND user_id = #{userId}
               AND event_type = #{eventType} AND deleted = 0
            """)
    List<Map<String, Object>> listByEvent(@Param("tenantId") long tenantId,
                                          @Param("userId") long userId,
                                          @Param("eventType") String eventType);

    @Update("""
            UPDATE hub_message_preference
               SET enabled = #{enabled}, quiet_hours = #{quietHours,jdbcType=VARCHAR},
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(),
                   version = version + 1
             WHERE tenant_id = #{tenantId} AND user_id = #{userId}
               AND event_type = #{eventType} AND channel = #{channel} AND deleted = 0
            """)
    int update(@Param("tenantId") long tenantId,
               @Param("userId") long userId,
               @Param("eventType") String eventType,
               @Param("channel") String channel,
               @Param("enabled") int enabled,
               @Param("quietHours") String quietHours,
               @Param("operatorId") Long operatorId);

    @Insert("""
            INSERT INTO hub_message_preference (id, tenant_id, user_id, event_type, channel,
                                               enabled, quiet_hours, created_by, created_at,
                                               updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{userId}, #{eventType}, #{channel}, #{enabled},
                    #{quietHours,jdbcType=VARCHAR}, #{operatorId,jdbcType=BIGINT}, now(),
                    #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("userId") long userId,
               @Param("eventType") String eventType,
               @Param("channel") String channel,
               @Param("enabled") int enabled,
               @Param("quietHours") String quietHours,
               @Param("operatorId") Long operatorId);

    @Update("""
            <script>
            UPDATE hub_message_preference
               SET enabled = 0, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND user_id = #{userId}
               AND event_type = #{eventType} AND deleted = 0
             <if test="channels != null and channels.size() > 0">
               AND channel NOT IN
               <foreach collection="channels" item="channel" open="(" separator="," close=")">
                   #{channel}
               </foreach>
             </if>
            </script>
            """)
    int disableOthers(@Param("tenantId") long tenantId,
                      @Param("userId") long userId,
                      @Param("eventType") String eventType,
                      @Param("channels") List<String> channels);
}
