package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 员工 Mapper：PII 仅存密文与哈希，列表/详情均不返回明文。
 */
@Mapper
public interface EmployeeMapper {

    String BASE_SELECT = """
            SELECT e.id AS "id", e.emp_no AS "empNo", e.name AS "name", e.gender AS "gender",
                   to_char(e.birth_date, 'YYYY-MM-DD') AS "birthDate", e.phone_enc AS "phoneEnc",
                   e.id_card_enc AS "idCardEnc", e.email AS "email",
                   e.org_id AS "orgId", o.org_name AS "orgName",
                   e.position_id AS "positionId", p.position_name AS "positionName",
                   e.grade_id AS "gradeId", g.grade_name AS "gradeName",
                   e.manager_id AS "managerId", m.name AS "managerName",
                   e.employment_type AS "employmentType", to_char(e.hire_date, 'YYYY-MM-DD') AS "hireDate",
                   to_char(e.formal_date, 'YYYY-MM-DD') AS "formalDate",
                   to_char(e.leave_date, 'YYYY-MM-DD') AS "leaveDate",
                   e.work_location AS "workLocation", e.emp_status AS "empStatus", e.version AS "version",
                   extract(epoch FROM e.created_at) * 1000 AS "createdAt"
            FROM talent_employee e
            JOIN talent_org o ON o.id = e.org_id AND o.tenant_id = e.tenant_id
            LEFT JOIN talent_position p ON p.id = e.position_id AND p.tenant_id = e.tenant_id
            LEFT JOIN talent_grade g ON g.id = e.grade_id AND g.tenant_id = e.tenant_id
            LEFT JOIN talent_employee m ON m.id = e.manager_id AND m.tenant_id = e.tenant_id
            """;

    @Insert("""
            INSERT INTO talent_employee
                (id, tenant_id, emp_no, name, gender, birth_date, phone_enc, phone_hash, id_card_enc, id_card_hash,
                 email, org_id, position_id, grade_id, manager_id, employment_type, hire_date, work_location,
                 emp_status, created_by)
            VALUES (#{id}, #{tenantId}, #{empNo}, #{name}, #{gender,jdbcType=VARCHAR},
                    #{birthDate,jdbcType=DATE}, #{phoneEnc,jdbcType=VARCHAR}, #{phoneHash,jdbcType=CHAR},
                    #{idCardEnc,jdbcType=VARCHAR}, #{idCardHash,jdbcType=CHAR}, #{email,jdbcType=VARCHAR},
                    #{orgId}, #{positionId,jdbcType=BIGINT}, #{gradeId,jdbcType=BIGINT},
                    #{managerId,jdbcType=BIGINT}, #{employmentType}, #{hireDate}, #{workLocation,jdbcType=VARCHAR},
                    'PENDING', #{userId})
            """)
    void insertEmployee(@Param("id") long id, @Param("tenantId") long tenantId, @Param("empNo") String empNo,
                        @Param("name") String name, @Param("gender") String gender,
                        @Param("birthDate") LocalDate birthDate, @Param("phoneEnc") String phoneEnc,
                        @Param("phoneHash") String phoneHash, @Param("idCardEnc") String idCardEnc,
                        @Param("idCardHash") String idCardHash, @Param("email") String email,
                        @Param("orgId") long orgId, @Param("positionId") Long positionId,
                        @Param("gradeId") Long gradeId, @Param("managerId") Long managerId,
                        @Param("employmentType") String employmentType, @Param("hireDate") LocalDate hireDate,
                        @Param("workLocation") String workLocation, @Param("userId") long userId);

