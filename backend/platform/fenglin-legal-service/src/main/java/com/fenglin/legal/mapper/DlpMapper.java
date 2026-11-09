package com.fenglin.legal.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * G2 DLP Mapper：敏感规则/策略/通道/事件/处置/水印/审计/白名单/终端。
 * 说明：文档未设独立放行单表，放行申请以 legal_dlp_event(event_type=RELEASE_APPLY) 承载。
 */
@Mapper
public interface DlpMapper {

    // ---------- 敏感规则 ----------
    @Insert("""
            INSERT INTO legal_sensitive_rule (id, tenant_id, rule_no, name, rule_type, expression,
                data_level, category, threshold, priority, enabled, remark, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{ruleNo}, #{name}, #{ruleType}, #{expression}, #{dataLevel},
                #{category}, #{threshold}, #{priority}, true, #{remark}, #{userId}, #{userId})
            """)
    void insertRule(@Param("id") long id, @Param("tenantId") long tenantId, @Param("ruleNo") String ruleNo,
                    @Param("name") String name, @Param("ruleType") String ruleType,
                    @Param("expression") String expression, @Param("dataLevel") String dataLevel,
                    @Param("category") String category, @Param("threshold") BigDecimal threshold,
                    @Param("priority") int priority, @Param("remark") String remark, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, rule_no, name, rule_type, expression, data_level, category, threshold, priority,
                   enabled, remark, created_at
            FROM legal_sensitive_rule WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="dataLevel != null and dataLevel != ''">AND data_level = #{dataLevel}</if>
              <if test="enabled != null">AND enabled = #{enabled}</if>
            ORDER BY priority, id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listRules(@Param("tenantId") long tenantId,
                                        @Param("dataLevel") String dataLevel, @Param("enabled") Boolean enabled,
                                        @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_sensitive_rule WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="dataLevel != null and dataLevel != ''">AND data_level = #{dataLevel}</if>
              <if test="enabled != null">AND enabled = #{enabled}</if>
            </script>
            """)
    long countRules(@Param("tenantId") long tenantId, @Param("dataLevel") String dataLevel,
                    @Param("enabled") Boolean enabled);

    @Select("""
            SELECT id, rule_no, name, rule_type, expression, data_level, category, threshold, priority, enabled
            FROM legal_sensitive_rule WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findRule(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, rule_no, name, rule_type, expression, data_level, category, threshold, priority, enabled
            FROM legal_sensitive_rule WHERE tenant_id = #{tenantId} AND enabled = true AND deleted = 0
            ORDER BY priority, id
            """)
    List<Map<String, Object>> listEnabledRules(@Param("tenantId") long tenantId);

    @Update("UPDATE legal_sensitive_rule SET enabled = #{enabled}, updated_by = #{userId}, "
            + "updated_at = now(), version = version + 1 WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateRuleEnabled(@Param("tenantId") long tenantId, @Param("id") long id,
                          @Param("enabled") boolean enabled, @Param("userId") long userId);

    // ---------- 策略 ----------
    @Insert("""
            INSERT INTO legal_dlp_policy (id, tenant_id, policy_no, name, data_level, rule_ids,
                scope_org_ids, scope_role_ids, action, channel_codes, effective_from, effective_to,
                priority, enabled, created_by, updated_by, version)
            VALUES (#{id}, #{tenantId}, #{policyNo}, #{name}, #{dataLevel}, #{ruleIds}::jsonb,
                #{scopeOrgIds}::jsonb, #{scopeRoleIds}::jsonb, #{action}, #{channelCodes}::jsonb,
                #{effectiveFrom}::timestamptz, #{effectiveTo}::timestamptz, #{priority}, true,
                #{userId}, #{userId}, 1)
            """)
    void insertPolicy(@Param("id") long id, @Param("tenantId") long tenantId, @Param("policyNo") String policyNo,
                      @Param("name") String name, @Param("dataLevel") String dataLevel,
                      @Param("ruleIds") String ruleIds, @Param("scopeOrgIds") String scopeOrgIds,
                      @Param("scopeRoleIds") String scopeRoleIds, @Param("action") String action,
                      @Param("channelCodes") String channelCodes, @Param("effectiveFrom") String effectiveFrom,
                      @Param("effectiveTo") String effectiveTo, @Param("priority") int priority,
                      @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, policy_no, name, data_level, rule_ids::text AS rule_ids, scope_org_ids::text AS scope_org_ids,
                   scope_role_ids::text AS scope_role_ids, action, channel_codes::text AS channel_codes,
                   effective_from, effective_to, priority, enabled, version, created_at
            FROM legal_dlp_policy WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="dataLevel != null and dataLevel != ''">AND data_level = #{dataLevel}</if>
              <if test="action != null and action != ''">AND action = #{action}</if>
            ORDER BY priority, id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listPolicies(@Param("tenantId") long tenantId,
                                           @Param("dataLevel") String dataLevel, @Param("action") String action,
                                           @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_dlp_policy WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="dataLevel != null and dataLevel != ''">AND data_level = #{dataLevel}</if>
              <if test="action != null and action != ''">AND action = #{action}</if>
            </script>
            """)
    long countPolicies(@Param("tenantId") long tenantId, @Param("dataLevel") String dataLevel,
                       @Param("action") String action);

    @Select("""
            SELECT id, policy_no, name, data_level, rule_ids::text AS rule_ids, scope_org_ids::text AS scope_org_ids,
                   scope_role_ids::text AS scope_role_ids, action, channel_codes::text AS channel_codes,
                   effective_from, effective_to, priority, enabled, version
            FROM legal_dlp_policy WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findPolicy(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, policy_no, name, data_level, rule_ids::text AS rule_ids, action,
                   channel_codes::text AS channel_codes, effective_from, effective_to, priority
            FROM legal_dlp_policy WHERE tenant_id = #{tenantId} AND enabled = true AND deleted = 0
              AND (effective_from IS NULL OR effective_from <= now())
              AND (effective_to IS NULL OR effective_to >= now())
            ORDER BY priority, id
            """)
    List<Map<String, Object>> listEffectivePolicies(@Param("tenantId") long tenantId);

    @Update("UPDATE legal_dlp_policy SET enabled = #{enabled}, updated_by = #{userId}, "
            + "updated_at = now(), version = version + 1 WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updatePolicyEnabled(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("enabled") boolean enabled, @Param("userId") long userId);

    /** 同 dataLevel + 同优先级 + 生效时间重叠视为冲突。 */
    @Select("""
            SELECT count(1) FROM legal_dlp_policy
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND enabled = true
              AND data_level = #{dataLevel} AND priority = #{priority}
              AND COALESCE(effective_from, '1970-01-01'::timestamptz)
                    <= COALESCE(#{effectiveTo}::timestamptz, '9999-12-31'::timestamptz)
              AND COALESCE(effective_to, '9999-12-31'::timestamptz)
                    >= COALESCE(#{effectiveFrom}::timestamptz, '1970-01-01'::timestamptz)
            """)
    long countPolicyConflict(@Param("tenantId") long tenantId, @Param("dataLevel") String dataLevel,
                             @Param("priority") int priority, @Param("effectiveFrom") String effectiveFrom,
                             @Param("effectiveTo") String effectiveTo);

    // ---------- 通道 ----------
    @Insert("""
            INSERT INTO legal_dlp_channel (id, tenant_id, policy_id, channel_code, enabled, default_action,
                params, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{policyId}, #{channelCode}, #{enabled}, #{defaultAction},
                #{params}::jsonb, #{userId}, #{userId})
            """)
    void insertChannel(@Param("id") long id, @Param("tenantId") long tenantId, @Param("policyId") Long policyId,
                       @Param("channelCode") String channelCode, @Param("enabled") boolean enabled,
                       @Param("defaultAction") String defaultAction, @Param("params") String params,
                       @Param("userId") long userId);

    @Select("""
            SELECT id, policy_id, channel_code, enabled, default_action, params::text AS params
            FROM legal_dlp_channel WHERE tenant_id = #{tenantId} AND deleted = 0
              AND channel_code = #{channelCode}
              AND COALESCE(policy_id, 0) = COALESCE(#{policyId}, 0)
            LIMIT 1
            """)
    Map<String, Object> findChannel(@Param("tenantId") long tenantId, @Param("policyId") Long policyId,
                                    @Param("channelCode") String channelCode);

    @Update("UPDATE legal_dlp_channel SET enabled = #{enabled}, default_action = #{defaultAction}, "
            + "params = #{params}::jsonb, updated_by = #{userId}, updated_at = now(), version = version + 1 "
            + "WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateChannel(@Param("tenantId") long tenantId, @Param("id") long id, @Param("enabled") boolean enabled,
                      @Param("defaultAction") String defaultAction, @Param("params") String params,
                      @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, policy_id, channel_code, enabled, default_action, params::text AS params
            FROM legal_dlp_channel WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="policyId != null">AND policy_id = #{policyId}</if>
            ORDER BY channel_code
            </script>
            """)
    List<Map<String, Object>> listChannels(@Param("tenantId") long tenantId, @Param("policyId") Long policyId);

    // ---------- 事件 ----------
    @Insert("""
            INSERT INTO legal_dlp_event (id, tenant_id, event_no, event_type, channel_code, user_id, org_id,
                terminal_id, data_level, file_name, file_fingerprint, matched_rule_ids, matched_snippet,
                action_taken, risk_score, status, occurred_at, source, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{eventNo}, #{eventType}, #{channelCode}, #{userIdValue}, #{orgId},
                #{terminalId}, #{dataLevel}, #{fileName}, #{fileFingerprint}, #{matchedRuleIds}::jsonb,
                #{matchedSnippet}, #{actionTaken}, #{riskScore}, #{status}, #{occurredAt}::timestamptz,
                #{source}, #{userId}, #{userId})
            ON CONFLICT (tenant_id, event_no) DO NOTHING
            """)
    int insertEvent(@Param("id") long id, @Param("tenantId") long tenantId, @Param("eventNo") String eventNo,
                    @Param("eventType") String eventType, @Param("channelCode") String channelCode,
                    @Param("userIdValue") Long userIdValue, @Param("orgId") Long orgId,
                    @Param("terminalId") Long terminalId, @Param("dataLevel") String dataLevel,
                    @Param("fileName") String fileName, @Param("fileFingerprint") String fileFingerprint,
                    @Param("matchedRuleIds") String matchedRuleIds, @Param("matchedSnippet") String matchedSnippet,
                    @Param("actionTaken") String actionTaken, @Param("riskScore") BigDecimal riskScore,
                    @Param("status") String status, @Param("occurredAt") String occurredAt,
                    @Param("source") String source, @Param("userId") long userId);

    @Select("""
            SELECT id, event_no, event_type, channel_code, user_id, org_id, terminal_id, data_level,
                   file_name, matched_rule_ids::text AS matched_rule_ids, matched_snippet, action_taken,
                   release_id, risk_score, status, occurred_at, source, created_at
            FROM legal_dlp_event WHERE tenant_id = #{tenantId} AND event_no = #{eventNo}
            """)
    Map<String, Object> findEventByNo(@Param("tenantId") long tenantId, @Param("eventNo") String eventNo);

    @Select("""
            SELECT id, event_no, event_type, channel_code, user_id, org_id, terminal_id, data_level,
                   file_name, matched_rule_ids::text AS matched_rule_ids, matched_snippet, action_taken,
                   release_id, risk_score, status, occurred_at, source, created_at
            FROM legal_dlp_event WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    Map<String, Object> findEvent(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id, event_no, event_type, channel_code, user_id, org_id, terminal_id, data_level,
                   file_name, action_taken, risk_score, status, occurred_at, source, created_at
            FROM legal_dlp_event WHERE tenant_id = #{tenantId}
              <if test="dataLevel != null and dataLevel != ''">AND data_level = #{dataLevel}</if>
              <if test="channelCode != null and channelCode != ''">AND channel_code = #{channelCode}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
              <if test="eventType != null and eventType != ''">AND event_type = #{eventType}</if>
            ORDER BY occurred_at DESC, id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listEvents(@Param("tenantId") long tenantId,
                                         @Param("dataLevel") String dataLevel, @Param("channelCode") String channelCode,
                                         @Param("status") String status, @Param("eventType") String eventType,
                                         @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_dlp_event WHERE tenant_id = #{tenantId}
              <if test="dataLevel != null and dataLevel != ''">AND data_level = #{dataLevel}</if>
              <if test="channelCode != null and channelCode != ''">AND channel_code = #{channelCode}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
              <if test="eventType != null and eventType != ''">AND event_type = #{eventType}</if>
            </script>
            """)
    long countEvents(@Param("tenantId") long tenantId, @Param("dataLevel") String dataLevel,
                     @Param("channelCode") String channelCode, @Param("status") String status,
                     @Param("eventType") String eventType);

    @Update("UPDATE legal_dlp_event SET status = #{status}, action_taken = COALESCE(#{actionTaken}, action_taken), "
            + "release_id = COALESCE(#{releaseId}, release_id), updated_by = #{userId}, "
            + "updated_at = now(), version = version + 1 WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateEventStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                          @Param("status") String status, @Param("actionTaken") String actionTaken,
                          @Param("releaseId") Long releaseId, @Param("userId") long userId);

    @Select("SELECT prev_hash, block_hash, seq_no FROM legal_dlp_audit_log WHERE tenant_id = #{tenantId} "
            + "AND chain_id = #{chainId} ORDER BY seq_no DESC LIMIT 1")
    Map<String, Object> lastAudit(@Param("tenantId") long tenantId, @Param("chainId") String chainId);

    // ---------- 处置 ----------
    @Insert("""
            INSERT INTO legal_dlp_event_handle (id, tenant_id, event_id, handler_id, action, conclusion,
                rectify_item, attachment_ref, sla_deadline, acted_at, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{eventId}, #{userId}, #{action}, #{conclusion}, #{rectifyItem},
                #{attachmentRef}, #{slaDeadline}::timestamptz, now(), #{userId}, #{userId})
            """)
    void insertEventHandle(@Param("id") long id, @Param("tenantId") long tenantId,
                           @Param("eventId") long eventId, @Param("action") String action,
                           @Param("conclusion") String conclusion, @Param("rectifyItem") String rectifyItem,
                           @Param("attachmentRef") String attachmentRef, @Param("slaDeadline") String slaDeadline,
                           @Param("userId") long userId);

    @Select("""
            SELECT id, event_id, handler_id, action, conclusion, rectify_item, attachment_ref,
                   sla_deadline, acted_at
            FROM legal_dlp_event_handle WHERE tenant_id = #{tenantId} AND event_id = #{eventId}
            ORDER BY id DESC
            """)
    List<Map<String, Object>> listEventHandles(@Param("tenantId") long tenantId, @Param("eventId") long eventId);

    // ---------- 白名单 ----------
    @Insert("""
            INSERT INTO legal_dlp_whitelist (id, tenant_id, name, channel_code, match_type, match_value,
                data_level, reason, approval_record_id, effective_from, effective_to, status,
                created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{name}, #{channelCode}, #{matchType}, #{matchValue}, #{dataLevel},
                #{reason}, #{approvalRecordId}, #{effectiveFrom}::timestamptz, #{effectiveTo}::timestamptz,
                'ACTIVE', #{userId}, #{userId})
            """)
    void insertWhitelist(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                         @Param("channelCode") String channelCode, @Param("matchType") String matchType,
                         @Param("matchValue") String matchValue, @Param("dataLevel") String dataLevel,
                         @Param("reason") String reason, @Param("approvalRecordId") Long approvalRecordId,
                         @Param("effectiveFrom") String effectiveFrom, @Param("effectiveTo") String effectiveTo,
                         @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, name, channel_code, match_type, match_value, data_level, reason, approval_record_id,
                   effective_from, effective_to, status, created_at
            FROM legal_dlp_whitelist WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="channelCode != null and channelCode != ''">AND channel_code = #{channelCode}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listWhitelist(@Param("tenantId") long tenantId,
                                            @Param("channelCode") String channelCode, @Param("status") String status,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_dlp_whitelist WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="channelCode != null and channelCode != ''">AND channel_code = #{channelCode}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </script>
            """)
    long countWhitelist(@Param("tenantId") long tenantId, @Param("channelCode") String channelCode,
                        @Param("status") String status);

    @Select("""
            SELECT id, name, channel_code, match_type, match_value, data_level, effective_from, effective_to, status
            FROM legal_dlp_whitelist WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 'ACTIVE'
              AND effective_to >= now()
            """)
    List<Map<String, Object>> listActiveWhitelist(@Param("tenantId") long tenantId);

    @Update("UPDATE legal_dlp_whitelist SET status = #{status}, updated_by = #{userId}, "
            + "updated_at = now(), version = version + 1 WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateWhitelistStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                              @Param("status") String status, @Param("userId") long userId);

    // ---------- 终端 ----------
    @Insert("""
            INSERT INTO legal_dlp_terminal (id, tenant_id, terminal_no, hostname, mac_hash, os_type,
                os_version, user_id, agent_version, policy_version, last_heartbeat_at, status,
                created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{terminalNo}, #{hostname}, #{macHash}, #{osType}, #{osVersion},
                #{userIdValue}, #{agentVersion}, #{policyVersion}, now(), 'ONLINE', #{userId}, #{userId})
            """)
    void insertTerminal(@Param("id") long id, @Param("tenantId") long tenantId, @Param("terminalNo") String terminalNo,
                        @Param("hostname") String hostname, @Param("macHash") String macHash,
                        @Param("osType") String osType, @Param("osVersion") String osVersion,
                        @Param("userIdValue") Long userIdValue, @Param("agentVersion") String agentVersion,
                        @Param("policyVersion") Integer policyVersion, @Param("userId") long userId);

    @Select("""
            SELECT id, terminal_no, hostname, mac_hash, os_type, os_version, user_id, agent_version,
                   policy_version, last_heartbeat_at, status
            FROM legal_dlp_terminal WHERE tenant_id = #{tenantId} AND terminal_no = #{terminalNo} AND deleted = 0
            """)
    Map<String, Object> findTerminalByNo(@Param("tenantId") long tenantId, @Param("terminalNo") String terminalNo);

    @Update("UPDATE legal_dlp_terminal SET status = 'ONLINE', last_heartbeat_at = now(), "
            + "agent_version = COALESCE(#{agentVersion}, agent_version), "
            + "policy_version = COALESCE(#{policyVersion}, policy_version), "
            + "updated_by = #{userId}, updated_at = now(), version = version + 1 "
            + "WHERE tenant_id = #{tenantId} AND id = #{id}")
    int heartbeat(@Param("tenantId") long tenantId, @Param("id") long id,
                  @Param("agentVersion") String agentVersion, @Param("policyVersion") Integer policyVersion,
                  @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, terminal_no, hostname, mac_hash, os_type, os_version, user_id, agent_version,
                   policy_version, last_heartbeat_at, status
            FROM legal_dlp_terminal WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="status != null and status != ''">AND status = #{status}</if>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listTerminals(@Param("tenantId") long tenantId, @Param("status") String status,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_dlp_terminal WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="status != null and status != ''">AND status = #{status}</if>
            </script>
            """)
    long countTerminals(@Param("tenantId") long tenantId, @Param("status") String status);

    // ---------- 水印 ----------
    @Insert("""
            INSERT INTO legal_watermark_record (id, tenant_id, watermark_no, biz_type, biz_id, watermark_type,
                embed_code, user_id, terminal_id, ip, file_hash, trace_status, applied_at, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{watermarkNo}, #{bizType}, #{bizId}, #{watermarkType}, #{embedCode},
                #{userIdValue}, #{terminalId}, #{ip}::inet, #{fileHash}, 'NONE', now(), #{userId}, #{userId})
            """)
    void insertWatermark(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("watermarkNo") String watermarkNo, @Param("bizType") String bizType,
                         @Param("bizId") Long bizId, @Param("watermarkType") String watermarkType,
                         @Param("embedCode") String embedCode, @Param("userIdValue") Long userIdValue,
                         @Param("terminalId") Long terminalId, @Param("ip") String ip,
                         @Param("fileHash") String fileHash, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, watermark_no, biz_type, biz_id, watermark_type, embed_code, user_id, terminal_id,
                   ip::text AS ip, file_hash, trace_status, traced_event_id, applied_at
            FROM legal_watermark_record WHERE tenant_id = #{tenantId}
              <if test="bizType != null and bizType != ''">AND biz_type = #{bizType}</if>
              <if test="traceStatus != null and traceStatus != ''">AND trace_status = #{traceStatus}</if>
            ORDER BY applied_at DESC, id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listWatermarks(@Param("tenantId") long tenantId, @Param("bizType") String bizType,
                                             @Param("traceStatus") String traceStatus,
                                             @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT count(1) FROM legal_watermark_record WHERE tenant_id = #{tenantId}")
    long countWatermarks(@Param("tenantId") long tenantId);

    @Select("""
            SELECT id, watermark_no, biz_type, biz_id, watermark_type, embed_code, user_id, terminal_id,
                   ip::text AS ip, file_hash, trace_status, applied_at
            FROM legal_watermark_record WHERE tenant_id = #{tenantId} AND embed_code = #{embedCode}
            """)
    Map<String, Object> findWatermarkByEmbed(@Param("tenantId") long tenantId,
                                             @Param("embedCode") String embedCode);

    @Select("""
            SELECT id, watermark_no, biz_type, biz_id, watermark_type, embed_code, user_id, terminal_id,
                   ip::text AS ip, file_hash, trace_status, applied_at
            FROM legal_watermark_record WHERE tenant_id = #{tenantId} AND file_hash = #{fileHash}
            ORDER BY applied_at DESC LIMIT 1
            """)
    Map<String, Object> findWatermarkByFileHash(@Param("tenantId") long tenantId,
                                                @Param("fileHash") String fileHash);

    @Update("UPDATE legal_watermark_record SET trace_status = 'TRACED', traced_event_id = #{eventId}, "
            + "updated_by = #{userId}, updated_at = now(), version = version + 1 "
            + "WHERE tenant_id = #{tenantId} AND id = #{id}")
    int markTraced(@Param("tenantId") long tenantId, @Param("id") long id, @Param("eventId") Long eventId,
                   @Param("userId") long userId);

    // ---------- 审计 ----------
    @Insert("""
            INSERT INTO legal_dlp_audit_log (id, tenant_id, log_id, action, operator_id, target_type,
                target_id, detail, result, occurred_at, chain_id, seq_no, prev_hash, block_hash,
                worm_ref, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{logId}, #{action}, #{operatorId}, #{targetType}, #{targetId},
                #{detail}::jsonb, #{result}, now(), #{chainId}, #{seqNo}, #{prevHash}, #{blockHash},
                #{wormRef}, #{userId}, #{userId})
            """)
    void insertAuditLog(@Param("id") long id, @Param("tenantId") long tenantId, @Param("logId") String logId,
                        @Param("action") String action, @Param("operatorId") Long operatorId,
                        @Param("targetType") String targetType, @Param("targetId") Long targetId,
                        @Param("detail") String detail, @Param("result") String result,
                        @Param("chainId") String chainId, @Param("seqNo") long seqNo,
                        @Param("prevHash") String prevHash, @Param("blockHash") String blockHash,
                        @Param("wormRef") String wormRef, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, log_id, action, operator_id, target_type, target_id, detail::text AS detail,
                   result, occurred_at, chain_id, seq_no, prev_hash, block_hash, worm_ref
            FROM legal_dlp_audit_log WHERE tenant_id = #{tenantId}
              <if test="action != null and action != ''">AND action = #{action}</if>
            ORDER BY occurred_at DESC, id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listAuditLogs(@Param("tenantId") long tenantId, @Param("action") String action,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_dlp_audit_log WHERE tenant_id = #{tenantId}
              <if test="action != null and action != ''">AND action = #{action}</if>
            </script>
            """)
    long countAuditLogs(@Param("tenantId") long tenantId, @Param("action") String action);

    @Select("""
            <script>
            SELECT id, log_id, chain_id, seq_no, prev_hash, block_hash, action, occurred_at
            FROM legal_dlp_audit_log WHERE tenant_id = #{tenantId}
              <if test="chainId != null and chainId != ''">AND chain_id = #{chainId}</if>
            ORDER BY chain_id, seq_no
            </script>
            """)
    List<Map<String, Object>> listAuditChain(@Param("tenantId") long tenantId, @Param("chainId") String chainId);
}
