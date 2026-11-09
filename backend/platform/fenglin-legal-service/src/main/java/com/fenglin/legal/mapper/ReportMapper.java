package com.fenglin.legal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 云法管理端报表 Mapper（只读聚合）。
 */
@Mapper
public interface ReportMapper {

    @Select("SELECT count(1) FROM legal_contract WHERE tenant_id = #{tenantId} AND deleted = 0")
    long totalContracts(@Param("tenantId") long tenantId);

    @Select("SELECT status, count(1) AS total FROM legal_contract WHERE tenant_id = #{tenantId} "
            + "AND deleted = 0 GROUP BY status ORDER BY status")
    List<Map<String, Object>> contractByStatus(@Param("tenantId") long tenantId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM legal_contract WHERE tenant_id = #{tenantId} "
            + "AND deleted = 0 AND status IN ('APPROVED','SEALING','SIGNING','SIGNED','EFFECTIVE')")
    java.math.BigDecimal effectiveAmount(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_contract WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND end_date IS NOT NULL AND end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 30")
    long expiringIn30Days(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_contract WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND status = 'ARCHIVED'")
    long archivedContracts(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_dlp_event WHERE tenant_id = #{tenantId}")
    long totalEvents(@Param("tenantId") long tenantId);

    @Select("SELECT status, count(1) AS total FROM legal_dlp_event WHERE tenant_id = #{tenantId} "
            + "GROUP BY status ORDER BY status")
    List<Map<String, Object>> eventByStatus(@Param("tenantId") long tenantId);

    @Select("SELECT data_level, count(1) AS total FROM legal_dlp_event WHERE tenant_id = #{tenantId} "
            + "GROUP BY data_level ORDER BY data_level")
    List<Map<String, Object>> eventByLevel(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_dlp_event WHERE tenant_id = #{tenantId} AND action_taken = 'BLOCK'")
    long blockedEvents(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_dlp_event WHERE tenant_id = #{tenantId} "
            + "AND status IN ('NEW','TRIAGING','INVESTIGATING','ESCALATED')")
    long openEvents(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_sign_record WHERE tenant_id = #{tenantId} AND status = 'SIGNED'")
    long signedRecords(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_evidence WHERE tenant_id = #{tenantId}")
    long evidenceCount(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_evidence WHERE tenant_id = #{tenantId} AND verified = true")
    long verifiedEvidenceCount(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_contract c WHERE c.tenant_id = #{tenantId} AND c.deleted = 0 "
            + "AND c.status IN ('SIGNED','EFFECTIVE') "
            + "AND NOT EXISTS (SELECT 1 FROM legal_evidence e WHERE e.tenant_id = c.tenant_id "
            + "AND e.contract_id = c.id)")
    long contractsWithoutEvidence(@Param("tenantId") long tenantId);

    // ---------- 合同维度聚合 ----------
    @Select("SELECT category AS key, count(1) AS total, COALESCE(SUM(amount), 0) AS amount "
            + "FROM legal_contract WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "GROUP BY category ORDER BY total DESC")
    List<Map<String, Object>> contractByCategory(@Param("tenantId") long tenantId);

    @Select("SELECT COALESCE(org_id, 0) AS key, count(1) AS total, COALESCE(SUM(amount), 0) AS amount "
            + "FROM legal_contract WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "GROUP BY COALESCE(org_id, 0) ORDER BY total DESC")
    List<Map<String, Object>> contractByOrg(@Param("tenantId") long tenantId);

    @Select("SELECT to_char(created_at, 'YYYY-MM') AS key, count(1) AS total, COALESCE(SUM(amount), 0) AS amount "
            + "FROM legal_contract WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "GROUP BY to_char(created_at, 'YYYY-MM') ORDER BY key")
    List<Map<String, Object>> contractByMonth(@Param("tenantId") long tenantId);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM legal_contract WHERE tenant_id = #{tenantId} AND deleted = 0")
    java.math.BigDecimal totalAmount(@Param("tenantId") long tenantId);

    // ---------- DLP 维度聚合 ----------
    @Select("SELECT channel_code AS key, count(1) AS events, "
            + "count(1) FILTER (WHERE action_taken = 'BLOCK') AS blocked, "
            + "count(1) FILTER (WHERE event_type = 'RELEASE_APPLY' AND status = 'APPROVED') AS released, "
            + "0 AS false_positive FROM legal_dlp_event WHERE tenant_id = #{tenantId} "
            + "GROUP BY channel_code ORDER BY events DESC")
    List<Map<String, Object>> eventByChannel(@Param("tenantId") long tenantId);

    @Select("SELECT COALESCE(org_id, 0) AS key, count(1) AS events, "
            + "count(1) FILTER (WHERE action_taken = 'BLOCK') AS blocked, "
            + "count(1) FILTER (WHERE event_type = 'RELEASE_APPLY' AND status = 'APPROVED') AS released, "
            + "0 AS false_positive FROM legal_dlp_event WHERE tenant_id = #{tenantId} "
            + "GROUP BY COALESCE(org_id, 0) ORDER BY events DESC")
    List<Map<String, Object>> eventByOrg(@Param("tenantId") long tenantId);

    @Select("SELECT COALESCE(user_id, 0) AS key, count(1) AS events, "
            + "count(1) FILTER (WHERE action_taken = 'BLOCK') AS blocked, "
            + "count(1) FILTER (WHERE event_type = 'RELEASE_APPLY' AND status = 'APPROVED') AS released, "
            + "0 AS false_positive FROM legal_dlp_event WHERE tenant_id = #{tenantId} "
            + "GROUP BY COALESCE(user_id, 0) ORDER BY events DESC")
    List<Map<String, Object>> eventByUser(@Param("tenantId") long tenantId);

    @Select("SELECT to_char(occurred_at, 'YYYY-MM') AS key, count(1) AS events, "
            + "count(1) FILTER (WHERE action_taken = 'BLOCK') AS blocked, "
            + "count(1) FILTER (WHERE event_type = 'RELEASE_APPLY' AND status = 'APPROVED') AS released, "
            + "0 AS false_positive FROM legal_dlp_event WHERE tenant_id = #{tenantId} "
            + "GROUP BY to_char(occurred_at, 'YYYY-MM') ORDER BY key")
    List<Map<String, Object>> eventByMonth(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_dlp_event WHERE tenant_id = #{tenantId} "
            + "AND event_type = 'RELEASE_APPLY' AND status = 'APPROVED'")
    long releasedEvents(@Param("tenantId") long tenantId);

    @Select("SELECT count(DISTINCT h.event_id) FROM legal_dlp_event_handle h "
            + "WHERE h.tenant_id = #{tenantId} AND h.action = 'FALSE_POSITIVE'")
    long falsePositiveEvents(@Param("tenantId") long tenantId);

    // ---------- 签章合规 ----------
    @Select("SELECT count(DISTINCT contract_id) FROM legal_sign_record "
            + "WHERE tenant_id = #{tenantId} AND status = 'SIGNED'")
    long signedContracts(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_sign_record WHERE tenant_id = #{tenantId} AND status = 'SIGNED' "
            + "AND real_name_verified = true")
    long realNameVerifiedRecords(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_sign_record WHERE tenant_id = #{tenantId} AND status = 'SIGNED' "
            + "AND will_confirmed = true")
    long willConfirmedRecords(@Param("tenantId") long tenantId);

    @Select("SELECT count(1) FROM legal_sign_record WHERE tenant_id = #{tenantId} AND status = 'SIGNED' "
            + "AND tsa_token IS NOT NULL")
    long tsaCoveredRecords(@Param("tenantId") long tenantId);

    @Select("SELECT count(DISTINCT contract_id) FROM legal_evidence WHERE tenant_id = #{tenantId}")
    long contractsWithEvidence(@Param("tenantId") long tenantId);
}
