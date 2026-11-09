package com.fenglin.rnd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * E1 产品、成员、需求状态定义 Mapper。
 */
@Mapper
public interface ProductMapper {

    @Insert("""
            INSERT INTO devops_product
                (id, tenant_id, product_code, name, product_line, owner_id, visibility, sprint_cycle_days,
                 description, status, created_by)
            VALUES (#{id}, #{tenantId}, #{productCode}, #{name}, #{productLine,jdbcType=VARCHAR},
                    #{ownerId}, #{visibility}, #{sprintCycleDays}, #{description,jdbcType=VARCHAR},
                    'active', #{userId})
            """)
    void insertProduct(@Param("id") long id, @Param("tenantId") long tenantId,
                       @Param("productCode") String productCode, @Param("name") String name,
                       @Param("productLine") String productLine, @Param("ownerId") long ownerId,
                       @Param("visibility") String visibility, @Param("sprintCycleDays") int sprintCycleDays,
                       @Param("description") String description, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", product_code AS "productCode", name AS "name",
                   product_line AS "productLine", owner_id AS "ownerId", visibility AS "visibility",
                   status AS "status", sprint_cycle_days AS "sprintCycleDays",
                   description AS "description", created_at::text AS "createdAt"
            FROM devops_product
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findProduct(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT id AS "id", product_code AS "productCode", name AS "name",
                   product_line AS "productLine", owner_id AS "ownerId", visibility AS "visibility",
                   status AS "status", sprint_cycle_days AS "sprintCycleDays",
                   description AS "description", created_at::text AS "createdAt"
            FROM devops_product
            WHERE tenant_id = #{tenantId} AND deleted = 0
              <if test="status != null">AND status = #{status}</if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> listProducts(@Param("tenantId") long tenantId, @Param("status") String status,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(1) FROM devops_product WHERE tenant_id = #{tenantId} AND product_code = #{code} "
            + "AND deleted = 0")
    long countProductCode(@Param("tenantId") long tenantId, @Param("code") String code);

    @Insert("""
            INSERT INTO devops_product_member (id, tenant_id, product_id, user_id, role_code, data_scope,
                                               created_by)
            VALUES (#{id}, #{tenantId}, #{productId}, #{userId}, #{roleCode}, #{dataScope}, #{operatorId})
            """)
    void insertMember(@Param("id") long id, @Param("tenantId") long tenantId, @Param("productId") long productId,
                      @Param("userId") long userId, @Param("roleCode") String roleCode,
                      @Param("dataScope") String dataScope, @Param("operatorId") long operatorId);

    @Select("""
            SELECT id AS "id", product_id AS "productId", user_id AS "userId", role_code AS "roleCode",
                   data_scope AS "dataScope", created_at::text AS "createdAt"
            FROM devops_product_member
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND deleted = 0
            ORDER BY id
            """)
    List<Map<String, Object>> listMembers(@Param("tenantId") long tenantId, @Param("productId") long productId);

    @Select("SELECT count(1) FROM devops_product_member WHERE tenant_id = #{tenantId} "
            + "AND product_id = #{productId} AND user_id = #{userId} AND deleted = 0")
    long countMember(@Param("tenantId") long tenantId, @Param("productId") long productId,
                     @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", status_code AS "statusCode", status_name AS "statusName",
                   category AS "category", color AS "color", sort_no AS "sortNo"
            FROM devops_requirement_status
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND deleted = 0
            ORDER BY sort_no
            """)
    List<Map<String, Object>> listStatuses(@Param("tenantId") long tenantId, @Param("productId") long productId);

    @Select("""
            SELECT id AS "id", status_code AS "statusCode", status_name AS "statusName",
                   category AS "category", color AS "color", sort_no AS "sortNo"
            FROM devops_requirement_status
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND status_code = #{code} AND deleted = 0
            """)
    Map<String, Object> findStatus(@Param("tenantId") long tenantId, @Param("productId") long productId,
                                   @Param("code") String code);

    @Select("SELECT count(1) FROM devops_product WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0")
    long countProduct(@Param("tenantId") long tenantId, @Param("id") long id);
}
