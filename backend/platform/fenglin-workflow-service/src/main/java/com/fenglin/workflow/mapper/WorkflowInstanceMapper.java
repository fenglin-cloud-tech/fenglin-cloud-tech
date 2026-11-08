package com.fenglin.workflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.Map;

@Mapper
public interface WorkflowInstanceMapper {

    @Insert("""
            INSERT INTO hub_wf_instance (id, tenant_id, instance_no, process_key, def_version, flowable_instance_id,
                                         business_key, business_type, title, initiator_id, status, started_at,
                                         created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{instanceNo}, #{processKey}, #{defVersion}, #{flowableInstanceId},
                    #{businessKey,jdbcType=VARCHAR}, #{businessType,jdbcType=VARCHAR}, #{title,jdbcType=VARCHAR},
                    #{initiatorId}, 'RUNNING', now(), #{initiatorId}, now(), #{initiatorId}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("instanceNo") String instanceNo,
               @Param("processKey") String processKey,
               @Param("defVersion") int defVersion,
               @Param("flowableInstanceId") String flowableInstanceId,
               @Param("businessKey") String businessKey,
               @Param("businessType") String businessType,
               @Param("title") String title,
               @Param("initiatorId") Long initiatorId);

    @Select("""
            SELECT id, instance_no, process_key, def_version, flowable_instance_id, business_key, business_type,
                   title, initiator_id, status, result, started_at, ended_at
              FROM hub_wf_instance
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, instance_no, process_key, def_version, flowable_instance_id, business_key, business_type,
                   title, initiator_id, status, result, started_at, ended_at
              FROM hub_wf_instance
             WHERE tenant_id = #{tenantId} AND flowable_instance_id = #{flowableInstanceId} AND deleted = 0
            """)
    Map<String, Object> findByFlowableId(@Param("tenantId") long tenantId,
                                         @Param("flowableInstanceId") String flowableInstanceId);

    @Update("""
            UPDATE hub_wf_instance
               SET status = 'COMPLETED', result = #{result}, ended_at = now(),
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'RUNNING' AND deleted = 0
            """)
    int markCompleted(@Param("tenantId") long tenantId,
                      @Param("id") long id,
                      @Param("result") String result,
                      @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_wf_instance
               SET status = 'TERMINATED', result = 'CANCELLED', ended_at = now(),
                   updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(), version = version + 1
             WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'RUNNING' AND deleted = 0
            """)
    int markTerminated(@Param("tenantId") long tenantId,
                       @Param("id") long id,
                       @Param("operatorId") Long operatorId);

    @Select("""
            <script>
            SELECT count(*) FROM hub_wf_instance
             WHERE tenant_id = #{tenantId} AND status = #{status} AND deleted = 0
            <if test="from != null">AND started_at &gt;= #{from}</if>
            <if test="to != null">AND started_at &lt;= #{to}</if>
            </script>
            """)
    long countByStatus(@Param("tenantId") long tenantId,
                       @Param("status") String status,
                       @Param("from") Instant from,
                       @Param("to") Instant to);

    @Select("""
            <script>
            SELECT COALESCE(avg(extract(epoch FROM (ended_at - started_at)) / 60.0), 0)
              FROM hub_wf_instance
             WHERE tenant_id = #{tenantId} AND status = 'COMPLETED' AND ended_at IS NOT NULL AND deleted = 0
            <if test="from != null">AND started_at &gt;= #{from}</if>
            <if test="to != null">AND started_at &lt;= #{to}</if>
            </script>
            """)
    double avgDurationMinutes(@Param("tenantId") long tenantId,
                              @Param("from") Instant from,
                              @Param("to") Instant to);
}
