package com.fenglin.legal.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * G1 用印与签署域 Mapper：印章/用印申请/用印记录/签署方/签署记录/存证。
 */
@Mapper
public interface SealSignMapper {

    @Insert("""
            INSERT INTO legal_seal (id, tenant_id, seal_no, name, seal_type, subject_name, image_ref,
                cert_serial, cert_algorithm, cert_not_before, cert_not_after, provider_code, status,
                keeper_id, remark, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{sealNo}, #{name}, #{sealType}, #{subjectName}, #{imageRef},
                #{certSerial}, #{certAlgorithm}, #{certNotBefore}::timestamptz, #{certNotAfter}::timestamptz,
                #{providerCode}, 'ACTIVE', #{keeperId}, #{remark}, #{userId}, #{userId})
            """)
    void insertSeal(@Param("id") long id, @Param("tenantId") long tenantId, @Param("sealNo") String sealNo,
                    @Param("name") String name, @Param("sealType") String sealType,
                    @Param("subjectName") String subjectName, @Param("imageRef") String imageRef,
                    @Param("certSerial") String certSerial, @Param("certAlgorithm") String certAlgorithm,
                    @Param("certNotBefore") String certNotBefore, @Param("certNotAfter") String certNotAfter,
                    @Param("providerCode") String providerCode, @Param("keeperId") Long keeperId,
                    @Param("remark") String remark, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, seal_no, name, seal_type, subject_name, image_ref, cert_serial, cert_algorithm,
                   cert_not_before, cert_not_after, provider_code, status, keeper_id, created_at
            FROM legal_seal WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="sealType != null and sealType != ''">AND seal_type = #{sealType}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listSeals(@Param("tenantId") long tenantId, @Param("sealType") String sealType,
                                        @Param("status") String status, @Param("offset") int offset,
                                        @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_seal WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="sealType != null and sealType != ''">AND seal_type = #{sealType}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </script>
            """)
    long countSeals(@Param("tenantId") long tenantId, @Param("sealType") String sealType,
                    @Param("status") String status);

    @Select("""
            SELECT id, seal_no, name, seal_type, status, cert_serial, cert_not_after, provider_code
            FROM legal_seal WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findSeal(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("UPDATE legal_seal SET status = 'REVOKED', updated_by = #{userId}, updated_at = now(), "
            + "version = version + 1 WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    int revokeSeal(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Insert("""
            INSERT INTO legal_seal_application (id, tenant_id, apply_no, contract_id, contract_version_id,
                seal_id, seal_type, copies, purpose, position_desc, applicant_id, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{applyNo}, #{contractId}, #{contractVersionId}, #{sealId},
                #{sealType}, #{copies}, #{purpose}, #{positionDesc}, #{userId}, 'PENDING', #{userId}, #{userId})
            """)
    void insertSealApplication(@Param("id") long id, @Param("tenantId") long tenantId,
                               @Param("applyNo") String applyNo, @Param("contractId") long contractId,
                               @Param("contractVersionId") long contractVersionId, @Param("sealId") long sealId,
                               @Param("sealType") String sealType, @Param("copies") int copies,
                               @Param("purpose") String purpose, @Param("positionDesc") String positionDesc,
                               @Param("userId") long userId);

    @Select("""
            SELECT id, apply_no, contract_id, contract_version_id, seal_id, seal_type, copies, purpose,
                   position_desc, applicant_id, status, approval_proc_id, created_at
            FROM legal_seal_application WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findSealApplication(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id, apply_no, contract_id, contract_version_id, seal_id, seal_type, copies, purpose,
                   position_desc, applicant_id, status, approval_proc_id, expected_at, created_at
            FROM legal_seal_application WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null and status != ''"> AND status = #{status}</if>
            <if test="applicantId != null"> AND applicant_id = #{applicantId}</if>
            ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listSealApplications(@Param("tenantId") long tenantId,
                                                   @Param("status") String status,
                                                   @Param("applicantId") Long applicantId,
                                                   @Param("offset") long offset, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT count(1) FROM legal_seal_application WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="status != null and status != ''"> AND status = #{status}</if>
            <if test="applicantId != null"> AND applicant_id = #{applicantId}</if>
            </script>
            """)
    long countSealApplications(@Param("tenantId") long tenantId, @Param("status") String status,
                               @Param("applicantId") Long applicantId);

    @Update("UPDATE legal_seal_application SET status = #{status}, updated_by = #{userId}, "
            + "updated_at = now(), version = version + 1 WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateSealApplicationStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                                    @Param("status") String status, @Param("userId") long userId);

    @Insert("""
            INSERT INTO legal_seal_record (id, tenant_id, record_no, seal_application_id, contract_id,
                seal_id, contract_version_id, version_hash, copies, image_ref, executor_id, executed_at,
                chain_id, seq_no, prev_hash, block_hash, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{recordNo}, #{applicationId}, #{contractId}, #{sealId},
                #{contractVersionId}, #{versionHash}, #{copies}, #{imageRef}, #{userId}, now(),
                #{chainId}, #{seqNo}, #{prevHash}, #{blockHash}, #{userId}, #{userId})
            """)
    void insertSealRecord(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("recordNo") String recordNo, @Param("applicationId") long applicationId,
                          @Param("contractId") long contractId, @Param("sealId") long sealId,
                          @Param("contractVersionId") long contractVersionId, @Param("versionHash") String versionHash,
                          @Param("copies") int copies, @Param("imageRef") String imageRef,
                          @Param("chainId") String chainId, @Param("seqNo") long seqNo,
                          @Param("prevHash") String prevHash, @Param("blockHash") String blockHash,
                          @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, record_no, seal_application_id, contract_id, seal_id, contract_version_id,
                   version_hash, copies, image_ref, executor_id, executed_at, chain_id, seq_no,
                   prev_hash, block_hash
            FROM legal_seal_record WHERE tenant_id = #{tenantId}
              <if test="contractId != null">AND contract_id = #{contractId}</if>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listSealRecords(@Param("tenantId") long tenantId,
                                              @Param("contractId") Long contractId,
                                              @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_seal_record WHERE tenant_id = #{tenantId}
              <if test="contractId != null">AND contract_id = #{contractId}</if>
            </script>
            """)
    long countSealRecords(@Param("tenantId") long tenantId, @Param("contractId") Long contractId);

    @Select("SELECT block_hash FROM legal_seal_record WHERE tenant_id = #{tenantId} AND chain_id = #{chainId} "
            + "ORDER BY seq_no DESC LIMIT 1")
    String lastSealHash(@Param("tenantId") long tenantId, @Param("chainId") String chainId);

    @Insert("""
            INSERT INTO legal_signatory (id, tenant_id, contract_id, party_type, party_id, signer_name,
                signer_id_no_enc, signer_id_hash, signer_mobile_enc, signer_mobile_hash, signer_mobile_tail,
                seal_mode, sign_order, sign_mode, status, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{contractId}, #{partyType}, #{partyId}, #{signerName},
                #{idNoEnc}, #{idNoHash}, #{mobileEnc}, #{mobileHash}, #{mobileTail}, #{sealMode},
                #{signOrder}, #{signMode}, 'PENDING', #{userId}, #{userId})
            """)
    void insertSignatory(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("contractId") long contractId, @Param("partyType") String partyType,
                         @Param("partyId") Long partyId, @Param("signerName") String signerName,
                         @Param("idNoEnc") String idNoEnc, @Param("idNoHash") String idNoHash,
                         @Param("mobileEnc") String mobileEnc, @Param("mobileHash") String mobileHash,
                         @Param("mobileTail") String mobileTail, @Param("sealMode") String sealMode,
                         @Param("signOrder") int signOrder, @Param("signMode") String signMode,
                         @Param("userId") long userId);

    @Select("""
            SELECT id, contract_id, party_type, party_id, signer_name, signer_mobile_tail, seal_mode,
                   sign_order, sign_mode, status, auth_evidence_ref, created_at
            FROM legal_signatory WHERE tenant_id = #{tenantId} AND contract_id = #{contractId}
            ORDER BY sign_order, id
            """)
    List<Map<String, Object>> listSignatories(@Param("tenantId") long tenantId,
                                              @Param("contractId") long contractId);

    @Select("""
            SELECT id, contract_id, party_type, signer_name, signer_mobile_tail, seal_mode, sign_order,
                   sign_mode, status, signer_mobile_enc, signer_id_no_enc
            FROM legal_signatory WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    Map<String, Object> findSignatory(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("UPDATE legal_signatory SET status = #{status}, updated_by = #{userId}, updated_at = now(), "
            + "version = version + 1 WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateSignatoryStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                              @Param("status") String status, @Param("userId") long userId);

    @Insert("""
            INSERT INTO legal_sign_record (id, tenant_id, sign_task_no, contract_id, contract_version_id,
                signatory_id, provider_code, provider_task_id, real_name_verified, will_confirmed, auth_mode,
                tsa_token, doc_hash, sign_data, sign_ip, sign_device, status, signed_at, chain_id,
                prev_hash, block_hash, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{taskNo}, #{contractId}, #{contractVersionId}, #{signatoryId},
                #{providerCode}, #{providerTaskId}, #{realNameVerified}, #{willConfirmed}, #{authMode},
                #{tsaToken}, #{docHash}, #{signData}, #{signIp}::inet, #{signDevice}, #{status},
                #{signedAt}::timestamptz, #{chainId}, #{prevHash}, #{blockHash}, #{userId}, #{userId})
            """)
    void insertSignRecord(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("taskNo") String taskNo, @Param("contractId") long contractId,
                          @Param("contractVersionId") long contractVersionId, @Param("signatoryId") long signatoryId,
                          @Param("providerCode") String providerCode, @Param("providerTaskId") String providerTaskId,
                          @Param("realNameVerified") boolean realNameVerified, @Param("willConfirmed") boolean willConfirmed,
                          @Param("authMode") String authMode, @Param("tsaToken") String tsaToken,
                          @Param("docHash") String docHash, @Param("signData") String signData,
                          @Param("signIp") String signIp, @Param("signDevice") String signDevice,
                          @Param("status") String status, @Param("signedAt") String signedAt,
                          @Param("chainId") String chainId, @Param("prevHash") String prevHash,
                          @Param("blockHash") String blockHash, @Param("userId") long userId);

    @Select("""
            SELECT id, sign_task_no, contract_id, contract_version_id, signatory_id, provider_code,
                   provider_task_id, real_name_verified, will_confirmed, auth_mode, tsa_token, doc_hash,
                   status, signed_at, chain_id, prev_hash, block_hash
            FROM legal_sign_record WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    Map<String, Object> findSignRecord(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, sign_task_no, contract_id, status, block_hash
            FROM legal_sign_record WHERE tenant_id = #{tenantId} AND provider_code = #{providerCode}
              AND provider_task_id = #{providerTaskId}
            """)
    Map<String, Object> findSignRecordByProviderTask(@Param("tenantId") long tenantId,
                                                     @Param("providerCode") String providerCode,
                                                     @Param("providerTaskId") String providerTaskId);

    @Select("""
            <script>
            SELECT id, sign_task_no, contract_id, contract_version_id, signatory_id, provider_code,
                   real_name_verified, will_confirmed, auth_mode, doc_hash, status, signed_at
            FROM legal_sign_record WHERE tenant_id = #{tenantId}
              <if test="contractId != null">AND contract_id = #{contractId}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listSignRecords(@Param("tenantId") long tenantId,
                                              @Param("contractId") Long contractId, @Param("status") String status,
                                              @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_sign_record WHERE tenant_id = #{tenantId}
              <if test="contractId != null">AND contract_id = #{contractId}</if>
              <if test="status != null and status != ''">AND status = #{status}</if>
            </script>
            """)
    long countSignRecords(@Param("tenantId") long tenantId, @Param("contractId") Long contractId,
                          @Param("status") String status);

    @Update("""
            UPDATE legal_sign_record SET status = #{status}, real_name_verified = #{realNameVerified},
                will_confirmed = #{willConfirmed}, auth_mode = #{authMode}, tsa_token = #{tsaToken},
                sign_data = #{signData}, sign_ip = #{signIp}::inet, sign_device = #{signDevice},
                signed_at = CASE WHEN #{status} = 'SIGNED' THEN now() ELSE signed_at END,
                updated_by = #{userId}, updated_at = now(), version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    int updateSignRecordStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                               @Param("status") String status, @Param("realNameVerified") boolean realNameVerified,
                               @Param("willConfirmed") boolean willConfirmed, @Param("authMode") String authMode,
                               @Param("tsaToken") String tsaToken, @Param("signData") String signData,
                               @Param("signIp") String signIp, @Param("signDevice") String signDevice,
                               @Param("userId") long userId);

    @Select("""
            <script>
            SELECT r.id, r.sign_task_no, r.contract_id, c.contract_no, c.name AS contract_name,
                   r.contract_version_id, r.signatory_id, s.signer_name, r.provider_code, r.status,
                   r.real_name_verified, r.will_confirmed, r.signed_at
            FROM legal_sign_record r
            JOIN legal_contract c ON c.id = r.contract_id AND c.tenant_id = r.tenant_id
            LEFT JOIN legal_signatory s ON s.id = r.signatory_id AND s.tenant_id = r.tenant_id
            WHERE r.tenant_id = #{tenantId}
              <if test="signerUserId != null">
                AND s.party_type = 'INTERNAL' AND s.party_id = #{signerUserId}
              </if>
            ORDER BY r.id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listSignTasks(@Param("tenantId") long tenantId,
                                            @Param("signerUserId") Long signerUserId,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1)
            FROM legal_sign_record r
            LEFT JOIN legal_signatory s ON s.id = r.signatory_id AND s.tenant_id = r.tenant_id
            WHERE r.tenant_id = #{tenantId}
              <if test="signerUserId != null">
                AND s.party_type = 'INTERNAL' AND s.party_id = #{signerUserId}
              </if>
            </script>
            """)
    long countSignTasks(@Param("tenantId") long tenantId, @Param("signerUserId") Long signerUserId);

    @Select("""
            SELECT r.id, r.contract_id, r.status, r.signatory_id, s.party_type, s.party_id
            FROM legal_sign_record r
            LEFT JOIN legal_signatory s ON s.id = r.signatory_id AND s.tenant_id = r.tenant_id
            WHERE r.tenant_id = #{tenantId} AND r.id = #{id}
            """)
    Map<String, Object> findSignRecordScope(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT block_hash FROM legal_sign_record WHERE tenant_id = #{tenantId} AND chain_id = #{chainId} "
            + "ORDER BY id DESC LIMIT 1")
    String lastSignHash(@Param("tenantId") long tenantId, @Param("chainId") String chainId);

    @Insert("""
            INSERT INTO legal_evidence (id, tenant_id, evidence_no, contract_id, sign_record_id,
                evidence_type, content_hash, hash_algorithm, worm_ref, provider_evidence_id, cert_ref,
                chain_id, prev_hash, block_hash, created_by, updated_by)
            VALUES (#{id}, #{tenantId}, #{evidenceNo}, #{contractId}, #{signRecordId}, #{evidenceType},
                #{contentHash}, #{algorithm}, #{wormRef}, #{providerEvidenceId}, #{certRef}, #{chainId},
                #{prevHash}, #{blockHash}, #{userId}, #{userId})
            """)
    void insertEvidence(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("evidenceNo") String evidenceNo, @Param("contractId") long contractId,
                        @Param("signRecordId") Long signRecordId, @Param("evidenceType") String evidenceType,
                        @Param("contentHash") String contentHash, @Param("algorithm") String algorithm,
                        @Param("wormRef") String wormRef, @Param("providerEvidenceId") String providerEvidenceId,
                        @Param("certRef") String certRef, @Param("chainId") String chainId,
                        @Param("prevHash") String prevHash, @Param("blockHash") String blockHash,
                        @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, evidence_no, contract_id, sign_record_id, evidence_type, content_hash,
                   hash_algorithm, worm_ref, provider_evidence_id, cert_ref, chain_id, prev_hash,
                   block_hash, verified, verified_at, created_at
            FROM legal_evidence WHERE tenant_id = #{tenantId}
              <if test="contractId != null">AND contract_id = #{contractId}</if>
              <if test="evidenceType != null and evidenceType != ''">AND evidence_type = #{evidenceType}</if>
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listEvidences(@Param("tenantId") long tenantId,
                                            @Param("contractId") Long contractId,
                                            @Param("evidenceType") String evidenceType,
                                            @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT count(1) FROM legal_evidence WHERE tenant_id = #{tenantId}
              <if test="contractId != null">AND contract_id = #{contractId}</if>
              <if test="evidenceType != null and evidenceType != ''">AND evidence_type = #{evidenceType}</if>
            </script>
            """)
    long countEvidences(@Param("tenantId") long tenantId, @Param("contractId") Long contractId,
                        @Param("evidenceType") String evidenceType);

    @Select("""
            SELECT id, evidence_no, contract_id, sign_record_id, evidence_type, content_hash,
                   hash_algorithm, block_hash, prev_hash, chain_id, verified
            FROM legal_evidence WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    Map<String, Object> findEvidence(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("UPDATE legal_evidence SET verified = #{verified}, "
            + "verified_at = CASE WHEN #{verified} THEN now() ELSE verified_at END, "
            + "updated_by = #{userId}, updated_at = now(), version = version + 1 "
            + "WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateEvidenceVerified(@Param("tenantId") long tenantId, @Param("id") long id,
                               @Param("verified") boolean verified, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, evidence_no, evidence_type, content_hash, chain_id, prev_hash, block_hash
            FROM legal_evidence WHERE tenant_id = #{tenantId}
              <if test="contractId != null">AND contract_id = #{contractId}</if>
            ORDER BY chain_id, id
            </script>
            """)
    List<Map<String, Object>> listEvidenceChain(@Param("tenantId") long tenantId,
                                                @Param("contractId") Long contractId);
}
