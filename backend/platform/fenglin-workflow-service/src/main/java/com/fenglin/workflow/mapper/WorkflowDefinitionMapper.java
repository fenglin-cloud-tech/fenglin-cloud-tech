package com.fenglin.workflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowDefinitionMapper {

    @Insert("""
            INSERT INTO hub_wf_definition (id, tenant_id, process_key, name, category, def_version, bpmn_xml,
                                           form_key, status, created_by, created_at, updated_by, updated_at,
                                           deleted, version)
            VALUES (#{id}, #{tenantId}, #{processKey}, #{name}, #{category,jdbcType=VARCHAR}, #{defVersion},
                    #{bpmnXml}, #{formKey,jdbcType=VARCHAR}, 'PUBLISHED',
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("processKey") String processKey,
               @Param("name") String name,
               @Param("category") String category,
               @Param("defVersion") int defVersion,
               @Param("bpmnXml") String bpmnXml,
               @Param("formKey") String formKey,
               @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_wf_definition
               SET status = 'SUSPENDED', updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(),
                   version = version + 1
             WHERE tenant_id = #{tenantId} AND process_key = #{processKey}
               AND status = 'PUBLISHED' AND deleted = 0
            """)
    int suspendPublished(@Param("tenantId") long tenantId,
                         @Param("processKey") String processKey,
                         @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, process_key, name, category, def_version, form_key, status, created_at
              FROM hub_wf_definition
             WHERE tenant_id = #{tenantId} AND process_key = #{processKey}
               AND status = 'PUBLISHED' AND deleted = 0
             ORDER BY def_version DESC
             LIMIT 1
            """)
    Map<String, Object> findPublished(@Param("tenantId") long tenantId,
                                      @Param("processKey") String processKey);

    @Select("""
            <script>
            SELECT id, process_key, name, category, def_version, form_key, status, created_at
              FROM hub_wf_definition
             WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="category != null and category != ''">
               AND category = #{category}
            </if>
            <if test="status != null and status != ''">
               AND status = #{status}
            </if>
             ORDER BY created_at DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> page(@Param("tenantId") long tenantId,
                                   @Param("category") String category,
                                   @Param("status") String status,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(*)
              FROM hub_wf_definition
             WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="category != null and category != ''">
               AND category = #{category}
            </if>
            <if test="status != null and status != ''">
               AND status = #{status}
            </if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId,
               @Param("category") String category,
               @Param("status") String status);
}
