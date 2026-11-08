package com.fenglin.audit.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper {

    @Insert("""
            INSERT INTO hub_audit_log (id, tenant_id, log_no, event_type, module, action,
                                       operator_id, operator_name, target_type, target_id,
                                       request_id, trace_id, client_type, ip_enc, result, risk_level,
                                       detail, occurred_at, created_by, created_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{logNo}, #{eventType}, #{module}, #{action},
                    #{operatorId,jdbcType=BIGINT}, #{operatorName}, #{targetType}, #{targetId},
                    #{requestId}, #{traceId}, #{clientType}, #{ipEnc}, #{result}, #{riskLevel},
                    #{detail,jdbcType=VARCHAR}::jsonb, #{occurredAt,jdbcType=TIMESTAMP},
                    #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("logNo") String logNo,
               @Param("eventType") String eventType,
               @Param("module") String module,
               @Param("action") String action,
               @Param("operatorId") Long operatorId,
               @Param("operatorName") String operatorName,
               @Param("targetType") String targetType,
               @Param("targetId") String targetId,
               @Param("requestId") String requestId,
               @Param("traceId") String traceId,
               @Param("clientType") String clientType,
               @Param("ipEnc") String ipEnc,
               @Param("result") String result,
               @Param("riskLevel") String riskLevel,
               @Param("detail") String detail,
               @Param("occurredAt") Instant occurredAt);

    @Select("""
            <script>
            SELECT id, tenant_id, log_no, event_type, module, action, operator_id, operator_name,
                   target_type, target_id, request_id, trace_id, client_type, result, risk_level,
                   detail::text AS detail, occurred_at
              FROM hub_audit_log
             WHERE deleted = 0
             <if test="eventType != null and eventType != ''">AND event_type = #{eventType}</if>
             <if test="operatorId != null">AND operator_id = #{operatorId}</if>
             <if test="targetType != null and targetType != ''">AND target_type = #{targetType}</if>
             <if test="targetId != null and targetId != ''">AND target_id = #{targetId}</if>
             <if test="requestId != null and requestId != ''">AND request_id = #{requestId}</if>
             <if test="result != null and result != ''">AND result = #{result}</if>
             <if test="from != null">AND occurred_at &gt;= #{from,jdbcType=TIMESTAMP}</if>
             <if test="to != null">AND occurred_at &lt; #{to,jdbcType=TIMESTAMP}</if>
             ORDER BY occurred_at DESC, id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> findPage(@Param("eventType") String eventType,
                                       @Param("operatorId") Long operatorId,
                                       @Param("targetType") String targetType,
                                       @Param("targetId") String targetId,
                                       @Param("requestId") String requestId,
                                       @Param("result") String result,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(*) FROM hub_audit_log
             WHERE deleted = 0
             <if test="eventType != null and eventType != ''">AND event_type = #{eventType}</if>
             <if test="operatorId != null">AND operator_id = #{operatorId}</if>
             <if test="targetType != null and targetType != ''">AND target_type = #{targetType}</if>
             <if test="targetId != null and targetId != ''">AND target_id = #{targetId}</if>
             <if test="requestId != null and requestId != ''">AND request_id = #{requestId}</if>
             <if test="result != null and result != ''">AND result = #{result}</if>
             <if test="from != null">AND occurred_at &gt;= #{from,jdbcType=TIMESTAMP}</if>
             <if test="to != null">AND occurred_at &lt; #{to,jdbcType=TIMESTAMP}</if>
            </script>
            """)
    long count(@Param("eventType") String eventType,
               @Param("operatorId") Long operatorId,
               @Param("targetType") String targetType,
               @Param("targetId") String targetId,
               @Param("requestId") String requestId,
               @Param("result") String result,
               @Param("from") Instant from,
               @Param("to") Instant to);

    @Select("""
            SELECT id, tenant_id, log_no, event_type, module, action, operator_id, operator_name,
                   target_type, target_id, request_id, trace_id, client_type, result, risk_level,
                   detail::text AS detail, occurred_at
              FROM hub_audit_log
             WHERE id = #{id}
            """)
    Map<String, Object> findContentById(@Param("id") long id);

    @Select("""
            SELECT id, tenant_id, log_no, event_type, module, action, operator_id, operator_name,
                   target_type, target_id, request_id, trace_id, client_type, result, risk_level,
                   detail::text AS detail, occurred_at
              FROM hub_audit_log
             WHERE id = #{id} AND deleted = 0
             ORDER BY occurred_at DESC
             LIMIT 1
            """)
    Map<String, Object> findById(@Param("id") long id);
}
