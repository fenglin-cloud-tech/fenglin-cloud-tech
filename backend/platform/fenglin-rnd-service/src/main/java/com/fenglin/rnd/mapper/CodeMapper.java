package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * E1 代码库、提交、提交关联与 Webhook 事件 Mapper。
 */
@Mapper
public interface CodeMapper {

    @Insert("""
            INSERT INTO devops_repo
                (id, tenant_id, product_id, repo_name, vcs_type, repo_url, default_branch,
                 access_token_cipher, access_token_hash, webhook_secret_cipher, sync_status, created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{repoName}, #{vcsType}, #{repoUrl}, #{defaultBranch},
                    #{tokenCipher}, #{tokenHash}, #{secretCipher,jdbcType=VARCHAR}, 'pending', #{userId})
            """)
    void insertRepo(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                    @Param("repoName") String repoName, @Param("vcsType") String vcsType,
                    @Param("repoUrl") String repoUrl, @Param("defaultBranch") String defaultBranch,
                    @Param("tokenCipher") String tokenCipher, @Param("tokenHash") String tokenHash,
                    @Param("secretCipher") String secretCipher, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", product_id AS "productId", repo_name AS "repoName", vcs_type AS "vcsType",
                   repo_url AS "repoUrl", default_branch AS "defaultBranch", sync_status AS "syncStatus",
                   access_token_cipher AS "tokenCipher", last_sync_at::text AS "lastSyncAt",
                   created_at::text AS "createdAt"
            FROM devops_repo
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findRepo(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id AS "id", product_id AS "productId", repo_name AS "repoName", vcs_type AS "vcsType",
                   repo_url AS "repoUrl", default_branch AS "defaultBranch", sync_status AS "syncStatus",
                   access_token_cipher AS "tokenCipher", last_sync_at::text AS "lastSyncAt",
                   created_at::text AS "createdAt"
            FROM devops_repo
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND deleted = 0
            ORDER BY id DESC
            </script>
            """)
    List<Map<String, Object>> listRepos(@Param("tenantId") long tenantId, @Param("productId") long productId);

    @Update("""
            UPDATE devops_repo SET sync_status = 'ok', last_sync_at = now(), updated_by = #{userId},
                   version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int markSynced(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Update("""
            UPDATE devops_repo
            SET repo_name = COALESCE(#{repoName,jdbcType=VARCHAR}, repo_name),
                default_branch = COALESCE(#{defaultBranch,jdbcType=VARCHAR}, default_branch),
                access_token_cipher = COALESCE(#{tokenCipher,jdbcType=VARCHAR}, access_token_cipher),
                access_token_hash = COALESCE(#{tokenHash,jdbcType=VARCHAR}, access_token_hash),
                webhook_secret_cipher = COALESCE(#{secretCipher,jdbcType=VARCHAR}, webhook_secret_cipher),
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int updateRepo(@Param("tenantId") long tenantId, @Param("id") long id,
                   @Param("repoName") String repoName, @Param("defaultBranch") String defaultBranch,
                   @Param("tokenCipher") String tokenCipher, @Param("tokenHash") String tokenHash,
                   @Param("secretCipher") String secretCipher, @Param("userId") long userId);

    @Update("""
            UPDATE devops_repo SET deleted = 1, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    int deleteRepo(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Insert("""
            INSERT INTO devops_commit
                (id, tenant_id, repo_id, commit_sha, branch, author_name, author_email_hash, author_user_id,
                 message, add_lines, del_lines, files_changed, is_merge, is_excluded, committed_at, created_by)
            VALUES (#{id}, #{tenantId}, #{repoId}, #{commitSha}, #{branch,jdbcType=VARCHAR}, #{authorName},
                    #{authorEmailHash,jdbcType=VARCHAR}, #{authorUserId,jdbcType=BIGINT},
                    #{message,jdbcType=VARCHAR}, #{addLines}, #{delLines}, #{filesChanged}, #{isMerge},
                    #{isExcluded}, #{committedAt}, #{userId})
            ON CONFLICT (tenant_id, repo_id, commit_sha) WHERE deleted = 0 DO NOTHING
            """)
    int insertCommit(@Param("id") long id, @Param("tenantId") long tenantId, @Param("repoId") long repoId,
                     @Param("commitSha") String commitSha, @Param("branch") String branch,
                     @Param("authorName") String authorName, @Param("authorEmailHash") String authorEmailHash,
                     @Param("authorUserId") Long authorUserId, @Param("message") String message,
                     @Param("addLines") int addLines, @Param("delLines") int delLines,
                     @Param("filesChanged") int filesChanged, @Param("isMerge") int isMerge,
                     @Param("isExcluded") int isExcluded, @Param("committedAt") OffsetDateTime committedAt,
                     @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", repo_id AS "repoId", commit_sha AS "commitSha", branch AS "branch",
                   author_name AS "authorName", message AS "message", add_lines AS "addLines",
                   del_lines AS "delLines", files_changed AS "filesChanged", is_merge AS "isMerge",
                   is_excluded AS "isExcluded", committed_at::text AS "committedAt"
            FROM devops_commit
            WHERE tenant_id = #{tenantId} AND repo_id = #{repoId} AND commit_sha = #{sha} AND deleted = 0
            """)
    Map<String, Object> findCommitBySha(@Param("tenantId") long tenantId, @Param("repoId") long repoId,
                                        @Param("sha") String sha);

    @Select("""
            SELECT id AS "id", repo_id AS "repoId", commit_sha AS "commitSha", branch AS "branch",
                   author_name AS "authorName", message AS "message", add_lines AS "addLines",
                   del_lines AS "delLines", files_changed AS "filesChanged", is_merge AS "isMerge",
                   is_excluded AS "isExcluded", committed_at::text AS "committedAt"
            FROM devops_commit
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findCommit(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id AS "id", repo_id AS "repoId", commit_sha AS "commitSha", branch AS "branch",
                   author_name AS "authorName", message AS "message", add_lines AS "addLines",
                   del_lines AS "delLines", files_changed AS "filesChanged", is_merge AS "isMerge",
                   is_excluded AS "isExcluded", committed_at::text AS "committedAt"
            FROM devops_commit
            WHERE tenant_id = #{tenantId} AND repo_id = #{repoId} AND deleted = 0
              <if test="authorUserId != null">AND author_user_id = #{authorUserId}</if>
            ORDER BY committed_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listCommits(@Param("tenantId") long tenantId, @Param("repoId") long repoId,
                                          @Param("authorUserId") Long authorUserId, @Param("limit") int limit,
                                          @Param("offset") int offset);

    @Insert("""
            INSERT INTO devops_commit_link
                (id, tenant_id, commit_id, biz_type, biz_id, link_type, created_by)
            VALUES (#{id}, #{tenantId}, #{commitId}, #{bizType}, #{bizId}, #{linkType}, #{userId})
            ON CONFLICT (tenant_id, commit_id, biz_type, biz_id) WHERE deleted = 0 DO NOTHING
            """)
    int insertLink(@Param("id") long id, @Param("tenantId") long tenantId, @Param("commitId") long commitId,
                   @Param("bizType") String bizType, @Param("bizId") long bizId,
                   @Param("linkType") String linkType, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", commit_id AS "commitId", biz_type AS "bizType", biz_id AS "bizId",
                   link_type AS "linkType"
            FROM devops_commit_link
            WHERE tenant_id = #{tenantId} AND commit_id = #{commitId} AND deleted = 0
            """)
    List<Map<String, Object>> listLinks(@Param("tenantId") long tenantId, @Param("commitId") long commitId);

    @Select("""
            SELECT count(1) FROM devops_commit c
            WHERE c.tenant_id = #{tenantId} AND c.repo_id = #{repoId} AND c.deleted = 0
              AND c.is_excluded = 0 AND c.is_merge = 0
            """)
    long countEffectiveCommitCandidates(@Param("tenantId") long tenantId, @Param("repoId") long repoId);

    @Select("""
            SELECT count(DISTINCT c.id) FROM devops_commit c
            JOIN devops_commit_link l ON l.tenant_id = c.tenant_id AND l.commit_id = c.id AND l.deleted = 0
            WHERE c.tenant_id = #{tenantId} AND c.repo_id = #{repoId} AND c.deleted = 0
              AND c.is_excluded = 0 AND c.is_merge = 0
            """)
    long countLinkedCommits(@Param("tenantId") long tenantId, @Param("repoId") long repoId);

    @Insert("""
            INSERT INTO devops_webhook_event
                (id, tenant_id, source, event_type, event_id, payload, signature_valid, process_status,
                 created_by)
            VALUES (#{id}, #{tenantId}, #{source}, #{eventType}, #{eventId}, CAST(#{payload} AS jsonb),
                    #{signatureValid}, #{processStatus}, #{userId})
            ON CONFLICT (source, event_id) DO NOTHING
            """)
    int insertWebhookEvent(@Param("id") long id, @Param("tenantId") long tenantId, @Param("source") String source,
                           @Param("eventType") String eventType, @Param("eventId") String eventId,
                           @Param("payload") String payload, @Param("signatureValid") int signatureValid,
                           @Param("processStatus") String processStatus, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", source AS "source", event_type AS "eventType", event_id AS "eventId",
                   signature_valid AS "signatureValid", process_status AS "processStatus",
                   error_msg AS "errorMsg", created_at::text AS "createdAt"
            FROM devops_webhook_event
            WHERE tenant_id = #{tenantId} AND source = #{source} AND event_id = #{eventId}
            """)
    Map<String, Object> findWebhookEvent(@Param("tenantId") long tenantId, @Param("source") String source,
                                         @Param("eventId") String eventId);
}
