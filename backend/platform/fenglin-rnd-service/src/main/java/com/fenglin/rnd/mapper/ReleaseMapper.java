package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * E1 发布单与部署记录 Mapper。
 */
@Mapper
public interface ReleaseMapper {

    @Insert("""
            INSERT INTO devops_release
                (id, tenant_id, product_id, release_no, release_version, env, build_id, status,
                 window_start, window_end, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{releaseNo}, #{releaseVersion}, #{env},
                    #{buildId,jdbcType=BIGINT}, 'draft', CAST(#{windowStart,jdbcType=VARCHAR} AS timestamptz),
                    CAST(#{windowEnd,jdbcType=VARCHAR} AS timestamptz), #{userId})
            """)
    void insertRelease(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                       @Param("releaseNo") String releaseNo, @Param("releaseVersion") String releaseVersion,
                       @Param("env") String env, @Param("buildId") Long buildId,
                       @Param("windowStart") String windowStart, @Param("windowEnd") String windowEnd,
                       @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", product_id AS "productId", release_no AS "releaseNo",
                   release_version AS "releaseVersion", env AS "env", build_id AS "buildId", status AS "status",
                   approval_instance_id AS "approvalInstanceId", window_start::text AS "windowStart",
                   window_end::text AS "windowEnd", deployed_at::text AS "deployedAt",
                   created_at::text AS "createdAt"
            FROM devops_release
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findRelease(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id AS "id", product_id AS "productId", release_no AS "releaseNo",
                   release_version AS "releaseVersion", env AS "env", build_id AS "buildId", status AS "status",
                   approval_instance_id AS "approvalInstanceId", window_start::text AS "windowStart",
                   window_end::text AS "windowEnd", deployed_at::text AS "deployedAt",
                   created_at::text AS "createdAt"
            FROM devops_release
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND deleted = 0
              <if test="env != null">AND env = #{env}</if>
              <if test="status != null">AND status = #{status}</if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listReleases(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                           @Param("env") String env, @Param("status") String status,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE devops_release
            SET status = #{status},
                approval_instance_id = COALESCE(#{approvalInstanceId,jdbcType=VARCHAR}, approval_instance_id),
                deployed_at = CASE WHEN #{status} = 'deployed' THEN now() ELSE deployed_at END,
                is_rollback = CASE WHEN #{status} = 'rolled_back' THEN 1 ELSE is_rollback END,
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                     @Param("approvalInstanceId") String approvalInstanceId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_deployment
                (id, tenant_id, release_id, env, result, strategy, operator_id, started_at, finished_at,
                 remark, created_by)
            VALUES (#{id}, #{tenantId}, #{releaseId}, #{env}, #{result}, #{strategy,jdbcType=VARCHAR},
                    #{userId}, now(), now(), #{remark,jdbcType=VARCHAR}, #{userId})
            """)
    void insertDeployment(@Param("id") long id, @Param("tenantId") long tenantId, @Param("releaseId") long releaseId,
                          @Param("env") String env, @Param("result") String result,
                          @Param("strategy") String strategy, @Param("remark") String remark,
                          @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", release_id AS "releaseId", env AS "env", result AS "result",
                   strategy AS "strategy", operator_id AS "operatorId", started_at::text AS "startedAt",
                   finished_at::text AS "finishedAt"
            FROM devops_deployment
            WHERE tenant_id = #{tenantId} AND release_id = #{releaseId} AND deleted = 0
            ORDER BY id DESC
            """)
    List<Map<String, Object>> listDeployments(@Param("tenantId") long tenantId,
                                              @Param("releaseId") long releaseId);

    @Select("""
            SELECT count(1) FROM devops_deployment d
            JOIN devops_release r ON r.tenant_id = d.tenant_id AND r.id = d.release_id
            WHERE d.tenant_id = #{tenantId} AND r.product_id = #{productId} AND d.env = 'prod'
              AND d.result IN ('success', 'rolled_back', 'failed')
              AND d.started_at >= CAST(#{from} AS timestamptz) AND d.started_at < CAST(#{to} AS timestamptz)
              AND d.deleted = 0
            """)
    long countProdDeployments(@Param("tenantId") long tenantId, @Param("productId") long productId,
                              @Param("from") String from, @Param("to") String to);

    @Select("""
            SELECT count(1) FROM devops_deployment d
            JOIN devops_release r ON r.tenant_id = d.tenant_id AND r.id = d.release_id
            WHERE d.tenant_id = #{tenantId} AND r.product_id = #{productId} AND d.env = 'prod'
              AND d.result IN ('failed', 'rolled_back')
              AND d.started_at >= CAST(#{from} AS timestamptz) AND d.started_at < CAST(#{to} AS timestamptz)
              AND d.deleted = 0
            """)
    long countFailedProdDeployments(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                    @Param("from") String from, @Param("to") String to);

    @Select("""
            SELECT COALESCE(avg(extract(epoch FROM (r.deployed_at - r.created_at)) / 3600.0), 0)
            FROM devops_release r
            WHERE r.tenant_id = #{tenantId} AND r.product_id = #{productId} AND r.env = 'prod'
              AND r.status = 'deployed' AND r.deployed_at IS NOT NULL
              AND r.deployed_at >= CAST(#{from} AS timestamptz) AND r.deployed_at < CAST(#{to} AS timestamptz)
              AND r.deleted = 0
            """)
    BigDecimal avgLeadTimeHours(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                @Param("from") String from, @Param("to") String to);

    @Select("SELECT count(1) FROM devops_release WHERE tenant_id = #{tenantId} AND product_id = #{productId} "
            + "AND env = 'prod' AND status = 'deployed' AND deleted = 0")
    long countDeployed(@Param("tenantId") long tenantId, @Param("productId") long productId);
}
