package com.fenglin.legal.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 云法公共 Mapper：租户盐、幂等记录、业务编号序列。
 */
@Mapper
public interface CommonsMapper {

    @Select("SELECT salt FROM legal_tenant_key WHERE tenant_id = #{tenantId} AND deleted = 0")
    String findTenantSalt(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO legal_tenant_key (id, tenant_id, salt, created_by)
            VALUES (#{id}, #{tenantId}, #{salt}, #{userId})
            """)
    void insertTenantKey(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("salt") String salt, @Param("userId") long userId);

    @Select("SELECT biz_id FROM legal_idempotency_record "
            + "WHERE tenant_id = #{tenantId} AND idem_key = #{key} AND api_path = #{apiPath}")
    Long findIdempotentBizId(@Param("tenantId") long tenantId, @Param("key") String key,
                             @Param("apiPath") String apiPath);

    @Insert("""
            INSERT INTO legal_idempotency_record (id, tenant_id, idem_key, api_path, biz_id, created_by)
            VALUES (#{id}, #{tenantId}, #{key}, #{apiPath}, #{bizId}, #{userId})
            ON CONFLICT (tenant_id, idem_key, api_path) DO NOTHING
            """)
    int insertIdempotent(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("key") String key, @Param("apiPath") String apiPath,
                         @Param("bizId") long bizId, @Param("userId") long userId);

    @Select("SELECT nextval('legal_template_no_seq')")
    long nextTemplateNo();

    @Select("SELECT nextval('legal_contract_no_seq')")
    long nextContractNo();

    @Select("SELECT nextval('legal_seal_apply_no_seq')")
    long nextSealApplyNo();

    @Select("SELECT nextval('legal_seal_record_no_seq')")
    long nextSealRecordNo();

    @Select("SELECT nextval('legal_sign_task_no_seq')")
    long nextSignTaskNo();

    @Select("SELECT nextval('legal_evidence_no_seq')")
    long nextEvidenceNo();

    @Select("SELECT nextval('legal_dlp_event_no_seq')")
    long nextDlpEventNo();

    @Select("SELECT nextval('legal_watermark_no_seq')")
    long nextWatermarkNo();

    @Select("SELECT now()")
    java.sql.Timestamp now();
}
