package com.fenglin.file.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface FileVersionMapper {

    @Insert("""
            INSERT INTO hub_file_version (id, tenant_id, file_id, ver_no, object_key, size, md5, remark,
                                          created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{fileId}, #{verNo}, #{objectKey}, #{size}, #{md5},
                    #{remark,jdbcType=VARCHAR}, #{operatorId,jdbcType=BIGINT}, now(),
                    #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("fileId") long fileId,
               @Param("verNo") int verNo,
               @Param("objectKey") String objectKey,
               @Param("size") long size,
               @Param("md5") String md5,
               @Param("remark") String remark,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT ver_no, size, md5, object_key, created_by, created_at
              FROM hub_file_version
             WHERE tenant_id = #{tenantId} AND file_id = #{fileId} AND deleted = 0
             ORDER BY ver_no DESC
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("fileId") long fileId);

    @Select("""
            SELECT ver_no, size, md5, object_key
              FROM hub_file_version
             WHERE tenant_id = #{tenantId} AND file_id = #{fileId} AND ver_no = #{verNo} AND deleted = 0
            """)
    Map<String, Object> find(@Param("tenantId") long tenantId,
                             @Param("fileId") long fileId,
                             @Param("verNo") int verNo);
}
