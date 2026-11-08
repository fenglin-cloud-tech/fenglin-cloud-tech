package com.fenglin.crm.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface OpportunityMapper {

    String VIEW_COLUMNS = " id AS \"opportunityId\", opportunity_no AS \"opportunityNo\", name AS \"name\", "
            + "customer_id AS \"customerId\", stage AS \"stage\", win_rate AS \"winRate\", amount AS \"amount\", "
            + "weighted_amount AS \"weightedAmount\", "
            + "to_char(expected_close_date, 'YYYY-MM-DD') AS \"expectedCloseDate\", owner_id AS \"ownerId\", "
            + "status AS \"status\", next_step AS \"nextStep\", lost_reason AS \"lostReason\", "
            + "(EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS \"createdMs\" ";

    String FILTERS = " AND (CAST(#{stage,jdbcType=VARCHAR} AS varchar) IS NULL OR stage = #{stage}) "
            + " AND (CAST(#{status,jdbcType=VARCHAR} AS varchar) IS NULL OR status = #{status}) "
            + " AND (CAST(#{customerId,jdbcType=BIGINT} AS bigint) IS NULL OR customer_id = #{customerId}) "
            + " AND (CAST(#{ownerId,jdbcType=BIGINT} AS bigint) IS NULL OR owner_id = #{ownerId}) ";

    @Select("SELECT nextval('fenglin_crm.crm_biz_no_seq')")
    long nextBizSeq();

    @Insert("INSERT INTO crm_opportunity (id, tenant_id, opportunity_no, name, customer_id, stage, win_rate, "
            + "amount, weighted_amount, currency, expected_close_date, owner_id, dept_id, source, next_step, status, "
            + "created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{opportunityNo}, #{name}, #{customerId}, #{stage}, #{winRate}, "
            + "#{amount}, #{weightedAmount}, 'CNY', #{expectedCloseDate}, #{ownerId}, #{deptId}, #{source}, "
            + "#{nextStep}, 'OPEN', #{operatorId}, #{operatorId})")
    int insert(@Param("id") long id, @Param("tenantId") long tenantId,
               @Param("opportunityNo") String opportunityNo, @Param("name") String name,
               @Param("customerId") long customerId, @Param("stage") String stage,
               @Param("winRate") BigDecimal winRate, @Param("amount") BigDecimal amount,
               @Param("weightedAmount") BigDecimal weightedAmount,
               @Param("expectedCloseDate") LocalDate expectedCloseDate, @Param("ownerId") Long ownerId,
               @Param("deptId") Long deptId, @Param("source") String source,
               @Param("nextStep") String nextStep, @Param("operatorId") long operatorId);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_opportunity WHERE id = #{id} AND tenant_id = #{tenantId} "
            + "AND deleted = 0")
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_opportunity WHERE tenant_id = #{tenantId} AND deleted = 0"
            + FILTERS + " ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("stage") String stage,
                                   @Param("status") String status, @Param("customerId") Long customerId,
                                   @Param("ownerId") Long ownerId, @Param("offset") int offset,
                                   @Param("size") int size);

    @Select("SELECT count(1) FROM crm_opportunity WHERE tenant_id = #{tenantId} AND deleted = 0" + FILTERS)
    long count(@Param("tenantId") long tenantId, @Param("stage") String stage, @Param("status") String status,
               @Param("customerId") Long customerId, @Param("ownerId") Long ownerId);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_opportunity WHERE tenant_id = #{tenantId} "
            + "AND customer_id = #{customerId} AND deleted = 0 ORDER BY created_at DESC")
    List<Map<String, Object>> listByCustomer(@Param("tenantId") long tenantId,
                                             @Param("customerId") long customerId);

    @Update("UPDATE crm_opportunity SET stage = #{stage}, win_rate = #{winRate}, amount = #{amount}, "
            + "weighted_amount = #{weightedAmount}, status = #{status}, lost_reason = #{lostReason}, "
            + "expected_close_date = COALESCE(CAST(#{expectedCloseDate,jdbcType=DATE} AS date), expected_close_date), "
            + "next_step = COALESCE(CAST(#{nextStep,jdbcType=VARCHAR} AS varchar), next_step), updated_by = #{operatorId}, "
            + "updated_at = now(), version = version + 1 "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int updateStage(@Param("tenantId") long tenantId, @Param("id") long id, @Param("stage") String stage,
                    @Param("winRate") BigDecimal winRate, @Param("amount") BigDecimal amount,
                    @Param("weightedAmount") BigDecimal weightedAmount, @Param("status") String status,
                    @Param("lostReason") String lostReason, @Param("expectedCloseDate") LocalDate expectedCloseDate,
                    @Param("nextStep") String nextStep, @Param("operatorId") long operatorId);

    @Select("SELECT stage AS \"stage\", count(1) AS \"cnt\", COALESCE(sum(amount), 0) AS \"amount\", "
            + "COALESCE(sum(weighted_amount), 0) AS \"weightedAmount\" "
            + "FROM crm_opportunity WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "GROUP BY stage ORDER BY stage")
    List<Map<String, Object>> pipeline(@Param("tenantId") long tenantId);

    // ---------- 阶段流转日志 ----------

    @Insert("INSERT INTO crm_opportunity_stage_log (id, tenant_id, opportunity_id, from_stage, to_stage, "
            + "amount_snapshot, changed_by, changed_at, opinion, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{opportunityId}, #{fromStage}, #{toStage}, #{amountSnapshot}, "
            + "#{changedBy}, #{changedAt}, #{opinion}, #{changedBy}, #{changedBy})")
    int insertStageLog(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("opportunityId") long opportunityId, @Param("fromStage") String fromStage,
                       @Param("toStage") String toStage, @Param("amountSnapshot") BigDecimal amountSnapshot,
                       @Param("changedBy") long changedBy, @Param("changedAt") Instant changedAt,
                       @Param("opinion") String opinion);

    @Select("SELECT from_stage AS \"fromStage\", to_stage AS \"toStage\", "
            + "amount_snapshot AS \"amountSnapshot\", changed_by AS \"changedBy\", "
            + "(EXTRACT(EPOCH FROM changed_at) * 1000)::bigint AS \"changedMs\", opinion AS \"opinion\" "
            + "FROM crm_opportunity_stage_log WHERE tenant_id = #{tenantId} AND opportunity_id = #{opportunityId} "
            + "AND deleted = 0 ORDER BY changed_at")
    List<Map<String, Object>> listStageLogs(@Param("tenantId") long tenantId,
                                            @Param("opportunityId") long opportunityId);
}
