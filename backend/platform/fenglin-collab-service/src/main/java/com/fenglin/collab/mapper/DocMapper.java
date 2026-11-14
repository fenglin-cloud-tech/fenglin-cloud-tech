package com.fenglin.collab.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 文档域 Mapper。正文仅以密文落库，检索使用标题与标签，禁止对密文做唯一索引。
 */
@Mapper
public interface DocMapper {

    @Insert("""
            INSERT INTO collab_document
                (id, tenant_id, title, doc_type, owner_id, folder_id, content_cipher, content_hash,
                 current_version, secret_level, share_scope, status, created_by)
            VALUES (CAST(#{id} AS uuid), #{tenantId}, #{title}, #{docType}, #{ownerId},
                    CAST(#{folderId,jdbcType=VARCHAR} AS uuid), #{contentCipher,jdbcType=VARCHAR},
                    #{contentHash,jdbcType=VARCHAR}, 1, #{secretLevel}, 'PRIVATE', 'DRAFT', #{ownerId})
            """)
    void insertDocument(@Param("id") String id, @Param("tenantId") long tenantId, @Param("title") String title,
                        @Param("docType") String docType, @Param("ownerId") long ownerId,
                        @Param("folderId") String folderId, @Param("contentCipher") String contentCipher,
                        @Param("contentHash") String contentHash, @Param("secretLevel") String secretLevel);

    @Select("""
            SELECT id AS "docId", title AS "title", doc_type AS "docType", owner_id AS "ownerId",
                   content_cipher AS "contentCipher", current_version AS "currentVersion",
                   secret_level AS "secretLevel", watermark_enabled AS "watermarkEnabled",
                   share_scope AS "shareScope", link_token AS "linkToken",
                   (EXTRACT(EPOCH FROM link_expire_at) * 1000)::bigint AS "linkExpireAt",
                   status AS "status", version AS "version",
                   (EXTRACT(EPOCH FROM updated_at) * 1000)::bigint AS "updatedAt"
            FROM collab_document
            WHERE tenant_id = #{tenantId} AND id = CAST(#{docId} AS uuid) AND deleted = 0
            """)
    Map<String, Object> findDocument(@Param("tenantId") long tenantId, @Param("docId") String docId);

    @Select("""
            SELECT id AS "docId", content_cipher AS "contentCipher", current_version AS "currentVersion",
                   version AS "version"
            FROM collab_document
            WHERE tenant_id = #{tenantId} AND id = CAST(#{docId} AS uuid) AND deleted = 0
            FOR UPDATE
            """)
    Map<String, Object> lockDocument(@Param("tenantId") long tenantId, @Param("docId") String docId);

