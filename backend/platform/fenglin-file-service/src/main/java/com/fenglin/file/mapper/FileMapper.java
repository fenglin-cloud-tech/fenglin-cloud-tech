package com.fenglin.file.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface FileMapper {

    @Insert("""
            INSERT INTO hub_file (id, tenant_id, file_no, name, ext, size, md5, storage_type, bucket, object_key,
                                  biz_type, biz_id, sec_level, status, virus_scanned,
                                  created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{fileNo}, #{name}, #{ext,jdbcType=VARCHAR}, #{size}, #{md5}, 'MINIO',
                    #{bucket}, #{objectKey}, #{bizType,jdbcType=VARCHAR}, #{bizId,jdbcType=VARCHAR},
                    #{secLevel}, #{status}, #{virusScanned},
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("fileNo") String fileNo,
               @Param("name") String name,
               @Param("ext") String ext,
               @Param("size") long size,
               @Param("md5") String md5,
               @Param("bucket") String bucket,
               @Param("objectKey") String objectKey,
               @Param("bizType") String bizType,
               @Param("bizId") String bizId,
               @Param("secLevel") int secLevel,
               @Param("status") String status,
               @Param("virusScanned") int virusScanned,
               @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, file_no, name, ext, size, md5, storage_type, bucket, object_key, biz_type, biz_id,
                   sec_level, status, virus_scanned, created_by, created_at
              FROM hub_file
             WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id, file_no, name, ext, size, md5, bucket, object_key, status
              FROM hub_file
             WHERE tenant_id = #{tenantId} AND md5 = #{md5} AND size = #{size}
               AND status = 'AVAILABLE' AND deleted = 0
             ORDER BY created_at ASC
             LIMIT 1
            """)
    Map<String, Object> findReusable(@Param("tenantId") long tenantId,
                                     @Param("md5") String md5,
                                     @Param("size") long size);

    @Select("""
            SELECT COALESCE(sum(size), 0) FROM hub_file
             WHERE tenant_id = #{tenantId} AND deleted = 0
            """)
    long sumSize(@Param("tenantId") long tenantId);
}
