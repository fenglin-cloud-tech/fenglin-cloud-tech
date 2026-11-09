package com.fenglin.crm.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContractMapper {

    String VIEW_COLUMNS = " id AS \"contractId\", contract_no AS \"contractNo\", customer_id AS \"customerId\", "
            + "sla_level AS \"slaLevel\", service_package AS \"servicePackage\", amount AS \"amount\", "
            + "to_char(start_date, 'YYYY-MM-DD') AS \"startDate\", "
            + "to_char(end_date, 'YYYY-MM-DD') AS \"endDate\", status AS \"status\", "
            + "parent_contract_id AS \"parentContractId\", quotation_id AS \"quotationId\", "
            + "(EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS \"createdMs\" ";

    String PLAN_COLUMNS = " p.id AS \"planId\", p.period_no AS \"periodNo\", "
            + "to_char(p.due_date, 'YYYY-MM-DD') AS \"dueDate\", p.amount AS \"amount\", "
            + "p.paid_amount AS \"paidAmount\", (p.amount - p.paid_amount) AS \"remainingAmount\", "
            + "CASE WHEN p.status <> 'PAID' AND p.due_date < CURRENT_DATE THEN 'OVERDUE' ELSE p.status END AS \"status\", "
            + "CASE WHEN p.status <> 'PAID' AND p.due_date < CURRENT_DATE "
            + "THEN (CURRENT_DATE - p.due_date) ELSE 0 END AS \"overdueDays\", "
            + "p.collection_flag AS \"collectionFlag\", p.collection_note AS \"collectionNote\" ";

    @Select("SELECT nextval('fenglin_crm.crm_contract_no_seq')")
    long nextContractSeq();

    @Select("SELECT nextval('fenglin_crm.crm_change_no_seq')")
    long nextChangeSeq();

    @Insert("INSERT INTO crm_service_contract (id, tenant_id, contract_no, customer_id, sla_level, service_package, "
            + "amount, start_date, end_date, status, owner_id, parent_contract_id, quotation_id, "
            + "created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{contractNo}, #{customerId}, #{slaLevel}, #{servicePackage}, #{amount}, "
            + "#{startDate}, #{endDate}, 'ACTIVE', #{ownerId}, "
            + "CAST(#{parentContractId,jdbcType=BIGINT} AS bigint), CAST(#{quotationId,jdbcType=BIGINT} AS bigint), "
            + "#{operatorId}, #{operatorId})")
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("contractNo") String contractNo,
               @Param("customerId") long customerId, @Param("slaLevel") String slaLevel,
               @Param("servicePackage") String servicePackage, @Param("amount") BigDecimal amount,
               @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
               @Param("ownerId") Long ownerId, @Param("parentContractId") Long parentContractId,
               @Param("quotationId") Long quotationId, @Param("operatorId") long operatorId);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_service_contract WHERE id = #{id} AND tenant_id = #{tenantId} "
            + "AND deleted = 0")
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_service_contract WHERE tenant_id = #{tenantId} "
            + "AND customer_id = #{customerId} AND deleted = 0 ORDER BY start_date DESC")
    List<Map<String, Object>> listByCustomer(@Param("tenantId") long tenantId, @Param("customerId") long customerId);

    // ---------- 列表（扩展） ----------

    String LIST_COLUMNS = " c.id AS \"contractId\", c.contract_no AS \"contractNo\", c.customer_id AS \"customerId\", "
            + "cu.name AS \"customerName\", c.sla_level AS \"slaLevel\", c.service_package AS \"servicePackage\", "
            + "c.amount AS \"amount\", to_char(c.start_date, 'YYYY-MM-DD') AS \"startDate\", "
            + "to_char(c.end_date, 'YYYY-MM-DD') AS \"endDate\", c.status AS \"status\", "
            + "c.owner_id AS \"ownerId\", (EXTRACT(EPOCH FROM c.created_at) * 1000)::bigint AS \"createdMs\" ";

    String LIST_FROM = " FROM crm_service_contract c "
            + "JOIN crm_customer cu ON cu.id = c.customer_id AND cu.tenant_id = c.tenant_id AND cu.deleted = 0 ";

    String LIST_FILTERS = " AND (CAST(#{status,jdbcType=VARCHAR} AS varchar) IS NULL OR c.status = #{status}) "
            + " AND (CAST(#{ownerId,jdbcType=BIGINT} AS bigint) IS NULL OR c.owner_id = #{ownerId}) "
            + " AND (CAST(#{customerId,jdbcType=BIGINT} AS bigint) IS NULL OR c.customer_id = #{customerId}) "
            + " AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL "
            + "      OR c.contract_no ILIKE '%' || CAST(#{keyword,jdbcType=VARCHAR} AS varchar) || '%' "
            + "      OR cu.name ILIKE '%' || CAST(#{keyword,jdbcType=VARCHAR} AS varchar) || '%') ";

    @Select("SELECT " + LIST_COLUMNS + LIST_FROM
            + "WHERE c.tenant_id = #{tenantId} AND c.deleted = 0" + LIST_FILTERS
            + "ORDER BY c.created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("status") String status,
                                   @Param("ownerId") Long ownerId, @Param("customerId") Long customerId,
                                   @Param("keyword") String keyword, @Param("offset") int offset,
                                   @Param("size") int size);

    @Select("SELECT count(1)" + LIST_FROM
            + "WHERE c.tenant_id = #{tenantId} AND c.deleted = 0" + LIST_FILTERS)
    long count(@Param("tenantId") long tenantId, @Param("status") String status,
               @Param("ownerId") Long ownerId, @Param("customerId") Long customerId,
               @Param("keyword") String keyword);

    // ---------- 回款提醒（扩展） ----------

    @Select("SELECT " + PLAN_COLUMNS + ", p.contract_id AS \"contractId\", c.contract_no AS \"contractNo\", "
            + "c.customer_id AS \"customerId\", cu.name AS \"customerName\" "
            + "FROM crm_payment_plan p "
            + "JOIN crm_service_contract c ON c.id = p.contract_id AND c.tenant_id = p.tenant_id AND c.deleted = 0 "
            + "JOIN crm_customer cu ON cu.id = c.customer_id AND cu.tenant_id = c.tenant_id AND cu.deleted = 0 "
            + "WHERE p.tenant_id = #{tenantId} AND p.deleted = 0 AND p.status <> 'PAID' "
            + "AND (CAST(#{ownerId,jdbcType=BIGINT} AS bigint) IS NULL OR c.owner_id = #{ownerId}) "
            + "AND (CAST(#{upcomingDays,jdbcType=INTEGER} AS integer) IS NULL "
            + "     OR p.due_date <= CURRENT_DATE + CAST(#{upcomingDays,jdbcType=INTEGER} AS integer)) "
            + "ORDER BY p.due_date, p.period_no LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listReminders(@Param("tenantId") long tenantId, @Param("ownerId") Long ownerId,
                                            @Param("upcomingDays") Integer upcomingDays,
                                            @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT count(1) FROM crm_payment_plan p "
            + "JOIN crm_service_contract c ON c.id = p.contract_id AND c.tenant_id = p.tenant_id AND c.deleted = 0 "
            + "WHERE p.tenant_id = #{tenantId} AND p.deleted = 0 AND p.status <> 'PAID' "
            + "AND (CAST(#{ownerId,jdbcType=BIGINT} AS bigint) IS NULL OR c.owner_id = #{ownerId}) "
            + "AND (CAST(#{upcomingDays,jdbcType=INTEGER} AS integer) IS NULL "
            + "     OR p.due_date <= CURRENT_DATE + CAST(#{upcomingDays,jdbcType=INTEGER} AS integer))")
    long countReminders(@Param("tenantId") long tenantId, @Param("ownerId") Long ownerId,
                        @Param("upcomingDays") Integer upcomingDays);

    @Update("UPDATE crm_service_contract SET amount = COALESCE(CAST(#{amount,jdbcType=NUMERIC} AS numeric), amount), "
            + "sla_level = COALESCE(CAST(#{slaLevel,jdbcType=VARCHAR} AS varchar), sla_level), "
            + "service_package = COALESCE(CAST(#{servicePackage,jdbcType=VARCHAR} AS varchar), service_package), "
            + "end_date = COALESCE(CAST(#{endDate,jdbcType=DATE} AS date), end_date), "
            + "updated_by = #{operatorId}, updated_at = now(), version = version + 1 "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int updateContract(@Param("tenantId") long tenantId, @Param("id") long id,
                       @Param("amount") BigDecimal amount, @Param("slaLevel") String slaLevel,
                       @Param("servicePackage") String servicePackage, @Param("endDate") LocalDate endDate,
                       @Param("operatorId") long operatorId);

    // ---------- 回款计划 ----------

    @Insert("INSERT INTO crm_payment_plan (id, tenant_id, contract_id, period_no, due_date, amount, paid_amount, "
            + "status, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{contractId}, #{periodNo}, #{dueDate}, #{amount}, 0, 'PENDING', "
            + "#{operatorId}, #{operatorId})")
    int insertPaymentPlan(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("contractId") long contractId, @Param("periodNo") int periodNo,
                          @Param("dueDate") LocalDate dueDate, @Param("amount") BigDecimal amount,
                          @Param("operatorId") long operatorId);

    @Select("SELECT " + PLAN_COLUMNS + " FROM crm_payment_plan p WHERE p.tenant_id = #{tenantId} "
            + "AND p.contract_id = #{contractId} AND p.deleted = 0 ORDER BY p.period_no")
    List<Map<String, Object>> listPaymentPlans(@Param("tenantId") long tenantId,
                                               @Param("contractId") long contractId);

    @Select("SELECT " + PLAN_COLUMNS + ", p.contract_id AS \"contractId\" FROM crm_payment_plan p "
            + "WHERE p.id = #{planId} AND p.tenant_id = #{tenantId} AND p.deleted = 0")
    Map<String, Object> findPaymentPlan(@Param("tenantId") long tenantId, @Param("planId") long planId);

    @Update("UPDATE crm_payment_plan SET paid_amount = #{paidAmount}, status = #{status}, "
            + "updated_by = #{operatorId}, updated_at = now(), version = version + 1 "
            + "WHERE id = #{planId} AND tenant_id = #{tenantId} AND deleted = 0")
    int updatePlanPaid(@Param("tenantId") long tenantId, @Param("planId") long planId,
                       @Param("paidAmount") BigDecimal paidAmount, @Param("status") String status,
                       @Param("operatorId") long operatorId);

    @Update("UPDATE crm_payment_plan SET collection_flag = #{flag}, "
            + "collection_note = CAST(#{note,jdbcType=VARCHAR} AS varchar), collection_updated_at = now(), "
            + "updated_by = #{operatorId}, updated_at = now(), version = version + 1 "
            + "WHERE id = #{planId} AND tenant_id = #{tenantId} AND deleted = 0")
    int updateCollection(@Param("tenantId") long tenantId, @Param("planId") long planId,
                         @Param("flag") String flag, @Param("note") String note,
                         @Param("operatorId") long operatorId);

    @Select("SELECT " + PLAN_COLUMNS + ", p.contract_id AS \"contractId\", c.contract_no AS \"contractNo\", "
            + "c.customer_id AS \"customerId\", cu.name AS \"customerName\" "
            + "FROM crm_payment_plan p "
            + "JOIN crm_service_contract c ON c.id = p.contract_id AND c.tenant_id = p.tenant_id AND c.deleted = 0 "
            + "JOIN crm_customer cu ON cu.id = c.customer_id AND cu.tenant_id = c.tenant_id AND cu.deleted = 0 "
            + "WHERE p.tenant_id = #{tenantId} AND p.deleted = 0 AND p.status <> 'PAID' "
            + "AND p.due_date < CURRENT_DATE "
            + "AND (CAST(#{customerId,jdbcType=BIGINT} AS bigint) IS NULL OR c.customer_id = #{customerId}) "
            + "ORDER BY p.due_date, p.period_no LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listOverduePlans(@Param("tenantId") long tenantId,
                                               @Param("customerId") Long customerId,
                                               @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT count(1) FROM crm_payment_plan p "
            + "JOIN crm_service_contract c ON c.id = p.contract_id AND c.tenant_id = p.tenant_id AND c.deleted = 0 "
            + "WHERE p.tenant_id = #{tenantId} AND p.deleted = 0 AND p.status <> 'PAID' "
            + "AND p.due_date < CURRENT_DATE "
            + "AND (CAST(#{customerId,jdbcType=BIGINT} AS bigint) IS NULL OR c.customer_id = #{customerId})")
    long countOverduePlans(@Param("tenantId") long tenantId, @Param("customerId") Long customerId);

    @Update("UPDATE crm_payment_plan SET deleted = 1, updated_by = #{operatorId}, updated_at = now(), "
            + "version = version + 1 WHERE tenant_id = #{tenantId} AND contract_id = #{contractId} "
            + "AND deleted = 0 AND paid_amount = 0 AND status <> 'PAID'")
    int removeUnpaidPlans(@Param("tenantId") long tenantId, @Param("contractId") long contractId,
                          @Param("operatorId") long operatorId);

    // ---------- 回款记录 ----------

    @Insert("INSERT INTO crm_payment_record (id, tenant_id, contract_id, plan_id, amount, received_date, method, "
            + "voucher_no, remark, status, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{contractId}, #{planId}, #{amount}, #{receivedDate}, #{method}, "
            + "#{voucherNo}, #{remark}, 'RECORDED', #{operatorId}, #{operatorId})")
    int insertPaymentRecord(@Param("id") long id, @Param("tenantId") long tenantId,
                            @Param("contractId") long contractId, @Param("planId") long planId,
                            @Param("amount") BigDecimal amount, @Param("receivedDate") LocalDate receivedDate,
                            @Param("method") String method, @Param("voucherNo") String voucherNo,
                            @Param("remark") String remark, @Param("operatorId") long operatorId);

    @Select("SELECT id AS \"paymentId\", contract_id AS \"contractId\", plan_id AS \"planId\", amount AS \"amount\", "
            + "to_char(received_date, 'YYYY-MM-DD') AS \"receivedDate\", method AS \"method\", "
            + "voucher_no AS \"voucherNo\", remark AS \"remark\", status AS \"status\", "
            + "(EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS \"createdMs\" "
            + "FROM crm_payment_record WHERE tenant_id = #{tenantId} AND contract_id = #{contractId} "
            + "AND deleted = 0 ORDER BY received_date DESC, created_at DESC")
    List<Map<String, Object>> listPaymentRecords(@Param("tenantId") long tenantId,
                                                 @Param("contractId") long contractId);

    // ---------- 合同变更 ----------

    @Insert("INSERT INTO crm_contract_change (id, tenant_id, contract_id, change_no, change_type, before_amount, "
            + "after_amount, before_start_date, before_end_date, after_start_date, after_end_date, reason, "
            + "new_contract_id, status, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{contractId}, #{changeNo}, #{changeType}, #{beforeAmount}, "
            + "#{afterAmount}, #{beforeStartDate}, #{beforeEndDate}, #{afterStartDate}, #{afterEndDate}, #{reason}, "
            + "CAST(#{newContractId,jdbcType=BIGINT} AS bigint), 'APPLIED', #{operatorId}, #{operatorId})")
    int insertChange(@Param("id") long id, @Param("tenantId") long tenantId, @Param("contractId") long contractId,
                     @Param("changeNo") String changeNo, @Param("changeType") String changeType,
                     @Param("beforeAmount") BigDecimal beforeAmount, @Param("afterAmount") BigDecimal afterAmount,
                     @Param("beforeStartDate") LocalDate beforeStartDate,
                     @Param("beforeEndDate") LocalDate beforeEndDate,
                     @Param("afterStartDate") LocalDate afterStartDate,
                     @Param("afterEndDate") LocalDate afterEndDate, @Param("reason") String reason,
                     @Param("newContractId") Long newContractId, @Param("operatorId") long operatorId);

    @Select("SELECT id AS \"changeId\", change_no AS \"changeNo\", contract_id AS \"contractId\", "
            + "change_type AS \"changeType\", before_amount AS \"beforeAmount\", after_amount AS \"afterAmount\", "
            + "to_char(before_start_date, 'YYYY-MM-DD') AS \"beforeStartDate\", "
            + "to_char(before_end_date, 'YYYY-MM-DD') AS \"beforeEndDate\", "
            + "to_char(after_start_date, 'YYYY-MM-DD') AS \"afterStartDate\", "
            + "to_char(after_end_date, 'YYYY-MM-DD') AS \"afterEndDate\", reason AS \"reason\", "
            + "new_contract_id AS \"newContractId\", status AS \"status\", "
            + "(EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS \"createdMs\" "
            + "FROM crm_contract_change WHERE tenant_id = #{tenantId} AND contract_id = #{contractId} "
            + "AND deleted = 0 ORDER BY created_at DESC")
    List<Map<String, Object>> listChanges(@Param("tenantId") long tenantId, @Param("contractId") long contractId);
}
