package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface DictMapper {

    @Select("""
            <script>
            SELECT code, name, scope, status
              FROM hub_dict_type
             WHERE deleted = 0 AND status = 1
             <if test="scope != null and scope != ''">AND scope = #{scope}</if>
             ORDER BY code
            </script>
            """)
    List<Map<String, Object>> listTypes(@Param("scope") String scope);

    @Select("""
            SELECT count(*) FROM hub_dict_type
             WHERE code = #{code} AND deleted = 0 AND status = 1
            """)
    int countType(@Param("code") String code);

    /**
     * 平台默认项（tenant_id=0）与租户自定义项同时返回，由上层按 item_value 覆盖合并。
     */
    @Select("""
            SELECT tenant_id, item_value, item_label, sort, status
              FROM hub_dict_item
             WHERE dict_code = #{dictCode} AND deleted = 0 AND status = 1
               AND (tenant_id = 0 OR tenant_id = #{tenantId})
             ORDER BY sort, item_value
            """)
    List<Map<String, Object>> listItems(@Param("tenantId") long tenantId,
                                        @Param("dictCode") String dictCode);

    @Insert("""
            INSERT INTO hub_dict_item (id, tenant_id, dict_code, item_value, item_label, sort, status,
                                       created_by, created_at, updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{dictCode}, #{itemValue}, #{itemLabel}, #{sort}, 1,
                    #{operatorId,jdbcType=BIGINT}, now(), #{operatorId,jdbcType=BIGINT}, now(), 0, 0)
            ON CONFLICT (tenant_id, dict_code, item_value)
            DO UPDATE SET item_label = EXCLUDED.item_label,
                          sort = EXCLUDED.sort,
                          status = 1,
                          deleted = 0,
                          updated_by = EXCLUDED.updated_by,
                          updated_at = now(),
                          version = hub_dict_item.version + 1
            """)
    int upsertItem(@Param("id") long id,
                   @Param("tenantId") long tenantId,
                   @Param("dictCode") String dictCode,
                   @Param("itemValue") String itemValue,
                   @Param("itemLabel") String itemLabel,
                   @Param("sort") int sort,
                   @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE hub_dict_item
               SET deleted = 1, updated_by = #{operatorId,jdbcType=BIGINT}, updated_at = now(),
                   version = version + 1
             WHERE tenant_id = #{tenantId} AND dict_code = #{dictCode} AND item_value = #{itemValue}
               AND deleted = 0
            """)
    int deleteItem(@Param("tenantId") long tenantId,
                   @Param("dictCode") String dictCode,
                   @Param("itemValue") String itemValue,
                   @Param("operatorId") Long operatorId);
}
