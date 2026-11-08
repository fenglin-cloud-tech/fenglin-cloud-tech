package com.fenglin.finance.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 公共 Mapper：租户盐、幂等记录、业务编号序列。
 */
@Mapper
public interface CommonsMapper {

    @Select("SELECT salt FROM fin_tenant_key WHERE tenant_id = #{tenantId} AND deleted = 0")
    String findTenantSalt(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO fin_tenant_key (id, tenant_id, salt, created_by)
            VALUES (#{id}, #{tenantId}, #{salt}, #{userId})
            """)
    void insertTenantKey(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("salt") String salt, @Param("userId") long userId);

    @Select("SELECT biz_id FROM fin_idempotency_record "
            + "WHERE tenant_id = #{tenantId} AND idempotency_key = #{key}")
    Long findIdempotentBizId(@Param("tenantId") long tenantId, @Param("key") String key);

    @Insert("""
            INSERT INTO fin_idempotency_record (id, tenant_id, idempotency_key, api_path, biz_id, created_by)
            VALUES (#{id}, #{tenantId}, #{key}, #{apiPath}, #{bizId}, #{userId})
            ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
            """)
    int insertIdempotent(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("key") String key, @Param("apiPath") String apiPath,
                         @Param("bizId") long bizId, @Param("userId") long userId);

    @Select("SELECT nextval('fin_project_no_seq')")
    long nextProjectNo();

    @Select("SELECT nextval('fin_voucher_no_seq')")
    long nextVoucherNo();

    @Select("SELECT nextval('fin_receipt_no_seq')")
    long nextReceiptNo();

    @Select("SELECT nextval('fin_payment_no_seq')")
    long nextPaymentNo();

    @Select("SELECT nextval('fin_claim_no_seq')")
    long nextClaimNo();

    @Select("SELECT nextval('fin_ar_no_seq')")
    long nextArNo();

    @Select("SELECT nextval('fin_ap_no_seq')")
    long nextApNo();

    @Select("SELECT nextval('fin_write_off_no_seq')")
    long nextWriteOffNo();

    @Select("SELECT nextval('fin_settlement_no_seq')")
    long nextSettlementNo();

    @Select("SELECT nextval('fin_cost_no_seq')")
    long nextCostNo();

    @Select("SELECT nextval('fin_timesheet_no_seq')")
    long nextTimesheetNo();

    @Select("SELECT nextval('fin_asset_no_seq')")
    long nextAssetNo();

    @Select("SELECT nextval('fin_dep_batch_seq')")
    long nextDepBatchNo();

    @Select("SELECT nextval('fin_stocktaking_no_seq')")
    long nextStocktakingNo();

    @Select("SELECT nextval('fin_recon_no_seq')")
    long nextReconNo();

    @Select("SELECT nextval('fin_activity_no_seq')")
    long nextActivityNo();

    @Select("SELECT nextval('fin_emission_no_seq')")
    long nextEmissionNo();

    @Select("SELECT nextval('fin_target_no_seq')")
    long nextTargetNo();

    @Select("SELECT nextval('fin_esg_report_no_seq')")
    long nextEsgReportNo();

    @Select("SELECT nextval('fin_chain_seq')")
    long nextChainSeq();
}
