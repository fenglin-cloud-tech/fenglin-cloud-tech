package com.fenglin.collab.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 文档图片元数据 Mapper（COL-07）。图片字节存放在文件服务，本表只记录归属与摘要。
 */
@Mapper
public interface DocImageMapper {

    @Insert("""
            INSERT INTO collab_doc_image
                (id, tenant_id, doc_id, file_id, uploader_id, mime, size_bytes, sha256, width, height,
                 status, created_by)
            VALUES (#{id}, #{tenantId}, CAST(#{docId} AS uuid), #{fileId}, #{uploaderId}, #{mime},
                    #{sizeBytes}, #{sha256}, #{width}, #{height}, 'AVAILABLE', #{uploaderId})
            """)
    void insertImage(@Param("id") long id, @Param("tenantId") long tenantId, @Param("docId") String docId,
                     @Param("fileId") long fileId, @Param("uploaderId") long uploaderId,
                     @Param("mime") String mime, @Param("sizeBytes") long sizeBytes,
                     @Param("sha256") String sha256, @Param("width") int width, @Param("height") int height);

    @Select("""
            SELECT id AS "imageId", CAST(doc_id AS varchar) AS "docId", file_id AS "fileId",
                   uploader_id AS "uploaderId", mime AS "mime", size_bytes AS "sizeBytes",
                   sha256 AS "sha256", width AS "width", height AS "height", status AS "status"
            FROM collab_doc_image
            WHERE tenant_id = #{tenantId} AND file_id = #{fileId} AND deleted = 0
            """)
    Map<String, Object> findImage(@Param("tenantId") long tenantId, @Param("fileId") long fileId);

    @Select("""
            SELECT id AS "imageId", CAST(doc_id AS varchar) AS "docId", file_id AS "fileId",
                   uploader_id AS "uploaderId", mime AS "mime", size_bytes AS "sizeBytes",
                   sha256 AS "sha256", width AS "width", height AS "height", status AS "status"
            FROM collab_doc_image
            WHERE tenant_id = #{tenantId} AND doc_id = CAST(#{docId} AS uuid) AND deleted = 0
            ORDER BY created_at ASC
            """)
    List<Map<String, Object>> listByDoc(@Param("tenantId") long tenantId, @Param("docId") String docId);
}