    @Select(BASE_SELECT + """
            WHERE e.tenant_id = #{tenantId} AND e.id = #{id} AND e.deleted = 0
            """)
    Map<String, Object> findEmployee(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select(BASE_SELECT + """
            WHERE e.tenant_id = #{tenantId} AND e.deleted = 0
              AND (#{orgId,jdbcType=BIGINT} IS NULL OR e.org_id = #{orgId})
              AND (CAST(#{pathPrefix,jdbcType=VARCHAR} AS text) IS NULL
                   OR e.org_id IN (SELECT o2.id FROM talent_org o2
                                   WHERE o2.tenant_id = #{tenantId} AND o2.deleted = 0
                                     AND o2.path LIKE #{pathPrefix} || '%'))
              AND (CAST(#{empStatus,jdbcType=VARCHAR} AS text) IS NULL OR e.emp_status = #{empStatus})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR e.name ILIKE '%' || #{keyword} || '%' OR e.emp_no ILIKE '%' || #{keyword} || '%')
            ORDER BY e.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listEmployees(@Param("tenantId") long tenantId,
                                            @Param("orgId") Long orgId,
                                            @Param("pathPrefix") String pathPrefix,
                                            @Param("empStatus") String empStatus,
                                            @Param("keyword") String keyword,
                                            @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_employee e
            WHERE e.tenant_id = #{tenantId} AND e.deleted = 0
              AND (#{orgId,jdbcType=BIGINT} IS NULL OR e.org_id = #{orgId})
              AND (CAST(#{pathPrefix,jdbcType=VARCHAR} AS text) IS NULL
                   OR e.org_id IN (SELECT o2.id FROM talent_org o2
                                   WHERE o2.tenant_id = #{tenantId} AND o2.deleted = 0
                                     AND o2.path LIKE #{pathPrefix} || '%'))
              AND (CAST(#{empStatus,jdbcType=VARCHAR} AS text) IS NULL OR e.emp_status = #{empStatus})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR e.name ILIKE '%' || #{keyword} || '%' OR e.emp_no ILIKE '%' || #{keyword} || '%')
            """)
    long countEmployees(@Param("tenantId") long tenantId, @Param("orgId") Long orgId,
                        @Param("pathPrefix") String pathPrefix,
                        @Param("empStatus") String empStatus, @Param("keyword") String keyword);

    @Select("""
            SELECT count(1) FROM talent_employee
            WHERE tenant_id = #{tenantId} AND phone_hash = #{phoneHash} AND deleted = 0
            """)
    long countByPhoneHash(@Param("tenantId") long tenantId, @Param("phoneHash") String phoneHash);

    @Select("""
            SELECT count(1) FROM talent_employee
            WHERE tenant_id = #{tenantId} AND id_card_hash = #{idCardHash} AND deleted = 0
            """)
    long countByIdCardHash(@Param("tenantId") long tenantId, @Param("idCardHash") String idCardHash);

    @Select("""
            SELECT count(1) FROM talent_employee
            WHERE tenant_id = #{tenantId} AND emp_no = #{empNo} AND deleted = 0
            """)
    long countByEmpNo(@Param("tenantId") long tenantId, @Param("empNo") String empNo);

    @Select("""
            SELECT count(1) FROM talent_employee
            WHERE tenant_id = #{tenantId} AND phone_hash = #{phoneHash} AND deleted = 0
              AND emp_status <> 'RESIGNED'
            """)
    long countActiveByPhoneHash(@Param("tenantId") long tenantId, @Param("phoneHash") String phoneHash);

    @Update("""
            UPDATE talent_employee
            SET name = #{name}, email = #{email,jdbcType=VARCHAR}, org_id = #{orgId},
                position_id = #{positionId,jdbcType=BIGINT}, grade_id = #{gradeId,jdbcType=BIGINT},
                manager_id = #{managerId,jdbcType=BIGINT}, work_location = #{workLocation,jdbcType=VARCHAR},
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND version = #{version} AND deleted = 0
            """)
    int updateEmployee(@Param("tenantId") long tenantId, @Param("id") long id, @Param("name") String name,
                       @Param("email") String email, @Param("orgId") long orgId, @Param("positionId") Long positionId,
                       @Param("gradeId") Long gradeId, @Param("managerId") Long managerId,
                       @Param("workLocation") String workLocation, @Param("version") int version,
                       @Param("userId") long userId);

    @Update("""
            UPDATE talent_employee
            SET org_id = #{orgId}, position_id = #{positionId,jdbcType=BIGINT},
                grade_id = #{gradeId,jdbcType=BIGINT}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateOrgPositionGrade(@Param("tenantId") long tenantId, @Param("id") long id,
                                @Param("orgId") long orgId, @Param("positionId") Long positionId,
                                @Param("gradeId") Long gradeId, @Param("userId") long userId);

    @Update("""
            UPDATE talent_employee
            SET emp_status = #{empStatus}, formal_date = #{formalDate,jdbcType=DATE},
                leave_date = #{leaveDate,jdbcType=DATE}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("empStatus") String empStatus,
                      @Param("formalDate") LocalDate formalDate, @Param("leaveDate") LocalDate leaveDate,
                      @Param("userId") long userId);
}
