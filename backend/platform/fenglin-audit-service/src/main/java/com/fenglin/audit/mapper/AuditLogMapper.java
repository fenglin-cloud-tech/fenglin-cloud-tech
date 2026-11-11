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
             WHERE tenant_id = #{tenantId} AND deleted = 0
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
    List<Map<String, Object>> findPage(@Param("tenantId") long tenantId,
                                       @Param("eventType") String eventType,
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
             WHERE tenant_id = #{tenantId} AND deleted = 0
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
    long count(@Param("tenantId") long tenantId,
               @Param("eventType") String eventType,
               @Param("operatorId") Long operatorId,
               @Param("targetType") String targetType,
               @Param("targetId") String targetId,
               @Param("requestId") String requestId,
               @Param("result") String result,
               @Param("from") Instant from,
               @Param("to") Instant to);

    @Select("""
            <script>
            SELECT log_no, event_type, module, action, operator_id, operator_name,
                   target_type, target_id, request_id, client_type, result, risk_level,
                   detail::text AS detail, occurred_at
              FROM hub_audit_log
             WHERE tenant_id = #{tenantId} AND deleted = 0
             <if test="eventType != null and eventType != ''">AND event_type = #{eventType}</if>
             <if test="from != null">AND occurred_at &gt;= #{from,jdbcType=TIMESTAMP}</if>
             <if test="to != null">AND occurred_at &lt; #{to,jdbcType=TIMESTAMP}</if>
             ORDER BY occurred_at ASC, id ASC
            </script>
            """)
    List<Map<String, Object>> findForExport(@Param("tenantId") long tenantId,
                                            @Param("eventType") String eventType,
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

    /**
     * 同期审计日志条数，用于与链条数比对，识别"整段删链"这类只验现存行发现不了的缺口。
     */
    @Select("""
            <script>
            SELECT count(*) FROM hub_audit_log
             WHERE tenant_id = #{tenantId} AND deleted = 0
             <if test="from != null">AND occurred_at &gt;= #{from,jdbcType=TIMESTAMP}</if>
             <if test="to != null">AND occurred_at &lt;= #{to,jdbcType=TIMESTAMP}</if>
            </script>
            """)
    long countInRange(@Param("tenantId") long tenantId,
                      @Param("from") Instant from,
                      @Param("to") Instant to);

    /**
     * 未入链的审计日志汇总：总数、基线前/后分别计数、log_id 区间（雪花 ID 单调，可作缺口定位）。
     */
    @Select("""
            <script>
            SELECT count(*) AS total,
            <choose>
              <when test="baselineAt != null">
                count(*) FILTER (WHERE l.occurred_at &lt;= #{baselineAt,jdbcType=TIMESTAMP}) AS before_baseline,
                count(*) FILTER (WHERE l.occurred_at &gt; #{baselineAt,jdbcType=TIMESTAMP}) AS after_baseline,
              </when>
              <otherwise>
                0 AS before_baseline, 0 AS after_baseline,
              </otherwise>
            </choose>
                   min(l.id) AS from_log_id, max(l.id) AS to_log_id
              FROM hub_audit_log l
             WHERE l.tenant_id = #{tenantId} AND l.deleted = 0
               AND NOT EXISTS (
                   SELECT 1 FROM hub_audit_chain c
                    WHERE c.tenant_id = l.tenant_id AND c.log_id = l.id)
            <if test="from != null">AND l.occurred_at &gt;= #{from,jdbcType=TIMESTAMP}</if>
            <if test="to != null">AND l.occurred_at &lt;= #{to,jdbcType=TIMESTAMP}</if>
            </script>
            """)
    Map<String, Object> summarizeUnchainedLogs(@Param("tenantId") long tenantId,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to,
                                               @Param("baselineAt") Instant baselineAt);

    /**
     * 该租户最早一条已入链的基线记录，基线之前视为不可验证区间。
     */
    @Select("""
            SELECT l.id AS log_id, l.occurred_at AS occurred_at
              FROM hub_audit_log l
              JOIN hub_audit_chain c ON c.tenant_id = l.tenant_id AND c.log_id = l.id
             WHERE l.tenant_id = #{tenantId} AND l.deleted = 0 AND l.event_type = 'CHAIN_BASELINE'
             ORDER BY l.occurred_at ASC
             LIMIT 1
            """)
    Map<String, Object> findBaseline(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id, tenant_id, log_no, event_type, module, action, operator_id, operator_name,
                   target_type, target_id, request_id, trace_id, client_type, result, risk_level,
                   detail::text AS detail, occurred_at
              FROM hub_audit_log
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
             ORDER BY occurred_at DESC
             LIMIT 1
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);
}
