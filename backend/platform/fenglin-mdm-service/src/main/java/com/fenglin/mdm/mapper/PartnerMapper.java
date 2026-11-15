package com.fenglin.mdm.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * MDM 往来单位三表访问（注解 SQL，返回 Map 行）。
 */
@Mapper
public interface PartnerMapper {

    String PARTNER_COLUMNS = """
            id AS "id", partner_code AS "partnerCode", name AS "name", short_name AS "shortName",
            unified_credit_code AS "unifiedCreditCode", legal_rep AS "legalRep",
            contact_name AS "contactName", contact_phone AS "contactPhone", contact_email AS "contactEmail",
            partner_type AS "partnerType", status AS "status", source_domain AS "sourceDomain",
            source_ref AS "sourceRef", normalized_name AS "normalizedName", dedup_key AS "dedupKey",
            merged_into AS "mergedInto", remark AS "remark", created_at AS "createdAt"
            """;

    @Insert("""
            INSERT INTO mdm_business_partner
                (id, tenant_id, partner_code, name, short_name, unified_credit_code, legal_rep,
                 contact_name, contact_phone, contact_email, partner_type, status, source_domain,
                 source_ref, normalized_name, dedup_key, remark, created_by)
            VALUES (#{id}, #{tenantId}, #{partnerCode}, #{name}, #{shortName}, #{unifiedCreditCode},
                    #{legalRep}, #{contactName}, #{contactPhone}, #{contactEmail}, #{partnerType},
                    'ENABLED', #{sourceDomain}, #{sourceRef}, #{normalizedName}, #{dedupKey},
                    #{remark}, #{userId})
            """)
    void insertPartner(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("partnerCode") String partnerCode, @Param("name") String name,
                       @Param("shortName") String shortName, @Param("unifiedCreditCode") String creditCode,
                       @Param("legalRep") String legalRep, @Param("contactName") String contactName,
                       @Param("contactPhone") String contactPhone, @Param("contactEmail") String contactEmail,
                       @Param("partnerType") String partnerType, @Param("sourceDomain") String sourceDomain,
                       @Param("sourceRef") String sourceRef, @Param("normalizedName") String normalizedName,
                       @Param("dedupKey") String dedupKey, @Param("remark") String remark,
                       @Param("userId") long userId);

    @Select("SELECT " + PARTNER_COLUMNS + " FROM mdm_business_partner"
            + " WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    Map<String, Object> findPartner(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT " + PARTNER_COLUMNS + " FROM mdm_business_partner"
            + " WHERE tenant_id = #{tenantId} AND dedup_key = #{dedupKey} AND deleted = 0 LIMIT 1")
    Map<String, Object> findByDedup(@Param("tenantId") long tenantId, @Param("dedupKey") String dedupKey);

    @Select("SELECT " + PARTNER_COLUMNS + " FROM mdm_business_partner"
            + " WHERE tenant_id = #{tenantId} AND unified_credit_code = #{creditCode}"
            + " AND deleted = 0 LIMIT 1")
    Map<String, Object> findByCreditCode(@Param("tenantId") long tenantId,
                                         @Param("creditCode") String creditCode);

    @Select("""
            SELECT id AS "id", partner_code AS "partnerCode", name AS "name",
                   normalized_name AS "normalizedName", status AS "status"
            FROM mdm_business_partner
            WHERE tenant_id = #{tenantId} AND deleted = 0 AND status <> 'MERGED'
              AND id <> #{excludeId} AND normalized_name <> #{normalized}
              AND (normalized_name LIKE #{prefix} || '%'
                   OR #{normalized} LIKE normalized_name || '%'
                   OR normalized_name LIKE '%' || #{normalized} || '%')
            ORDER BY length(normalized_name), id
            LIMIT #{limit}
            """)
    List<Map<String, Object>> findCandidates(@Param("tenantId") long tenantId,
                                             @Param("excludeId") long excludeId,
                                             @Param("normalized") String normalized,
                                             @Param("prefix") String prefix,
                                             @Param("limit") int limit);

