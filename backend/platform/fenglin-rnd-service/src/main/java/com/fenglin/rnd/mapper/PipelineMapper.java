package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * E1 流水线与构建 Mapper。
 */
@Mapper
public interface PipelineMapper {

    @Insert("""
            INSERT INTO devops_pipeline
                (id, tenant_id, product_id, name, ci_type, trigger_mode, status, repo_id, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{name}, #{ciType}, #{triggerMode}, 'active',
                    #{repoId,jdbcType=BIGINT}, #{userId})
            """)
    void insertPipeline(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                        @Param("name") String name, @Param("ciType") String ciType,
                        @Param("triggerMode") String triggerMode, @Param("repoId") Long repoId,
                        @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", product_id AS "productId", name AS "name", ci_type AS "ciType",
                   trigger_mode AS "triggerMode", status AS "status", created_at::text AS "createdAt"
            FROM devops_pipeline
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findPipeline(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", product_id AS "productId", name AS "name", ci_type AS "ciType",
                   trigger_mode AS "triggerMode", status AS "status", created_at::text AS "createdAt"
            FROM devops_pipeline
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND deleted = 0
            ORDER BY id DESC
            """)
    List<Map<String, Object>> listPipelines(@Param("tenantId") long tenantId,
                                            @Param("productId") long productId);

    @Insert("""
            INSERT INTO devops_pipeline_run
                (id, tenant_id, pipeline_id, run_no, trigger_by, trigger_type, status, started_at, created_by)
            VALUES (#{id}, #{tenantId}, #{pipelineId}, #{runNo}, #{userId}, #{triggerType}, 'running', now(),
                    #{userId})
            """)
    void insertRun(@Param("id") long id, @Param("tenantId") long tenantId, @Param("pipelineId") long pipelineId,
                   @Param("runNo") String runNo, @Param("triggerType") String triggerType,
                   @Param("userId") long userId);

    @Update("""
            UPDATE devops_pipeline_run
            SET status = #{status}, finished_at = now(),
                duration_ms = (extract(epoch FROM (now() - started_at)) * 1000)::bigint,
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int finishRun(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                  @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_build
                (id, tenant_id, run_id, build_no, status, commit_sha, artifact_name, artifact_version,
                 artifact_url, quality_gate, started_at, created_by)
            VALUES (#{id}, #{tenantId}, #{runId}, #{buildNo}, #{status}, #{commitSha,jdbcType=VARCHAR},
                    #{artifactName,jdbcType=VARCHAR}, #{artifactVersion,jdbcType=VARCHAR},
                    #{artifactUrl,jdbcType=VARCHAR}, #{qualityGate,jdbcType=VARCHAR}, now(), #{userId})
            """)
    void insertBuild(@Param("id") long id, @Param("tenantId") long tenantId, @Param("runId") long runId,
                     @Param("buildNo") String buildNo, @Param("status") String status,
                     @Param("commitSha") String commitSha, @Param("artifactName") String artifactName,
                     @Param("artifactVersion") String artifactVersion, @Param("artifactUrl") String artifactUrl,
                     @Param("qualityGate") String qualityGate, @Param("userId") long userId);

    @Update("""
            UPDATE devops_build SET status = #{status}, quality_gate = #{qualityGate,jdbcType=VARCHAR},
                   finished_at = now(), updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int finishBuild(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status,
                    @Param("qualityGate") String qualityGate, @Param("userId") long userId);

    @Select("""
            SELECT r.id AS "id", r.pipeline_id AS "pipelineId", r.run_no AS "runNo", r.status AS "status",
                   r.trigger_type AS "triggerType", r.started_at::text AS "startedAt",
                   r.finished_at::text AS "finishedAt", r.duration_ms AS "durationMs",
                   b.id AS "buildId", b.build_no AS "buildNo", b.artifact_name AS "artifactName",
                   b.artifact_version AS "artifactVersion", b.quality_gate AS "qualityGate"
            FROM devops_pipeline_run r
            LEFT JOIN devops_build b ON b.tenant_id = r.tenant_id AND b.run_id = r.id AND b.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.id = #{id} AND r.deleted = 0
            """)
    Map<String, Object> findRun(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT r.id AS "id", r.pipeline_id AS "pipelineId", r.run_no AS "runNo", r.status AS "status",
                   r.trigger_type AS "triggerType", r.started_at::text AS "startedAt",
                   r.finished_at::text AS "finishedAt", r.duration_ms AS "durationMs",
                   b.id AS "buildId", b.build_no AS "buildNo", b.artifact_name AS "artifactName",
                   b.artifact_version AS "artifactVersion", b.quality_gate AS "qualityGate"
            FROM devops_pipeline_run r
            LEFT JOIN devops_build b ON b.tenant_id = r.tenant_id AND b.run_id = r.id AND b.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.pipeline_id = #{pipelineId} AND r.deleted = 0
            ORDER BY r.id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listRuns(@Param("tenantId") long tenantId, @Param("pipelineId") long pipelineId,
                                       @Param("limit") int limit);

    @Select("SELECT count(1) FROM devops_pipeline_run WHERE tenant_id = #{tenantId} AND pipeline_id = #{pipelineId} "
            + "AND deleted = 0")
    long countRuns(@Param("tenantId") long tenantId, @Param("pipelineId") long pipelineId);

    @Select("SELECT count(1) FROM devops_pipeline_run r JOIN devops_pipeline p "
            + "ON p.tenant_id = r.tenant_id AND p.id = r.pipeline_id "
            + "WHERE r.tenant_id = #{tenantId} AND p.product_id = #{productId} AND r.deleted = 0")
    long countRunsByProduct(@Param("tenantId") long tenantId, @Param("productId") long productId);

    @Select("""
            SELECT b.id AS "id", b.run_id AS "run_id", b.build_no AS "build_no", b.status AS "status",
                   b.commit_sha AS "commit_sha", b.artifact_name AS "artifact_name",
                   b.artifact_version AS "artifact_version", b.quality_gate AS "quality_gate",
                   b.started_at::text AS "started_at", b.finished_at::text AS "finished_at"
            FROM devops_build b
            JOIN devops_pipeline_run r ON r.tenant_id = b.tenant_id AND r.id = b.run_id AND r.deleted = 0
            JOIN devops_pipeline p ON p.tenant_id = r.tenant_id AND p.id = r.pipeline_id AND p.deleted = 0
            WHERE b.tenant_id = #{tenantId} AND b.deleted = 0
              AND (#{productId,jdbcType=BIGINT} IS NULL OR p.product_id = #{productId})
            ORDER BY b.id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listBuilds(@Param("tenantId") long tenantId,
                                         @Param("productId") Long productId, @Param("limit") int limit);
}
