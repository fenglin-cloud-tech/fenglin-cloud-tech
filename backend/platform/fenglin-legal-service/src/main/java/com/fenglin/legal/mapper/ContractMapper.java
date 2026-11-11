package com.fenglin.legal.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * G1 合同域 Mapper：模板/版本/变量、合同/版本/条款、相对方、审批、履约、到期提醒。
 */
@Mapper
public interface ContractMapper {

    // ---------- 模板 ----------
    @Insert("""
            INSERT INTO legal_contract_template (id, tenant_id, template_no, name, category, owner_id,
                visible_scope, applied_org_ids, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{templateNo}, #{name}, #{category}, #{ownerId},
                #{visibleScope}, #{appliedOrgIds}::jsonb, 'DRAFT', #{userId}, #{userId})
            """)
    void insertTemplate(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("templateNo") String templateNo, @Param("name") String name,
                        @Param("category") String category, @Param("ownerId") Long ownerId,
                        @Param("visibleScope") String visibleScope, @Param("appliedOrgIds") String appliedOrgIds,
                        @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, uuid::text AS uuid, template_no, name, category, current_version_id, status,
                   applied_org_ids::text AS applied_org_ids, visible_scope, owner_id, remark,
                   created_at, updated_at, version
            FROM legal_contract_template
            WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="category != null and category != ''">AND category = #{category}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listTemplates(@Param("tenantId") long tenantId, @Param("category") String category,
                                            @Param("status") String status, @Param("offset") int offset,
                                            @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_contract_template
            WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="category != null and category != ''">AND category = #{category}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </script>
            """)
    long countTemplates(@Param("tenantId") long tenantId, @Param("category") String category,
                        @Param("status") String status);

    @Select("""
            SELECT id, uuid::text AS uuid, template_no, name, category, current_version_id, status,
                   applied_org_ids::text AS applied_org_ids, visible_scope, owner_id, remark,
                   created_at, updated_at, version
            FROM legal_contract_template WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findTemplate(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM legal_template_version "
            + "WHERE tenant_id = #{tenantId} AND template_id = #{templateId}")
    int nextTemplateVersionNo(@Param("tenantId") long tenantId, @Param("templateId") long templateId);

    @Insert("""
            INSERT INTO legal_template_version (id, tenant_id, template_id, version_no, content_ref,
                content_hash, change_summary, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{templateId}, #{versionNo}, #{contentRef},
                #{contentHash}, #{changeSummary}, 'DRAFT', #{userId}, #{userId})
            """)
    void insertTemplateVersion(@Param("id") long id, @Param("tenantId") long tenantId,
                               @Param("templateId") long templateId, @Param("versionNo") int versionNo,
                               @Param("contentRef") String contentRef, @Param("contentHash") String contentHash,
                               @Param("changeSummary") String changeSummary, @Param("userId") long userId);

    @Update("""
            UPDATE legal_template_version SET status = 'PUBLISHED', published_at = now(), published_by = #{userId},
                updated_by = #{userId}, updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{versionId}
            """)
    int publishTemplateVersion(@Param("tenantId") long tenantId, @Param("versionId") long versionId,
                               @Param("userId") long userId);

    @Update("""
            UPDATE legal_contract_template SET current_version_id = #{versionId}, status = 'PUBLISHED',
                updated_by = #{userId}, updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{templateId}
            """)
    int touchTemplatePublished(@Param("tenantId") long tenantId, @Param("templateId") long templateId,
                               @Param("versionId") long versionId, @Param("userId") long userId);

    @Select("""
            SELECT id, template_id, version_no, content_ref, content_hash, status, published_at
            FROM legal_template_version WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    Map<String, Object> findTemplateVersion(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, template_id, version_no, content_ref, content_hash, status
            FROM legal_template_version
            WHERE tenant_id = #{tenantId} AND template_id = #{templateId} AND status = 'PUBLISHED'
            ORDER BY version_no DESC LIMIT 1
            """)
    Map<String, Object> findPublishedTemplateVersion(@Param("tenantId") long tenantId,
                                                     @Param("templateId") long templateId);

    @Update("DELETE FROM legal_template_variable WHERE tenant_id = #{tenantId} "
            + "AND template_version_id = #{versionId}")
    int deleteTemplateVariables(@Param("tenantId") long tenantId, @Param("versionId") long versionId);

    @Insert("""
            INSERT INTO legal_template_variable (id, tenant_id, template_version_id, var_key, var_label,
                data_type, required, default_value, value_source, options, sort_no, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{versionId}, #{varKey}, #{varLabel}, #{dataType}, #{required},
                #{defaultValue}, #{valueSource}, #{options}::jsonb, #{sortNo}, #{userId}, #{userId})
            """)
    void insertTemplateVariable(@Param("id") long id, @Param("tenantId") long tenantId,
                                @Param("versionId") long versionId, @Param("varKey") String varKey,
                                @Param("varLabel") String varLabel, @Param("dataType") String dataType,
                                @Param("required") boolean required, @Param("defaultValue") String defaultValue,
                                @Param("valueSource") String valueSource, @Param("options") String options,
                                @Param("sortNo") int sortNo, @Param("userId") long userId);

    @Select("""
            SELECT id, template_version_id, var_key, var_label, data_type, required, default_value,
                   value_source, options::text AS options, sort_no
            FROM legal_template_variable WHERE tenant_id = #{tenantId} AND template_version_id = #{versionId}
            ORDER BY sort_no, id
            """)
    List<Map<String, Object>> listTemplateVariables(@Param("tenantId") long tenantId,
                                                    @Param("versionId") long versionId);

    // ---------- 合同 ----------
    @Insert("""
            INSERT INTO legal_contract (id, tenant_id, contract_no, name, category, template_id,
                template_version_id, current_version_id, party_a_id, party_b_id, amount, currency,
                start_date, end_date, owner_id, org_id, risk_level, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{contractNo}, #{name}, #{category}, #{templateId},
                #{templateVersionId}, #{currentVersionId}, #{partyAId}, #{partyBId}, #{amount}, #{currency},
                #{startDate}, #{endDate}, #{ownerId}, #{orgId}, #{riskLevel}, 'DRAFT', #{userId}, #{userId})
            """)
    void insertContract(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("contractNo") String contractNo, @Param("name") String name,
                        @Param("category") String category, @Param("templateId") Long templateId,
                        @Param("templateVersionId") Long templateVersionId, @Param("currentVersionId") Long currentVersionId,
                        @Param("partyAId") Long partyAId, @Param("partyBId") Long partyBId,
                        @Param("amount") BigDecimal amount, @Param("currency") String currency,
                        @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                        @Param("ownerId") Long ownerId, @Param("orgId") Long orgId,
                        @Param("riskLevel") String riskLevel, @Param("userId") long userId);

    @Update("""
            UPDATE legal_contract SET name = #{name}, category = #{category}, amount = #{amount},
                currency = #{currency}, start_date = #{startDate}, end_date = #{endDate},
                owner_id = #{ownerId}, org_id = #{orgId}, risk_level = #{riskLevel}, remark = #{remark},
                sign_date = #{signDate}, updated_by = #{userId}, updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateContract(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                       @Param("category") String category, @Param("amount") BigDecimal amount,
                       @Param("currency") String currency, @Param("startDate") LocalDate startDate,
                       @Param("endDate") LocalDate endDate, @Param("ownerId") Long ownerId,
                       @Param("orgId") Long orgId, @Param("riskLevel") String riskLevel,
                       @Param("remark") String remark, @Param("signDate") LocalDate signDate,
                       @Param("userId") long userId);

    @Select("""
            SELECT id, uuid::text AS uuid, contract_no, name, category, template_id, template_version_id,
                   current_version_id, party_a_id, party_b_id, amount, currency, start_date, end_date,
                   sign_date, owner_id, org_id, risk_level, status, seal_status, sign_status,
                   archive_hash, archived_at, chain_id, remark, created_at, updated_at, version
            FROM legal_contract WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findContract(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id, contract_no, name, category, amount, currency, start_date, end_date, owner_id,
                   org_id, risk_level, status, seal_status, sign_status, created_at
            FROM legal_contract
            WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="status != null and status != ''">AND status = #{status}</if>
              <if test="category != null and category != ''">AND category = #{category}</if>
              <if test="ownerId != null">AND owner_id = #{ownerId}</if>
              <if test="orgId != null">AND org_id = #{orgId}</if>
              <if test="keyword != null and keyword != ''">AND (name ILIKE '%' || #{keyword} || '%'
                   OR contract_no ILIKE '%' || #{keyword} || '%')</if>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listContracts(@Param("tenantId") long tenantId, @Param("status") String status,
                                            @Param("category") String category, @Param("ownerId") Long ownerId,
                                            @Param("orgId") Long orgId, @Param("keyword") String keyword,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_contract
            WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="status != null and status != ''">AND status = #{status}</if>
              <if test="category != null and category != ''">AND category = #{category}</if>
              <if test="ownerId != null">AND owner_id = #{ownerId}</if>
              <if test="orgId != null">AND org_id = #{orgId}</if>
              <if test="keyword != null and keyword != ''">AND (name ILIKE '%' || #{keyword} || '%'
                   OR contract_no ILIKE '%' || #{keyword} || '%')</if>
            </script>
            """)
    long countContracts(@Param("tenantId") long tenantId, @Param("status") String status,
                        @Param("category") String category, @Param("ownerId") Long ownerId,
                        @Param("orgId") Long orgId, @Param("keyword") String keyword);

    @Update("""
            UPDATE legal_contract SET status = #{status},
                seal_status = COALESCE(CAST(#{sealStatus} AS varchar), seal_status),
                sign_status = COALESCE(CAST(#{signStatus} AS varchar), sign_status),
                archive_hash = COALESCE(CAST(#{archiveHash} AS varchar), archive_hash),
                archived_at = CASE WHEN CAST(#{archiveHash} AS varchar) IS NOT NULL THEN now() ELSE archived_at END,
                updated_by = #{userId}, updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateContractState(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("status") String status, @Param("sealStatus") String sealStatus,
                            @Param("signStatus") String signStatus, @Param("archiveHash") String archiveHash,
                            @Param("userId") long userId);

    @Update("UPDATE legal_contract SET deleted = 1, updated_by = #{userId}, updated_at = now(), "
            + "version = version + 1 WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'DRAFT' "
            + "AND deleted = 0")
    int softDeleteDraft(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM legal_contract_version "
            + "WHERE tenant_id = #{tenantId} AND contract_id = #{contractId}")
    int nextContractVersionNo(@Param("tenantId") long tenantId, @Param("contractId") long contractId);

    @Update("UPDATE legal_contract SET current_version_id = #{versionId}, updated_by = #{userId}, "
            + "updated_at = now(), version = version + 1 WHERE tenant_id = #{tenantId} AND id = #{contractId}")
    int updateCurrentVersion(@Param("tenantId") long tenantId, @Param("contractId") long contractId,
                             @Param("versionId") long versionId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO legal_contract_version (id, tenant_id, contract_id, version_no, content_ref,
                content_hash, variable_values, change_summary, created_stage, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{contractId}, #{versionNo}, #{contentRef}, #{contentHash},
                #{variableValues}::jsonb, #{changeSummary}, #{stage}, #{userId}, #{userId})
            """)
    void insertContractVersion(@Param("id") long id, @Param("tenantId") long tenantId,
                               @Param("contractId") long contractId, @Param("versionNo") int versionNo,
                               @Param("contentRef") String contentRef, @Param("contentHash") String contentHash,
                               @Param("variableValues") String variableValues, @Param("changeSummary") String changeSummary,
                               @Param("stage") String stage, @Param("userId") long userId);

    @Select("""
            SELECT id, contract_id, version_no, content_ref, content_hash, variable_values::text AS variable_values,
                   change_summary, created_stage, created_at
            FROM legal_contract_version WHERE tenant_id = #{tenantId} AND contract_id = #{contractId}
            ORDER BY version_no DESC
            """)
    List<Map<String, Object>> listContractVersions(@Param("tenantId") long tenantId,
                                                   @Param("contractId") long contractId);

    @Select("""
            SELECT id, version_no, content_hash, variable_values::text AS variable_values FROM legal_contract_version
            WHERE tenant_id = #{tenantId} AND contract_id = #{contractId}
            ORDER BY version_no DESC LIMIT 1
            """)
    Map<String, Object> findLatestContractVersion(@Param("tenantId") long tenantId,
                                                  @Param("contractId") long contractId);

    @Update("""
            UPDATE legal_contract_clause SET title = #{title}, content = #{content}, risk_tag = #{riskTag},
                is_mandatory = #{mandatory}, updated_by = #{userId}, updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND contract_version_id = #{versionId} AND clause_no = #{clauseNo}
            """)
    int updateClause(@Param("tenantId") long tenantId, @Param("versionId") long versionId,
                     @Param("clauseNo") String clauseNo, @Param("title") String title,
                     @Param("content") String content, @Param("riskTag") String riskTag,
                     @Param("mandatory") boolean mandatory, @Param("userId") long userId);

    @Insert("""
            INSERT INTO legal_contract_clause (id, tenant_id, contract_id, contract_version_id, clause_no,
                title, content, is_mandatory, risk_tag, sort_no, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{contractId}, #{versionId}, #{clauseNo}, #{title}, #{content},
                #{mandatory}, #{riskTag}, #{sortNo}, #{userId}, #{userId})
            """)
    void insertClause(@Param("id") long id, @Param("tenantId") long tenantId,
                      @Param("contractId") long contractId, @Param("versionId") long versionId,
                      @Param("clauseNo") String clauseNo, @Param("title") String title,
                      @Param("content") String content, @Param("mandatory") boolean mandatory,
                      @Param("riskTag") String riskTag, @Param("sortNo") int sortNo, @Param("userId") long userId);

    @Select("""
            SELECT id, contract_id, contract_version_id, clause_no, title, content, is_mandatory, risk_tag, sort_no
            FROM legal_contract_clause WHERE tenant_id = #{tenantId} AND contract_id = #{contractId}
              AND contract_version_id = #{versionId}
            ORDER BY sort_no, clause_no
            """)
    List<Map<String, Object>> listClauses(@Param("tenantId") long tenantId, @Param("contractId") long contractId,
                                          @Param("versionId") long versionId);

    @Select("SELECT count(1) FROM legal_contract_clause WHERE tenant_id = #{tenantId} "
            + "AND contract_version_id = #{versionId} AND is_mandatory = true")
    long countMandatoryClauses(@Param("tenantId") long tenantId, @Param("versionId") long versionId);

    @Select("""
            SELECT id, biz_type, biz_id, proc_inst_id, task_id, node_name, approver_id, action, comment,
                   acted_at, contract_version_id
            FROM legal_approval_record WHERE tenant_id = #{tenantId} AND biz_type = #{bizType} AND biz_id = #{bizId}
            ORDER BY id DESC
            """)
    List<Map<String, Object>> listApprovalRecords(@Param("tenantId") long tenantId,
                                                  @Param("bizType") String bizType, @Param("bizId") long bizId);

    @Insert("""
            INSERT INTO legal_approval_record (id, tenant_id, biz_type, biz_id, proc_inst_id, task_id,
                node_name, approver_id, action, comment, acted_at, contract_version_id, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{bizType}, #{bizId}, #{procInstId}, #{taskId}, #{nodeName},
                #{approverId}, #{action}, #{comment}, now(), #{contractVersionId}, #{userId}, #{userId})
            """)
    void insertApprovalRecord(@Param("id") long id, @Param("tenantId") long tenantId,
                              @Param("bizType") String bizType, @Param("bizId") long bizId,
                              @Param("procInstId") String procInstId, @Param("taskId") String taskId,
                              @Param("nodeName") String nodeName, @Param("approverId") long approverId,
                              @Param("action") String action, @Param("comment") String comment,
                              @Param("contractVersionId") Long contractVersionId, @Param("userId") long userId);

    // ---------- 相对方 ----------
    @Insert("""
            INSERT INTO legal_contract_party (id, tenant_id, name, uscc, uscc_hash, contact_name,
                contact_mobile_enc, contact_mobile_hash, contact_mobile_tail, email, legal_rep, address,
                risk_level, status, remark, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{name}, #{uscc}, #{usccHash}, #{contactName},
                #{mobileEnc}, #{mobileHash}, #{mobileTail}, #{email}, #{legalRep}, #{address},
                #{riskLevel}, 'ACTIVE', #{remark}, #{userId}, #{userId})
            """)
    void insertParty(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
                     @Param("uscc") String uscc, @Param("usccHash") String usccHash,
                     @Param("contactName") String contactName, @Param("mobileEnc") String mobileEnc,
                     @Param("mobileHash") String mobileHash, @Param("mobileTail") String mobileTail,
                     @Param("email") String email, @Param("legalRep") String legalRep,
                     @Param("address") String address, @Param("riskLevel") String riskLevel,
                     @Param("remark") String remark, @Param("userId") long userId);

    @Select("""
            SELECT id, name, uscc, uscc_hash, contact_name, contact_mobile_enc, contact_mobile_tail,
                   email, legal_rep, address, risk_level, status, remark, created_at
            FROM legal_contract_party WHERE tenant_id = #{tenantId} AND uscc_hash = #{usccHash} AND deleted = 0
            """)
    Map<String, Object> findPartyByUsccHash(@Param("tenantId") long tenantId, @Param("usccHash") String usccHash);

    @Select("""
            <script>
            SELECT id, name, uscc, contact_name, contact_mobile_tail, email, legal_rep, address,
                   risk_level, status, created_at
            FROM legal_contract_party WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="keyword != null and keyword != ''">AND name ILIKE '%' || #{keyword} || '%'</if>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listParties(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
                                          @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_contract_party WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="keyword != null and keyword != ''">AND name ILIKE '%' || #{keyword} || '%'</if>
            </script>
            """)
    long countParties(@Param("tenantId") long tenantId, @Param("keyword") String keyword);

    @Select("""
            SELECT id, name, contact_mobile_enc
            FROM legal_contract_party WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findPartyPii(@Param("tenantId") long tenantId, @Param("id") long id);

    // ---------- 相对方手机号明文申请 ----------

    @Insert("""
            INSERT INTO legal_party_mobile_request
                (id, tenant_id, party_id, reason, status, applied_by, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{partyId}, #{reason}, 'PENDING', #{appliedBy}, #{appliedBy}, #{appliedBy})
            """)
    void insertMobileRequest(@Param("id") long id, @Param("tenantId") long tenantId,
                             @Param("partyId") long partyId, @Param("reason") String reason,
                             @Param("appliedBy") long appliedBy);

    @Select("""
            SELECT id, party_id, reason, status, applied_by, decided_by, decided_at, decision_comment, created_at
            FROM legal_party_mobile_request
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findMobileRequest(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id FROM legal_party_mobile_request
            WHERE tenant_id = #{tenantId} AND party_id = #{partyId} AND applied_by = #{appliedBy}
              AND status = 'PENDING' AND deleted = 0
            """)
    Long findPendingMobileRequest(@Param("tenantId") long tenantId, @Param("partyId") long partyId,
                                  @Param("appliedBy") long appliedBy);

    @Select("""
            SELECT id FROM legal_party_mobile_request
            WHERE tenant_id = #{tenantId} AND party_id = #{partyId} AND applied_by = #{appliedBy}
              AND status = 'APPROVED' AND deleted = 0
            ORDER BY decided_at DESC LIMIT 1
            """)
    Long findApprovedMobileRequest(@Param("tenantId") long tenantId, @Param("partyId") long partyId,
                                   @Param("appliedBy") long appliedBy);

    @Select("""
            <script>
            SELECT r.id, r.party_id, p.name AS party_name, r.reason, r.status, r.applied_by,
                   r.decided_by, r.decided_at, r.decision_comment, r.created_at
            FROM legal_party_mobile_request r
            JOIN legal_contract_party p ON p.id = r.party_id AND p.tenant_id = r.tenant_id AND p.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.applied_by = #{appliedBy} AND r.deleted = 0
              <if test="status != null and status != ''">AND r.status = #{status}</if>
            ORDER BY r.id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listMyMobileRequests(@Param("tenantId") long tenantId,
                                                   @Param("appliedBy") long appliedBy,
                                                   @Param("status") String status,
                                                   @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1)
            FROM legal_party_mobile_request r
            JOIN legal_contract_party p ON p.id = r.party_id AND p.tenant_id = r.tenant_id AND p.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.applied_by = #{appliedBy} AND r.deleted = 0
              <if test="status != null and status != ''">AND r.status = #{status}</if>
            </script>
            """)
    long countMyMobileRequests(@Param("tenantId") long tenantId, @Param("appliedBy") long appliedBy,
                               @Param("status") String status);

    @Select("""
            <script>
            SELECT r.id, r.party_id, p.name AS party_name, p.contact_mobile_tail, r.reason, r.status,
                   r.applied_by, r.decided_by, r.decided_at, r.decision_comment, r.created_at
            FROM legal_party_mobile_request r
            JOIN legal_contract_party p ON p.id = r.party_id AND p.tenant_id = r.tenant_id AND p.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
              <if test="status != null and status != ''">AND r.status = #{status}</if>
            ORDER BY r.id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listMobileRequestsForReview(@Param("tenantId") long tenantId,
                                                          @Param("status") String status,
                                                          @Param("offset") int offset,
                                                          @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1)
            FROM legal_party_mobile_request r
            JOIN legal_contract_party p ON p.id = r.party_id AND p.tenant_id = r.tenant_id AND p.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.deleted = 0
              <if test="status != null and status != ''">AND r.status = #{status}</if>
            </script>
            """)
    long countMobileRequestsForReview(@Param("tenantId") long tenantId, @Param("status") String status);

    @Update("""
            UPDATE legal_party_mobile_request
            SET status = #{status}, decided_by = #{decidedBy}, decided_at = now(),
                decision_comment = #{comment,jdbcType=VARCHAR}, updated_by = #{decidedBy}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'PENDING' AND deleted = 0
            """)
    int decideMobileRequest(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("status") String status, @Param("decidedBy") long decidedBy,
                            @Param("comment") String comment);

    // ---------- 履约与提醒 ----------
    @Insert("""
            INSERT INTO legal_performance_node (id, tenant_id, contract_id, node_type, name, plan_date,
                amount, owner_id, status, evidence_ref, remark, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{contractId}, #{nodeType}, #{name}, #{planDate}, #{amount},
                #{ownerId}, 'PENDING', #{evidenceRef}, #{remark}, #{userId}, #{userId})
            """)
    void insertPerformanceNode(@Param("id") long id, @Param("tenantId") long tenantId,
                               @Param("contractId") long contractId, @Param("nodeType") String nodeType,
                               @Param("name") String name, @Param("planDate") LocalDate planDate,
                               @Param("amount") BigDecimal amount, @Param("ownerId") Long ownerId,
                               @Param("evidenceRef") String evidenceRef, @Param("remark") String remark,
                               @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, contract_id, node_type, name, plan_date, actual_date, amount, owner_id, status,
                   evidence_ref, remark, created_at
            FROM legal_performance_node WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="contractId != null">AND contract_id = #{contractId}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            ORDER BY plan_date NULLS LAST, id LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listPerformanceNodes(@Param("tenantId") long tenantId,
                                                   @Param("contractId") Long contractId,
                                                   @Param("status") String status,
                                                   @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_performance_node WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="contractId != null">AND contract_id = #{contractId}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </script>
            """)
    long countPerformanceNodes(@Param("tenantId") long tenantId, @Param("contractId") Long contractId,
                               @Param("status") String status);

    @Update("""
            UPDATE legal_performance_node SET status = #{status},
                actual_date = CASE WHEN #{status} = 'DONE' THEN COALESCE(actual_date, CURRENT_DATE) ELSE actual_date END,
                updated_by = #{userId}, updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updatePerformanceStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                                @Param("status") String status, @Param("userId") long userId);

    @Insert("""
            INSERT INTO legal_expiry_reminder (id, tenant_id, contract_id, offset_days, trigger_date,
                channel, receiver_ids, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{contractId}, #{offsetDays}, #{triggerDate}, #{channel},
                #{receiverIds}::jsonb, 'PENDING', #{userId}, #{userId})
            ON CONFLICT (tenant_id, contract_id, offset_days) DO NOTHING
            """)
    int insertReminder(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("contractId") long contractId, @Param("offsetDays") int offsetDays,
                       @Param("triggerDate") LocalDate triggerDate, @Param("channel") String channel,
                       @Param("receiverIds") String receiverIds, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, contract_id, offset_days, trigger_date, channel, receiver_ids::text AS receiver_ids,
                   status, sent_at, handle_action
            FROM legal_expiry_reminder WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="contractId != null">AND contract_id = #{contractId}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            ORDER BY trigger_date, id LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listReminders(@Param("tenantId") long tenantId,
                                            @Param("contractId") Long contractId, @Param("status") String status,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_expiry_reminder WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="contractId != null">AND contract_id = #{contractId}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </script>
            """)
    long countReminders(@Param("tenantId") long tenantId, @Param("contractId") Long contractId,
                        @Param("status") String status);

    @Update("UPDATE legal_expiry_reminder SET status = #{status}, updated_by = #{userId}, "
            + "updated_at = now(), version = version + 1 WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateReminderStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                             @Param("status") String status, @Param("userId") long userId);
}
