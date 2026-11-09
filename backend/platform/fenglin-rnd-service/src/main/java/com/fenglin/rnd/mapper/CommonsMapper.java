package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 公共 Mapper：租户盐、幂等记录、业务编号序列、需求状态默认值。
 */
@Mapper
public interface CommonsMapper {

    @Select("SELECT salt FROM devops_tenant_key WHERE tenant_id = #{tenantId} AND deleted = 0")
    String findTenantSalt(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO devops_tenant_key (id, tenant_id, salt, created_by)
            VALUES (#{id}, #{tenantId}, #{salt}, #{userId})
            """)
    void insertTenantKey(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("salt") String salt, @Param("userId") long userId);

    @Select("SELECT biz_id FROM devops_idempotency_record "
            + "WHERE tenant_id = #{tenantId} AND idem_key = #{key}")
    Long findIdempotentBizId(@Param("tenantId") long tenantId, @Param("key") String key);

    @Insert("""
            INSERT INTO devops_idempotency_record (id, tenant_id, idem_key, api_path, biz_id, created_by)
            VALUES (#{id}, #{tenantId}, #{key}, #{apiPath}, #{bizId}, #{userId})
            ON CONFLICT (tenant_id, idem_key, api_path) DO NOTHING
            """)
    int insertIdempotent(@Param("id") long id, @Param("tenantId") long tenantId,
                         @Param("key") String key, @Param("apiPath") String apiPath,
                         @Param("bizId") long bizId, @Param("userId") long userId);

    @Select("SELECT nextval('devops_req_no_seq')")
    long nextRequirementNo();

    @Select("SELECT nextval('devops_task_no_seq')")
    long nextTaskNo();

    @Select("SELECT nextval('devops_defect_no_seq')")
    long nextDefectNo();

    @Select("SELECT nextval('devops_sprint_no_seq')")
    long nextSprintNo();

    @Select("SELECT nextval('devops_case_no_seq')")
    long nextCaseNo();

    @Select("SELECT nextval('devops_plan_no_seq')")
    long nextPlanNo();

    @Select("SELECT nextval('devops_release_no_seq')")
    long nextReleaseNo();

    @Select("SELECT nextval('devops_run_no_seq')")
    long nextRunNo();

    @Select("SELECT nextval('devops_build_no_seq')")
    long nextBuildNo();

    /** 产品创建时按租户默认状态方案落库，保证 RLS 下产品维度可见。 */
    @Insert("""
            INSERT INTO devops_requirement_status
                (id, tenant_id, product_id, status_code, status_name, category, color, sort_no, created_by)
            VALUES
                (#{baseId} + 1, #{tenantId}, #{productId}, 'TODO', '待处理', 'todo', '#909399', 1, #{userId}),
                (#{baseId} + 2, #{tenantId}, #{productId}, 'REVIEWING', '评审中', 'todo', '#E6A23C', 2, #{userId}),
                (#{baseId} + 3, #{tenantId}, #{productId}, 'REVIEWED', '已评审', 'todo', '#409EFF', 3, #{userId}),
                (#{baseId} + 4, #{tenantId}, #{productId}, 'REJECTED', '已拒绝', 'todo', '#F56C6C', 4, #{userId}),
                (#{baseId} + 5, #{tenantId}, #{productId}, 'DOING', '开发中', 'doing', '#409EFF', 5, #{userId}),
                (#{baseId} + 6, #{tenantId}, #{productId}, 'DONE', '已完成', 'done', '#67C23A', 6, #{userId}),
                (#{baseId} + 7, #{tenantId}, #{productId}, 'CLOSED', '已关闭', 'done', '#909399', 7, #{userId})
            """)
    void insertDefaultRequirementStatus(@Param("baseId") long baseId, @Param("tenantId") long tenantId,
                                        @Param("productId") long productId, @Param("userId") long userId);

    @Select("SELECT id FROM devops_requirement_status WHERE tenant_id = #{tenantId} "
            + "AND product_id = #{productId} AND status_code = 'TODO' AND deleted = 0")
    Long findTodoStatusId(@Param("tenantId") long tenantId, @Param("productId") long productId);
}
