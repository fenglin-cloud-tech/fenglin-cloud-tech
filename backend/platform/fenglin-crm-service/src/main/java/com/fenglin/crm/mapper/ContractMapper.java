package com.fenglin.crm.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
            + "(EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS \"createdMs\" ";

    @Select("SELECT nextval('fenglin_crm.crm_contract_no_seq')")
    long nextContractSeq();

    @Insert("INSERT INTO crm_service_contract (id, tenant_id, contract_no, customer_id, sla_level, service_package, "
            + "amount, start_date, end_date, status, owner_id, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{contractNo}, #{customerId}, #{slaLevel}, #{servicePackage}, #{amount}, "
            + "#{startDate}, #{endDate}, 'ACTIVE', #{ownerId}, #{operatorId}, #{operatorId})")
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("contractNo") String contractNo,
               @Param("customerId") long customerId, @Param("slaLevel") String slaLevel,
               @Param("servicePackage") String servicePackage, @Param("amount") BigDecimal amount,
               @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
               @Param("ownerId") Long ownerId, @Param("operatorId") long operatorId);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_service_contract WHERE id = #{id} AND tenant_id = #{tenantId} "
            + "AND deleted = 0")
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_service_contract WHERE tenant_id = #{tenantId} "
            + "AND customer_id = #{customerId} AND deleted = 0 ORDER BY start_date DESC")
    List<Map<String, Object>> listByCustomer(@Param("tenantId") long tenantId, @Param("customerId") long customerId);

    @Insert("INSERT INTO crm_payment_plan (id, tenant_id, contract_id, period_no, due_date, amount, paid_amount, "
            + "status, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{contractId}, #{periodNo}, #{dueDate}, #{amount}, 0, 'PENDING', "
            + "#{operatorId}, #{operatorId})")
    int insertPaymentPlan(@Param("id") long id, @Param("tenantId") long tenantId,
                          @Param("contractId") long contractId, @Param("periodNo") int periodNo,
                          @Param("dueDate") LocalDate dueDate, @Param("amount") BigDecimal amount,
                          @Param("operatorId") long operatorId);

    @Select("SELECT period_no AS \"periodNo\", to_char(due_date, 'YYYY-MM-DD') AS \"dueDate\", "
            + "amount AS \"amount\", paid_amount AS \"paidAmount\", status AS \"status\" "
            + "FROM crm_payment_plan WHERE tenant_id = #{tenantId} AND contract_id = #{contractId} AND deleted = 0 "
            + "ORDER BY period_no")
    List<Map<String, Object>> listPaymentPlans(@Param("tenantId") long tenantId,
                                               @Param("contractId") long contractId);
}