    @Select("SELECT " + PARTNER_COLUMNS + """
            FROM mdm_business_partner p
            WHERE p.tenant_id = #{tenantId} AND p.deleted = 0
              AND (CAST(#{status,jdbcType=VARCHAR} AS text) IS NULL OR p.status = #{status})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR p.name ILIKE '%' || #{keyword} || '%'
                   OR p.partner_code ILIKE '%' || #{keyword} || '%'
                   OR p.unified_credit_code ILIKE '%' || #{keyword} || '%'
                   OR p.normalized_name LIKE '%' || #{normalizedKeyword} || '%'
                   OR EXISTS (SELECT 1 FROM mdm_partner_alias a
                              WHERE a.tenant_id = p.tenant_id AND a.partner_id = p.id
                                AND a.deleted = 0
                                AND a.normalized_value LIKE '%' || #{normalizedKeyword} || '%'))
            ORDER BY p.created_at DESC, p.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listPartners(@Param("tenantId") long tenantId,
                                           @Param("keyword") String keyword,
                                           @Param("normalizedKeyword") String normalizedKeyword,
                                           @Param("status") String status,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM mdm_business_partner WHERE tenant_id = #{tenantId} AND deleted = 0")
    long countPartners(@Param("tenantId") long tenantId);

    @Update("""
            UPDATE mdm_business_partner SET status = #{status}, updated_by = #{userId},
                updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0 AND status IN ('ENABLED', 'DISABLED')
            """)
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                     @Param("status") String status, @Param("userId") long userId);

    @Update("""
            UPDATE mdm_business_partner SET status = 'MERGED', merged_into = #{targetId},
                updated_by = #{userId}, updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0 AND status = 'ENABLED'
            """)
    int markMerged(@Param("tenantId") long tenantId, @Param("id") long id,
                   @Param("targetId") long targetId, @Param("userId") long userId);

    @Update("""
            UPDATE mdm_business_partner SET status = 'ENABLED', merged_into = NULL,
                updated_by = #{userId}, updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0 AND status = 'MERGED'
            """)
    int restoreMerged(@Param("tenantId") long tenantId, @Param("id") long id,
                      @Param("userId") long userId);

    @Insert("""
            INSERT INTO mdm_partner_merge
                (id, tenant_id, source_partner_id, target_partner_id, status, reason, merged_by,
                 merged_at, created_by)
            VALUES (#{id}, #{tenantId}, #{sourceId}, #{targetId}, 'ACTIVE', #{reason}, #{userId},
                    now(), #{userId})
            """)
    void insertMerge(@Param("id") long id, @Param("tenantId") long tenantId,
                     @Param("sourceId") long sourceId, @Param("targetId") long targetId,
                     @Param("reason") String reason, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", source_partner_id AS "sourcePartnerId",
                   target_partner_id AS "targetPartnerId", status AS "status", reason AS "reason",
                   merged_at AS "mergedAt"
            FROM mdm_partner_merge
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findMerge(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", source_partner_id AS "sourcePartnerId",
                   target_partner_id AS "targetPartnerId", status AS "status", reason AS "reason",
                   merged_at AS "mergedAt"
            FROM mdm_partner_merge
            WHERE tenant_id = #{tenantId} AND source_partner_id = #{sourceId} AND status = 'ACTIVE'
              AND deleted = 0 LIMIT 1
            """)
    Map<String, Object> findActiveMerge(@Param("tenantId") long tenantId,
                                        @Param("sourceId") long sourceId);

    @Update("""
            UPDATE mdm_partner_merge SET status = 'REVOKED', revoked_by = #{userId},
                revoked_at = now(), updated_by = #{userId}, updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'ACTIVE' AND deleted = 0
            """)
    int revokeMerge(@Param("tenantId") long tenantId, @Param("id") long id,
                    @Param("userId") long userId);

    @Insert("""
            INSERT INTO mdm_partner_alias
                (id, tenant_id, partner_id, alias_type, alias_value, normalized_value,
                 source_merge_id, active, created_by)
            VALUES (#{id}, #{tenantId}, #{partnerId}, #{aliasType}, #{aliasValue}, #{normalizedValue},
                    #{sourceMergeId,jdbcType=BIGINT}, #{active}, #{userId})
            """)
    void insertAlias(@Param("id") long id, @Param("tenantId") long tenantId,
                     @Param("partnerId") long partnerId, @Param("aliasType") String aliasType,
                     @Param("aliasValue") String aliasValue, @Param("normalizedValue") String normalizedValue,
                     @Param("sourceMergeId") Long sourceMergeId, @Param("active") int active,
                     @Param("userId") long userId);

    @Update("""
            UPDATE mdm_partner_alias SET active = 0, updated_by = #{userId}, updated_at = now(),
                version = version + 1
            WHERE tenant_id = #{tenantId} AND source_merge_id = #{sourceMergeId} AND deleted = 0
            """)
    int deactivateAliases(@Param("tenantId") long tenantId,
                          @Param("sourceMergeId") long sourceMergeId, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", alias_type AS "aliasType", alias_value AS "aliasValue",
                   active AS "active"
            FROM mdm_partner_alias
            WHERE tenant_id = #{tenantId} AND partner_id = #{partnerId} AND deleted = 0
            ORDER BY created_at, id
            """)
    List<Map<String, Object>> listAliases(@Param("tenantId") long tenantId,
                                          @Param("partnerId") long partnerId);
}
