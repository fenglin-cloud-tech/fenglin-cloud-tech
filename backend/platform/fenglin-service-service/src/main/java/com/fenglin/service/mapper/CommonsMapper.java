package com.fenglin.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 公共 Mapper：租户盐、幂等记录、业务编号序列。
 */
@Mapper
public interface CommonsMapper {

    @Select("SELECT salt FROM svc_tenant_key WHERE tenant_id = #{tenantId} AND deleted = 0")
    String findTenantSalt(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO svc_tenant_key (id, tenant_id, salt, created_by)
            VALUES (#{id}, #{tenantId}, #{salt}, #{userId})
            """)
    void insertTenantKey(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("salt") String salt, @Param("userId") long userId);

    @Select("SELECT biz_id FROM svc_idempotency_record "
            + "WHERE tenant_id = #{tenantId} AND idem_key = #{key} AND api_path = #{apiPath}")
    Long findIdempotentBizId(@Param("tenantId") long tenantId, @Param("key") String key,
                             @Param("apiPath") String apiPath);

    @Insert("""
            INSERT INTO svc_idempotency_record (id, tenant_id, idem_key, api_path, biz_id, created_by)
            VALUES (#{id}, #{tenantId}, #{key}, #{apiPath}, #{bizId}, #{userId})
            ON CONFLICT (tenant_id, idem_key, api_path) DO NOTHING
            """)
    int insertIdempotent(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("key") String key, @Param("apiPath") String apiPath,
                         @Param("bizId") long bizId, @Param("userId") long userId);

    @Select("SELECT nextval('svc_ticket_no_seq')")
    long nextTicketNo();

    @Select("SELECT nextval('svc_problem_no_seq')")
    long nextProblemNo();

    @Select("SELECT nextval('svc_change_no_seq')")
    long nextChangeNo();

    @Select("SELECT nextval('svc_release_no_seq')")
    long nextReleaseNo();

    @Select("SELECT nextval('svc_robot_task_no_seq')")
    long nextRobotTaskNo();

    @Select("SELECT nextval('svc_app_code_seq')")
    long nextAppCode();

    @Select("SELECT now()")
    java.sql.Timestamp now();
}
