package com.fenglin.talent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * C2 培训与知识 Mapper。
 */
@Mapper
public interface TrainingMapper {

    // ---------- 课程 ----------

    @Insert("""
            INSERT INTO talent_training_course
                (id, tenant_id, course_code, title, category, cover_file_id, description, lecturer, mode,
                 duration_hours, credit, course_status, target_audience, created_by)
            VALUES (#{id}, #{tenantId}, #{courseCode}, #{title}, #{category,jdbcType=VARCHAR},
                    #{coverFileId,jdbcType=BIGINT}, #{description,jdbcType=VARCHAR}, #{lecturer,jdbcType=VARCHAR},
                    #{mode}, #{durationHours}, #{credit}, 'DRAFT',
                    CAST(#{targetAudience,jdbcType=VARCHAR} AS jsonb), #{userId})
            """)
    void insertCourse(@Param("id") long id, @Param("tenantId") long tenantId, @Param("courseCode") String courseCode,
                      @Param("title") String title, @Param("category") String category,
                      @Param("coverFileId") Long coverFileId, @Param("description") String description,
                      @Param("lecturer") String lecturer, @Param("mode") String mode,
                      @Param("durationHours") BigDecimal durationHours, @Param("credit") BigDecimal credit,
                      @Param("targetAudience") String targetAudience, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", course_code AS "courseCode", title AS "title", category AS "category",
                   mode AS "mode", duration_hours AS "durationHours", credit AS "credit",
                   course_status AS "courseStatus", version AS "version",
                   coalesce(enrolled_hint, 0) AS "enrolledHint"
            FROM (SELECT c.*, (SELECT coalesce(sum(e.enrolled), 0) FROM talent_course_session e
                               WHERE e.course_id = c.id AND e.tenant_id = c.tenant_id AND e.deleted = 0) AS enrolled_hint
                  FROM talent_training_course c) c
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findCourse(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", course_code AS "courseCode", title AS "title", category AS "category",
                   mode AS "mode", duration_hours AS "durationHours", credit AS "credit",
                   course_status AS "courseStatus"
            FROM talent_training_course
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{category,jdbcType=VARCHAR} AS text) IS NULL OR category = #{category})
              AND (CAST(#{mode,jdbcType=VARCHAR} AS text) IS NULL OR mode = #{mode})
              AND (CAST(#{courseStatus,jdbcType=VARCHAR} AS text) IS NULL OR course_status = #{courseStatus})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR title ILIKE '%' || #{keyword} || '%' OR course_code ILIKE '%' || #{keyword} || '%')
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listCourses(@Param("tenantId") long tenantId, @Param("category") String category,
                                          @Param("mode") String mode, @Param("courseStatus") String courseStatus,
                                          @Param("keyword") String keyword, @Param("limit") int limit,
                                          @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_training_course
            WHERE tenant_id = #{tenantId} AND deleted = 0
              AND (CAST(#{category,jdbcType=VARCHAR} AS text) IS NULL OR category = #{category})
              AND (CAST(#{mode,jdbcType=VARCHAR} AS text) IS NULL OR mode = #{mode})
              AND (CAST(#{courseStatus,jdbcType=VARCHAR} AS text) IS NULL OR course_status = #{courseStatus})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR title ILIKE '%' || #{keyword} || '%' OR course_code ILIKE '%' || #{keyword} || '%')
            """)
    long countCourses(@Param("tenantId") long tenantId, @Param("category") String category,
                      @Param("mode") String mode, @Param("courseStatus") String courseStatus,
                      @Param("keyword") String keyword);

    @Update("""
            UPDATE talent_training_course
            SET course_status = #{courseStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateCourseStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("courseStatus") String courseStatus, @Param("userId") long userId);

    // ---------- 班次 ----------

    @Insert("""
            INSERT INTO talent_course_session
                (id, tenant_id, course_id, start_at, end_at, capacity, enrolled, enroll_deadline, venue,
                 session_status, created_by)
            VALUES (#{id}, #{tenantId}, #{courseId}, #{startAt}, #{endAt}, #{capacity}, 0,
                    #{enrollDeadline,jdbcType=TIMESTAMP_WITH_TIMEZONE}, #{venue,jdbcType=VARCHAR}, 'OPEN', #{userId})
            """)
    void insertSession(@Param("id") long id, @Param("tenantId") long tenantId, @Param("courseId") long courseId,
                       @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
                       @Param("capacity") int capacity, @Param("enrollDeadline") OffsetDateTime enrollDeadline,
                       @Param("venue") String venue, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", course_id AS "courseId", capacity AS "capacity", enrolled AS "enrolled",
                   extract(epoch FROM enroll_deadline) * 1000 AS "enrollDeadline",
                   session_status AS "sessionStatus", version AS "version"
            FROM talent_course_session
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findSession(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_course_session
            SET enrolled = greatest(enrolled + #{delta}, 0), updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void addEnrolled(@Param("tenantId") long tenantId, @Param("id") long id, @Param("delta") int delta,
                     @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", course_id AS "courseId", capacity AS "capacity", enrolled AS "enrolled",
                   extract(epoch FROM start_at) * 1000 AS "startAt",
                   extract(epoch FROM end_at) * 1000 AS "endAt",
                   extract(epoch FROM enroll_deadline) * 1000 AS "enrollDeadline",
                   venue AS "venue", session_status AS "sessionStatus"
            FROM talent_course_session
            WHERE tenant_id = #{tenantId} AND course_id = #{courseId} AND deleted = 0
              AND (CAST(#{sessionStatus,jdbcType=VARCHAR} AS text) IS NULL
                   OR session_status = #{sessionStatus})
            ORDER BY start_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listSessions(@Param("tenantId") long tenantId, @Param("courseId") long courseId,
                                           @Param("sessionStatus") String sessionStatus, @Param("limit") int limit,
                                           @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_course_session
            WHERE tenant_id = #{tenantId} AND course_id = #{courseId} AND deleted = 0
              AND (CAST(#{sessionStatus,jdbcType=VARCHAR} AS text) IS NULL
                   OR session_status = #{sessionStatus})
            """)
    long countSessions(@Param("tenantId") long tenantId, @Param("courseId") long courseId,
                       @Param("sessionStatus") String sessionStatus);

    // ---------- 报名 / 学习记录 ----------

    @Insert("""
            INSERT INTO talent_learning_record
                (id, tenant_id, employee_id, course_id, session_id, progress, learn_status, study_seconds,
                 enroll_status, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{courseId}, #{sessionId,jdbcType=BIGINT}, 0,
                    'NOT_STARTED', 0, #{enrollStatus}, #{userId})
            """)
    void insertLearning(@Param("id") long id, @Param("tenantId") long tenantId,
                        @Param("employeeId") long employeeId, @Param("courseId") long courseId,
                        @Param("sessionId") Long sessionId, @Param("enrollStatus") String enrollStatus,
                        @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", employee_id AS "employeeId", course_id AS "courseId", session_id AS "sessionId",
                   progress AS "progress", learn_status AS "learnStatus", score AS "score",
                   study_seconds AS "studySeconds", enroll_status AS "enrollStatus", version AS "version",
                   extract(epoch FROM completed_at) * 1000 AS "completedAt",
                   extract(epoch FROM created_at) * 1000 AS "createdAt"
            FROM talent_learning_record
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findLearning(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            SELECT id AS "id", enroll_status AS "enrollStatus"
            FROM talent_learning_record
            WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND session_id = #{sessionId}
              AND deleted = 0 AND enroll_status <> 'CANCELED'
            """)
    Map<String, Object> findEnrollment(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId,
                                       @Param("sessionId") long sessionId);

    @Select("""
            SELECT count(1) FROM talent_learning_record
            WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND enroll_status = 'WAITLIST'
              AND deleted = 0
            """)
    long countWaitlist(@Param("tenantId") long tenantId, @Param("sessionId") long sessionId);

    @Select("""
            SELECT id AS "id", employee_id AS "employeeId"
            FROM talent_learning_record
            WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND enroll_status = 'WAITLIST'
              AND deleted = 0
            ORDER BY created_at, id
            LIMIT 1
            """)
    Map<String, Object> findFirstWaitlist(@Param("tenantId") long tenantId, @Param("sessionId") long sessionId);

    @Update("""
            UPDATE talent_learning_record
            SET enroll_status = #{enrollStatus}, updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateEnrollStatus(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("enrollStatus") String enrollStatus, @Param("userId") long userId);

    @Select("""
            SELECT r.id AS "id", r.course_id AS "courseId", c.course_code AS "courseCode",
                   c.title AS "courseTitle", r.session_id AS "sessionId", r.progress AS "progress",
                   r.learn_status AS "learnStatus", r.enroll_status AS "enrollStatus", r.score AS "score",
                   extract(epoch FROM s.start_at) * 1000 AS "startAt",
                   extract(epoch FROM s.end_at) * 1000 AS "endAt",
                   s.venue AS "venue",
                   extract(epoch FROM r.completed_at) * 1000 AS "completedAt",
                   extract(epoch FROM r.created_at) * 1000 AS "createdAt"
            FROM talent_learning_record r
            JOIN talent_training_course c ON c.id = r.course_id AND c.tenant_id = r.tenant_id AND c.deleted = 0
            LEFT JOIN talent_course_session s ON s.id = r.session_id AND s.tenant_id = r.tenant_id
                 AND s.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.employee_id = #{employeeId} AND r.deleted = 0
              AND (CAST(#{enrollStatus,jdbcType=VARCHAR} AS text) IS NULL
                   OR r.enroll_status = #{enrollStatus})
            ORDER BY r.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listMyEnrollments(@Param("tenantId") long tenantId,
                                                @Param("employeeId") long employeeId,
                                                @Param("enrollStatus") String enrollStatus,
                                                @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1)
            FROM talent_learning_record r
            JOIN talent_training_course c ON c.id = r.course_id AND c.tenant_id = r.tenant_id AND c.deleted = 0
            WHERE r.tenant_id = #{tenantId} AND r.employee_id = #{employeeId} AND r.deleted = 0
              AND (CAST(#{enrollStatus,jdbcType=VARCHAR} AS text) IS NULL
                   OR r.enroll_status = #{enrollStatus})
            """)
    long countMyEnrollments(@Param("tenantId") long tenantId, @Param("employeeId") long employeeId,
                            @Param("enrollStatus") String enrollStatus);

    @Update("""
            UPDATE talent_learning_record
            SET progress = greatest(progress, #{progress}), study_seconds = greatest(study_seconds, #{studySeconds}),
                learn_status = #{learnStatus}, completed_at = #{completedAt,jdbcType=TIMESTAMP_WITH_TIMEZONE},
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateLearningProgress(@Param("tenantId") long tenantId, @Param("id") long id,
                                @Param("progress") int progress, @Param("studySeconds") int studySeconds,
                                @Param("learnStatus") String learnStatus,
                                @Param("completedAt") OffsetDateTime completedAt, @Param("userId") long userId);

    @Update("""
            UPDATE talent_learning_record
            SET score = #{score}, learn_status = #{learnStatus}, completed_at = #{completedAt},
                updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void updateLearningExam(@Param("tenantId") long tenantId, @Param("id") long id,
                            @Param("score") BigDecimal score, @Param("learnStatus") String learnStatus,
                            @Param("completedAt") OffsetDateTime completedAt, @Param("userId") long userId);

    // ---------- 证书 ----------

    @Insert("""
            INSERT INTO talent_certificate
                (id, tenant_id, employee_id, course_id, cert_no, cert_name, issue_date, expire_date, created_by)
            VALUES (#{id}, #{tenantId}, #{employeeId}, #{courseId,jdbcType=BIGINT}, #{certNo}, #{certName},
                    #{issueDate}, #{expireDate,jdbcType=DATE}, #{userId})
            """)
    void insertCertificate(@Param("id") long id, @Param("tenantId") long tenantId,
                           @Param("employeeId") long employeeId, @Param("courseId") Long courseId,
                           @Param("certNo") String certNo, @Param("certName") String certName,
                           @Param("issueDate") LocalDate issueDate, @Param("expireDate") LocalDate expireDate,
                           @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", cert_no AS "certNo", cert_name AS "certName",
                   to_char(issue_date, 'YYYY-MM-DD') AS "issueDate",
                   to_char(expire_date, 'YYYY-MM-DD') AS "expireDate"
            FROM talent_certificate
            WHERE tenant_id = #{tenantId} AND employee_id = #{employeeId} AND deleted = 0
            ORDER BY issue_date DESC
            """)
    List<Map<String, Object>> listCertificates(@Param("tenantId") long tenantId,
                                               @Param("employeeId") long employeeId);

    // ---------- 知识分类 / 文章 ----------

    @Insert("""
            INSERT INTO talent_knowledge_category
                (id, tenant_id, parent_id, name, sort_no, created_by)
            VALUES (#{id}, #{tenantId}, #{parentId,jdbcType=BIGINT}, #{name}, #{sortNo}, #{userId})
            """)
    void insertCategory(@Param("id") long id, @Param("tenantId") long tenantId, @Param("parentId") Long parentId,
                        @Param("name") String name, @Param("sortNo") int sortNo, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", name AS "name", parent_id AS "parentId", sort_no AS "sortNo"
            FROM talent_knowledge_category
            WHERE tenant_id = #{tenantId} AND deleted = 0
            ORDER BY sort_no, id
            """)
    List<Map<String, Object>> listCategories(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO talent_knowledge_article
                (id, tenant_id, title, category_id, content, author_id, tags, art_status, art_version, created_by)
            VALUES (#{id}, #{tenantId}, #{title}, #{categoryId,jdbcType=BIGINT}, #{content,jdbcType=VARCHAR},
                    #{authorId}, CAST(#{tags,jdbcType=VARCHAR} AS jsonb), 'DRAFT', 1, #{userId})
            """)
    void insertArticle(@Param("id") long id, @Param("tenantId") long tenantId, @Param("title") String title,
                       @Param("categoryId") Long categoryId, @Param("content") String content,
                       @Param("authorId") long authorId, @Param("tags") String tags, @Param("userId") long userId);

    @Select("""
            SELECT id AS "id", title AS "title", category_id AS "categoryId", author_id AS "authorId",
                   art_status AS "artStatus", art_version AS "artVersion", version AS "version"
            FROM talent_knowledge_article
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    Map<String, Object> findArticle(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE talent_knowledge_article
            SET art_status = 'REVIEW', updated_by = #{userId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void submitArticle(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Update("""
            UPDATE talent_knowledge_article
            SET art_status = 'PUBLISHED', art_version = art_version + 1, updated_by = #{userId},
                version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = 0
            """)
    void publishArticle(@Param("tenantId") long tenantId, @Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT a.id AS "id", a.title AS "title", c.name AS "category", e.name AS "author",
                   a.view_count AS "viewCount",
                   extract(epoch FROM coalesce(a.updated_at, a.created_at)) * 1000 AS "publishedAt"
            FROM talent_knowledge_article a
            LEFT JOIN talent_knowledge_category c ON c.id = a.category_id AND c.tenant_id = a.tenant_id
            LEFT JOIN talent_employee e ON e.id = a.author_id AND e.tenant_id = a.tenant_id
            WHERE a.tenant_id = #{tenantId} AND a.deleted = 0 AND a.art_status = 'PUBLISHED'
              AND (#{categoryId,jdbcType=BIGINT} IS NULL OR a.category_id = #{categoryId})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR a.title ILIKE '%' || #{keyword} || '%' OR a.content ILIKE '%' || #{keyword} || '%'
                   OR a.tags::text ILIKE '%' || #{keyword} || '%')
            ORDER BY a.updated_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> listArticles(@Param("tenantId") long tenantId,
                                           @Param("categoryId") Long categoryId,
                                           @Param("keyword") String keyword,
                                           @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT count(1) FROM talent_knowledge_article a
            WHERE a.tenant_id = #{tenantId} AND a.deleted = 0 AND a.art_status = 'PUBLISHED'
              AND (#{categoryId,jdbcType=BIGINT} IS NULL OR a.category_id = #{categoryId})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS text) IS NULL
                   OR a.title ILIKE '%' || #{keyword} || '%' OR a.content ILIKE '%' || #{keyword} || '%'
                   OR a.tags::text ILIKE '%' || #{keyword} || '%')
            """)
    long countArticles(@Param("tenantId") long tenantId, @Param("categoryId") Long categoryId,
                       @Param("keyword") String keyword);
}