    @Select("""
            SELECT d.id AS "docId", d.title AS "title", d.doc_type AS "docType", d.owner_id AS "ownerId",
                   d.current_version AS "currentVersion", d.secret_level AS "secretLevel",
                   d.share_scope AS "shareScope", d.status AS "status",
                   (EXTRACT(EPOCH FROM d.updated_at) * 1000)::bigint AS "updatedAt"
            FROM collab_document d
            WHERE d.tenant_id = #{tenantId} AND d.deleted = 0
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL OR d.title ILIKE '%' || #{keyword} || '%')
              AND (d.owner_id = #{userId}
                   OR d.share_scope <> 'PRIVATE'
                   OR EXISTS (SELECT 1 FROM collab_document_collaborator c
                              WHERE c.tenant_id = d.tenant_id AND c.document_id = d.id
                                AND c.subject_type = 'USER' AND c.subject_id = #{userId} AND c.deleted = 0))
            ORDER BY d.updated_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listDocuments(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                            @Param("keyword") String keyword, @Param("offset") int offset,
                                            @Param("size") int size);

    @Select("""
            SELECT count(1)
            FROM collab_document d
            WHERE d.tenant_id = #{tenantId} AND d.deleted = 0
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL OR d.title ILIKE '%' || #{keyword} || '%')
              AND (d.owner_id = #{userId}
                   OR d.share_scope <> 'PRIVATE'
                   OR EXISTS (SELECT 1 FROM collab_document_collaborator c
                              WHERE c.tenant_id = d.tenant_id AND c.document_id = d.id
                                AND c.subject_type = 'USER' AND c.subject_id = #{userId} AND c.deleted = 0))
            """)
    long countDocuments(@Param("tenantId") long tenantId, @Param("userId") long userId,
                        @Param("keyword") String keyword);

    /**
     * 员工自助：只返回 owner_id 等于当前登录用户的文档。
     */
    @Select("""
            SELECT d.id AS "docId", d.title AS "title", d.doc_type AS "docType", d.owner_id AS "ownerId",
                   d.current_version AS "currentVersion", d.secret_level AS "secretLevel",
                   d.share_scope AS "shareScope", d.status AS "status",
                   (EXTRACT(EPOCH FROM d.updated_at) * 1000)::bigint AS "updatedAt"
            FROM collab_document d
            WHERE d.tenant_id = #{tenantId} AND d.owner_id = #{userId} AND d.deleted = 0
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL OR d.title ILIKE '%' || #{keyword} || '%')
            ORDER BY d.updated_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listDocumentsByOwner(@Param("tenantId") long tenantId, @Param("userId") long userId,
                                                   @Param("keyword") String keyword, @Param("offset") int offset,
                                                   @Param("size") int size);

    @Select("""
            SELECT count(1)
            FROM collab_document d
            WHERE d.tenant_id = #{tenantId} AND d.owner_id = #{userId} AND d.deleted = 0
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS varchar) IS NULL OR d.title ILIKE '%' || #{keyword} || '%')
            """)
    long countDocumentsByOwner(@Param("tenantId") long tenantId, @Param("userId") long userId,
                               @Param("keyword") String keyword);

    @Update("""
            UPDATE collab_document
            SET title = COALESCE(#{title,jdbcType=VARCHAR}, title),
                content_cipher = COALESCE(#{contentCipher,jdbcType=VARCHAR}, content_cipher),
                content_hash = COALESCE(#{contentHash,jdbcType=VARCHAR}, content_hash),
                current_version = #{newVersion}, status = 'DRAFT', updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{docId} AS uuid) AND deleted = 0
              AND current_version = #{expectedVersion}
            """)
    int updateContent(@Param("tenantId") long tenantId, @Param("docId") String docId,
                      @Param("title") String title, @Param("contentCipher") String contentCipher,
                      @Param("contentHash") String contentHash, @Param("newVersion") int newVersion,
                      @Param("expectedVersion") int expectedVersion, @Param("userId") long userId);

    @Insert("""
            INSERT INTO collab_document_version
                (id, tenant_id, document_id, version_no, content_cipher, editor_id, version_type, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{docId} AS uuid), #{versionNo}, #{contentCipher,jdbcType=VARCHAR},
                    #{editorId}, #{versionType}, #{editorId})
            ON CONFLICT (tenant_id, document_id, version_no) DO NOTHING
            """)
    void insertVersion(@Param("id") long id, @Param("tenantId") long tenantId, @Param("docId") String docId,
                       @Param("versionNo") int versionNo, @Param("contentCipher") String contentCipher,
                       @Param("editorId") Long editorId, @Param("versionType") String versionType);

    @Select("""
            SELECT version_no AS "versionNo", content_cipher AS "contentCipher", editor_id AS "editorId",
                   version_type AS "versionType", (EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS "createdAt"
            FROM collab_document_version
            WHERE tenant_id = #{tenantId} AND document_id = CAST(#{docId} AS uuid)
              AND version_no = #{versionNo} AND deleted = 0
            """)
    Map<String, Object> findVersion(@Param("tenantId") long tenantId, @Param("docId") String docId,
                                    @Param("versionNo") int versionNo);

    @Select("""
            SELECT version_no AS "versionNo", editor_id AS "editorId", version_type AS "versionType",
                   (EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS "createdAt"
            FROM collab_document_version
            WHERE tenant_id = #{tenantId} AND document_id = CAST(#{docId} AS uuid) AND deleted = 0
            ORDER BY version_no DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> listVersions(@Param("tenantId") long tenantId, @Param("docId") String docId,
                                           @Param("offset") int offset, @Param("size") int size);

    @Select("""
            SELECT count(1) FROM collab_document_version
            WHERE tenant_id = #{tenantId} AND document_id = CAST(#{docId} AS uuid) AND deleted = 0
            """)
    long countVersions(@Param("tenantId") long tenantId, @Param("docId") String docId);

    @Insert("""
            INSERT INTO collab_document_collaborator
                (id, tenant_id, document_id, subject_type, subject_id, permission, granted_by, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{docId} AS uuid), #{subjectType}, #{subjectId}, #{permission},
                    #{userId}, #{userId})
            """)
    void insertCollaborator(@Param("id") long id, @Param("tenantId") long tenantId, @Param("docId") String docId,
                            @Param("subjectType") String subjectType, @Param("subjectId") long subjectId,
                            @Param("permission") String permission, @Param("userId") long userId);

    @Select("""
            SELECT id AS "collaboratorId", subject_type AS "subjectType", subject_id AS "subjectId",
                   permission AS "permission", granted_by AS "grantedBy"
            FROM collab_document_collaborator
            WHERE tenant_id = #{tenantId} AND document_id = CAST(#{docId} AS uuid) AND deleted = 0
            ORDER BY created_at
            """)
    List<Map<String, Object>> listCollaborators(@Param("tenantId") long tenantId, @Param("docId") String docId);

    @Select("""
            SELECT permission AS permission FROM collab_document_collaborator
            WHERE tenant_id = #{tenantId} AND document_id = CAST(#{docId} AS uuid)
              AND subject_type = 'USER' AND subject_id = #{userId} AND deleted = 0
            """)
    String findCollaboratorPermission(@Param("tenantId") long tenantId, @Param("docId") String docId,
                                      @Param("userId") long userId);

    @Update("""
            UPDATE collab_document SET link_token = #{linkToken}, link_expire_at = #{expireAt},
                   share_scope = 'LINK', updated_by = #{userId}
            WHERE tenant_id = #{tenantId} AND id = CAST(#{docId} AS uuid) AND deleted = 0
            """)
    void setShareLink(@Param("tenantId") long tenantId, @Param("docId") String docId,
                      @Param("linkToken") String linkToken,
                      @Param("expireAt") java.time.OffsetDateTime expireAt, @Param("userId") long userId);

    @Select("""
            SELECT id AS "docId", title AS "title", owner_id AS "ownerId", secret_level AS "secretLevel",
                   current_version AS "currentVersion", content_cipher AS "contentCipher",
                   (EXTRACT(EPOCH FROM link_expire_at) * 1000)::bigint AS "linkExpireAt"
            FROM collab_document
            WHERE tenant_id = #{tenantId} AND link_token = #{linkToken} AND deleted = 0 AND share_scope = 'LINK'
            """)
    Map<String, Object> findByLinkToken(@Param("tenantId") long tenantId, @Param("linkToken") String linkToken);

    @Insert("""
            INSERT INTO collab_document_comment
                (id, tenant_id, document_id, parent_id, author_id, content_cipher, anchor, mention_ids, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{docId} AS uuid), #{parentId,jdbcType=BIGINT}, #{authorId},
                    #{contentCipher}, CAST(#{anchor,jdbcType=VARCHAR} AS jsonb),
                    CAST(#{mentionIds,jdbcType=VARCHAR} AS bigint[]), #{authorId})
            """)
    void insertComment(@Param("id") long id, @Param("tenantId") long tenantId, @Param("docId") String docId,
                       @Param("parentId") Long parentId, @Param("authorId") long authorId,
                       @Param("contentCipher") String contentCipher, @Param("anchor") String anchor,
                       @Param("mentionIds") String mentionIds);

    @Select("""
            SELECT id AS "commentId", parent_id AS "parentId", author_id AS "authorId",
                   content_cipher AS "contentCipher", resolved AS "resolved",
                   (EXTRACT(EPOCH FROM created_at) * 1000)::bigint AS "createdAt"
            FROM collab_document_comment
            WHERE tenant_id = #{tenantId} AND document_id = CAST(#{docId} AS uuid) AND deleted = 0
            ORDER BY created_at
            """)
    List<Map<String, Object>> listComments(@Param("tenantId") long tenantId, @Param("docId") String docId);
}
