package com.fenglin.audit.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuditAlertRuleMapper {

    @Select("""
            SELECT id, code, event_type, threshold, receiver_role, channels
              FROM hub_audit_alert_rule
             WHERE deleted = 0 AND enabled = 1
             ORDER BY code
            """)
    List<Map<String, Object>> findAll();

    @Update("""
            UPDATE hub_audit_alert_rule
               SET deleted = 1, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    int deleteAll(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO hub_audit_alert_rule (id, tenant_id, code, event_type, threshold,
                                              receiver_role, channels, enabled,
                                              created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{code}, #{eventType}, #{threshold},
                    #{receiverRole}, #{channels}, 1,
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("code") String code,
               @Param("eventType") String eventType,
               @Param("threshold") int threshold,
               @Param("receiverRole") String receiverRole,
               @Param("channels") String channels,
               @Param("operatorId") Long operatorId);
}
