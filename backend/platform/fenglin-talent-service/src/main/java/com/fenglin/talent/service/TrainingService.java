package com.fenglin.talent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.PageResult;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.mapper.TrainingMapper;
import com.fenglin.talent.support.Rows;
import com.fenglin.talent.support.TalentAudit;
import com.fenglin.talent.support.TalentContext;
import com.fenglin.talent.web.dto.TrainingDtos.ArticleCreateRequest;
import com.fenglin.talent.web.dto.TrainingDtos.ArticleCreateResult;
import com.fenglin.talent.web.dto.TrainingDtos.ArticleListItem;
import com.fenglin.talent.web.dto.TrainingDtos.ArticleStatusResult;
import com.fenglin.talent.web.dto.TrainingDtos.CategoryCreateRequest;
import com.fenglin.talent.web.dto.TrainingDtos.CategoryView;
import com.fenglin.talent.web.dto.TrainingDtos.CertificateView;
import com.fenglin.talent.web.dto.TrainingDtos.CourseCreateRequest;
import com.fenglin.talent.web.dto.TrainingDtos.CourseCreateResult;
import com.fenglin.talent.web.dto.TrainingDtos.CourseListItem;
import com.fenglin.talent.web.dto.TrainingDtos.CourseStatusResult;
import com.fenglin.talent.web.dto.TrainingDtos.EnrollmentCancelResult;
import com.fenglin.talent.web.dto.TrainingDtos.EnrollmentCreateRequest;
import com.fenglin.talent.web.dto.TrainingDtos.EnrollmentCreateResult;
import com.fenglin.talent.web.dto.TrainingDtos.ExamSubmitRequest;
import com.fenglin.talent.web.dto.TrainingDtos.ExamSubmitResult;
import com.fenglin.talent.web.dto.TrainingDtos.LearningProgressRequest;
import com.fenglin.talent.web.dto.TrainingDtos.LearningProgressResult;
import com.fenglin.talent.web.dto.TrainingDtos.MyEnrollmentView;
import com.fenglin.talent.web.dto.TrainingDtos.SessionCreateRequest;
import com.fenglin.talent.web.dto.TrainingDtos.SessionCreateResult;
import com.fenglin.talent.web.dto.TrainingDtos.SessionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C2 培训与知识：课程、班次报名与候补、学习进度、考试证书、知识库。
 */
@Service
public class TrainingService {

    private static final Set<String> MODES = Set.of("ONLINE", "OFFLINE", "BLENDED");

    private final TrainingMapper trainingMapper;
    private final TalentAudit audit;
    private final ObjectMapper objectMapper;
    private final EmployeeBindingService employeeBindingService;

    public TrainingService(TrainingMapper trainingMapper, TalentAudit audit, ObjectMapper objectMapper,
                           EmployeeBindingService employeeBindingService) {
        this.trainingMapper = trainingMapper;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.employeeBindingService = employeeBindingService;
    }

    // ---------- 课程 ----------

    @Transactional
    public CourseCreateResult createCourse(CourseCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || isBlank(request.courseCode()) || isBlank(request.title())
                || isBlank(request.mode())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "课程编码、名称与授课形式不能为空");
        }
        if (!MODES.contains(request.mode())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "授课形式必须为 ONLINE/OFFLINE/BLENDED");
        }
        long id = IdWorker.getId();
        trainingMapper.insertCourse(id, tenantId, request.courseCode(), request.title(), request.category(),
                request.coverFileId(), request.description(), request.lecturer(), request.mode(),
                request.durationHours(), request.credit(), toJson(request.targetAudience()), userId);
        audit.publish("TALENT_COURSE_CREATED", "CREATE", "talent_training_course", id, "LOW");
        return new CourseCreateResult(id, "DRAFT");
    }

    @Transactional(readOnly = true)
    public PageResult<CourseListItem> listCourses(String category, String mode, String courseStatus,
                                                 String keyword, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<CourseListItem> records = trainingMapper.listCourses(tenantId, category, mode, courseStatus, keyword,
                        pageSize, (pageNo - 1) * pageSize)
                .stream()
                .map(row -> new CourseListItem(Rows.lngVal(row, "id"), Rows.str(row, "courseCode"),
                        Rows.str(row, "title"), Rows.str(row, "category"), Rows.str(row, "mode"),
                        Rows.dec(row, "durationHours"), Rows.dec(row, "credit"), Rows.str(row, "courseStatus")))
                .toList();
        long total = trainingMapper.countCourses(tenantId, category, mode, courseStatus, keyword);
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Transactional
    public CourseStatusResult changeCourseStatus(long id, String targetStatus) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> course = requireCourse(tenantId, id);
        String current = Rows.str(course, "courseStatus");
        if ("PUBLISHED".equals(targetStatus)) {
            if (!"DRAFT".equals(current) && !"OFFLINE".equals(current)) {
                throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅草稿或已下架课程可发布");
            }
        } else if ("OFFLINE".equals(targetStatus)) {
            if (!"PUBLISHED".equals(current)) {
                throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅已发布课程可下架");
            }
        } else {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "目标状态必须为 PUBLISHED/OFFLINE");
        }
        trainingMapper.updateCourseStatus(tenantId, id, targetStatus, userId);
        audit.publish("TALENT_COURSE_STATUS_CHANGED", "UPDATE", "talent_training_course", id, "LOW");
        return new CourseStatusResult(id, targetStatus);
    }

    // ---------- 班次 ----------

    @Transactional
    public SessionCreateResult createSession(long courseId, SessionCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        requireCourse(tenantId, courseId);
        if (request == null || request.startAt() == null || request.endAt() == null
                || request.capacity() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "开始时间、结束时间与容量不能为空");
        }
        if (request.capacity() <= 0) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "班次容量必须大于 0");
        }
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        long id = IdWorker.getId();
        trainingMapper.insertSession(id, tenantId, courseId, request.startAt(), request.endAt(),
                request.capacity(), request.enrollDeadline(), request.venue(), userId);
        audit.publish("TALENT_COURSE_SESSION_CREATED", "CREATE", "talent_course_session", id, "LOW");
        return new SessionCreateResult(id, "OPEN");
    }

    @Transactional(readOnly = true)
    public PageResult<SessionView> listSessions(long courseId, String sessionStatus, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        requireCourse(tenantId, courseId);
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<SessionView> records = trainingMapper.listSessions(tenantId, courseId, sessionStatus, pageSize,
                        (pageNo - 1) * pageSize)
                .stream()
                .map(row -> new SessionView(Rows.lngVal(row, "id"), Rows.lngVal(row, "courseId"),
                        Rows.lng(row, "startAt"), Rows.lng(row, "endAt"), Rows.intVal(row, "capacity"),
                        Rows.intVal(row, "enrolled"), Rows.lng(row, "enrollDeadline"), Rows.str(row, "venue"),
                        Rows.str(row, "sessionStatus")))
                .toList();
        long total = trainingMapper.countSessions(tenantId, courseId, sessionStatus);
        return PageResult.of(records, total, pageNo, pageSize);
    }

    // ---------- 报名 / 候补 ----------

    @Transactional(noRollbackFor = BizException.class)
    public EnrollmentCreateResult enroll(EnrollmentCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || request.courseId() == null || request.sessionId() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "课程与班次不能为空");
        }
        Long targetEmployeeId = request.employeeId() == null
                ? employeeBindingService.currentEmployeeIdOrNull() : request.employeeId();
        long employeeId = targetEmployeeId == null ? userId : targetEmployeeId;
        Map<String, Object> session = trainingMapper.findSession(tenantId, request.sessionId());
        if (session == null || Rows.lngVal(session, "courseId") != request.courseId()) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "班次不存在或不属于该课程");
        }
        Long deadline = Rows.lng(session, "enrollDeadline");
        if (deadline != null && System.currentTimeMillis() > deadline) {
            throw new BizException(TalentErrorCode.ENROLL_DEADLINE_PASSED);
        }
        if (trainingMapper.findEnrollment(tenantId, employeeId, request.sessionId()) != null) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "已报名该班次，请勿重复提交");
        }
        long id = IdWorker.getId();
        int capacity = Rows.intVal(session, "capacity");
        int enrolled = Rows.intVal(session, "enrolled");
        if (enrolled < capacity) {
            trainingMapper.insertLearning(id, tenantId, employeeId, request.courseId(), request.sessionId(),
                    "ENROLLED", userId);
            trainingMapper.addEnrolled(tenantId, request.sessionId(), 1, userId);
            audit.publish("TALENT_COURSE_ENROLLED", "CREATE", "talent_learning_record", id, "LOW");
            return new EnrollmentCreateResult(id, "ENROLLED", false, null);
        }
        trainingMapper.insertLearning(id, tenantId, employeeId, request.courseId(), request.sessionId(),
                "WAITLIST", userId);
        int waitlistNo = (int) trainingMapper.countWaitlist(tenantId, request.sessionId());
        audit.publish("TALENT_COURSE_WAITLISTED", "CREATE", "talent_learning_record", id, "LOW");
        throw new BizException(TalentErrorCode.SESSION_FULL, "班次已满，候补位次 " + waitlistNo);
    }

    @Transactional
    public EnrollmentCancelResult cancelEnrollment(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> learning = trainingMapper.findLearning(tenantId, id);
        if (learning == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "报名记录不存在");
        }
        String enrollStatus = Rows.str(learning, "enrollStatus");
        if (!"ENROLLED".equals(enrollStatus) && !"WAITLIST".equals(enrollStatus)) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "当前报名状态不可取消");
        }
        Long sessionId = Rows.lng(learning, "sessionId");
        trainingMapper.updateEnrollStatus(tenantId, id, "CANCELED", userId);
        Long promoted = null;
        if ("ENROLLED".equals(enrollStatus) && sessionId != null) {
            trainingMapper.addEnrolled(tenantId, sessionId, -1, userId);
            Map<String, Object> first = trainingMapper.findFirstWaitlist(tenantId, sessionId);
            if (first != null) {
                promoted = Rows.lngVal(first, "employeeId");
                trainingMapper.updateEnrollStatus(tenantId, Rows.lngVal(first, "id"), "ENROLLED", userId);
                trainingMapper.addEnrolled(tenantId, sessionId, 1, userId);
            }
        }
        audit.publish("TALENT_COURSE_ENROLL_CANCELED", "UPDATE", "talent_learning_record", id, "LOW");
        return new EnrollmentCancelResult(id, "CANCELED", promoted);
    }

    @Transactional(readOnly = true)
    public PageResult<MyEnrollmentView> myEnrollments(String enrollStatus, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        Long employeeId = employeeBindingService.currentEmployeeIdOrNull();
        if (employeeId == null) {
            return PageResult.of(List.of(), 0, pageNo, pageSize);
        }
        List<MyEnrollmentView> records = trainingMapper.listMyEnrollments(tenantId, employeeId, enrollStatus,
                        pageSize, (pageNo - 1) * pageSize)
                .stream()
                .map(row -> new MyEnrollmentView(Rows.lngVal(row, "id"), Rows.lngVal(row, "courseId"),
                        Rows.str(row, "courseCode"), Rows.str(row, "courseTitle"), Rows.lng(row, "sessionId"),
                        Rows.lng(row, "startAt"), Rows.lng(row, "endAt"), Rows.str(row, "venue"),
                        Rows.intVal(row, "progress"), Rows.str(row, "learnStatus"), Rows.str(row, "enrollStatus"),
                        Rows.dec(row, "score"), Rows.lng(row, "completedAt"), Rows.lng(row, "createdAt")))
                .toList();
        long total = trainingMapper.countMyEnrollments(tenantId, employeeId, enrollStatus);
        return PageResult.of(records, total, pageNo, pageSize);
    }

    // ---------- 学习进度 / 考试 ----------

    @Transactional
    public LearningProgressResult reportProgress(long id, LearningProgressRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> learning = trainingMapper.findLearning(tenantId, id);
        if (learning == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "学习记录不存在");
        }
        if (request == null || request.progress() == null) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "progress 不能为空");
        }
        if (request.progress() < 0 || request.progress() > 100) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "progress 必须为 0-100");
        }
        int progress = Math.max(Rows.intVal(learning, "progress"), request.progress());
        int studySeconds = Math.max(Rows.intVal(learning, "studySeconds"),
                request.studySeconds() == null ? 0 : request.studySeconds());
        String learnStatus = progress >= 100 ? "DONE" : "LEARNING";
        OffsetDateTime completedAt = progress >= 100 ? OffsetDateTime.now(ZoneOffset.UTC) : null;
        trainingMapper.updateLearningProgress(tenantId, id, progress, studySeconds, learnStatus, completedAt,
                userId);
        audit.publish("TALENT_LEARNING_PROGRESS_REPORTED", "UPDATE", "talent_learning_record", id, "LOW");
        return new LearningProgressResult(id, progress, learnStatus);
    }

    @Transactional
    public ExamSubmitResult submitExam(long id, ExamSubmitRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> learning = trainingMapper.findLearning(tenantId, id);
        if (learning == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "学习记录不存在");
        }
        BigDecimal score = request == null ? null : request.score();
        BigDecimal storedScore = Rows.dec(learning, "score");
        if (storedScore != null && storedScore.compareTo(BigDecimal.valueOf(60)) >= 0) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "该课程考试已通过");
        }
        if (score == null) {
            score = scoreFromAnswers(request == null ? null : request.answers());
        }
        boolean passed = score.compareTo(BigDecimal.valueOf(60)) >= 0;
        String learnStatus;
        if (passed) {
            learnStatus = "DONE";
        } else if (storedScore != null) {
            learnStatus = "FAILED";
        } else {
            learnStatus = "LEARNING";
        }
        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        trainingMapper.updateLearningExam(tenantId, id, score, learnStatus, completedAt, userId);
        if (passed) {
            long courseId = Rows.lngVal(learning, "courseId");
            Map<String, Object> course = trainingMapper.findCourse(tenantId, courseId);
            String courseTitle = course == null ? "课程" : Rows.str(course, "title");
            long certId = IdWorker.getId();
            trainingMapper.insertCertificate(certId, tenantId, Rows.lngVal(learning, "employeeId"), courseId,
                    "CERT" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + tail(certId),
                    courseTitle + " 培训证书", LocalDate.now(), LocalDate.now().plusYears(3), userId);
        }
        audit.publish("TALENT_EXAM_SUBMITTED", "UPDATE", "talent_learning_record", id, "LOW");
        return new ExamSubmitResult(score, passed, learnStatus);
    }

    @Transactional(readOnly = true)
    public List<CertificateView> myCertificates(Long employeeId) {
        long tenantId = TalentContext.tenantId();
        Long target = employeeId == null ? employeeBindingService.currentEmployeeIdOrNull() : employeeId;
        if (target == null) {
            return List.of();
        }
        return trainingMapper.listCertificates(tenantId, target).stream()
                .map(row -> new CertificateView(Rows.lngVal(row, "id"), Rows.str(row, "certNo"),
                        Rows.str(row, "certName"), Rows.date(row, "issueDate"), Rows.date(row, "expireDate")))
                .toList();
    }

    // ---------- 知识库 ----------

    @Transactional
    public CategoryView createCategory(CategoryCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || isBlank(request.name())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "分类名称不能为空");
        }
        long id = IdWorker.getId();
        trainingMapper.insertCategory(id, tenantId, request.parentId(), request.name(),
                request.sortNo() == null ? 0 : request.sortNo(), userId);
        audit.publish("TALENT_KNOWLEDGE_CATEGORY_CREATED", "CREATE", "talent_knowledge_category", id, "LOW");
        return new CategoryView(id, request.name(), request.parentId(),
                request.sortNo() == null ? 0 : request.sortNo(), List.of());
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listCategories() {
        long tenantId = TalentContext.tenantId();
        List<Map<String, Object>> rows = trainingMapper.listCategories(tenantId);
        Map<Long, List<Map<String, Object>>> byParent = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byParent.computeIfAbsent(Rows.lng(row, "parentId"), key -> new ArrayList<>()).add(row);
        }
        return buildCategoryTree(byParent, null);
    }

    @Transactional
    public ArticleCreateResult createArticle(ArticleCreateRequest request) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        if (request == null || isBlank(request.title()) || isBlank(request.content())) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "标题与正文不能为空");
        }
        long id = IdWorker.getId();
        trainingMapper.insertArticle(id, tenantId, request.title(), request.categoryId(), request.content(),
                userId, toJson(request.tags()), userId);
        audit.publish("TALENT_KNOWLEDGE_ARTICLE_CREATED", "CREATE", "talent_knowledge_article", id, "LOW");
        return new ArticleCreateResult(id, "DRAFT");
    }

    @Transactional
    public ArticleStatusResult submitArticle(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> article = requireArticle(tenantId, id);
        if (!"DRAFT".equals(Rows.str(article, "artStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "仅草稿文章可提交审核");
        }
        trainingMapper.submitArticle(tenantId, id, userId);
        audit.publish("TALENT_KNOWLEDGE_ARTICLE_SUBMITTED", "UPDATE", "talent_knowledge_article", id, "LOW");
        return new ArticleStatusResult(id, "REVIEW", Rows.intVal(article, "artVersion"));
    }

    @Transactional
    public ArticleStatusResult publishArticle(long id) {
        long tenantId = TalentContext.tenantId();
        long userId = TalentContext.userId();
        Map<String, Object> article = requireArticle(tenantId, id);
        if ("PUBLISHED".equals(Rows.str(article, "artStatus"))) {
            throw new BizException(TalentErrorCode.STATE_CONFLICT, "文章已发布");
        }
        trainingMapper.publishArticle(tenantId, id, userId);
        audit.publish("TALENT_KNOWLEDGE_ARTICLE_PUBLISHED", "UPDATE", "talent_knowledge_article", id, "LOW");
        return new ArticleStatusResult(id, "PUBLISHED", Rows.intVal(article, "artVersion") + 1);
    }

    @Transactional(readOnly = true)
    public PageResult<ArticleListItem> searchArticles(Long categoryId, String keyword, Integer page, Integer size) {
        long tenantId = TalentContext.tenantId();
        int pageNo = TalentContext.page(page);
        int pageSize = TalentContext.size(size);
        List<ArticleListItem> records = trainingMapper.listArticles(tenantId, categoryId, keyword, pageSize,
                        (pageNo - 1) * pageSize).stream()
                .map(row -> new ArticleListItem(Rows.lngVal(row, "id"), Rows.str(row, "title"),
                        Rows.str(row, "category"), Rows.str(row, "author"), Rows.intVal(row, "viewCount"),
                        Rows.instant(row, "publishedAt")))
                .toList();
        long total = trainingMapper.countArticles(tenantId, categoryId, keyword);
        return PageResult.of(records, total, pageNo, pageSize);
    }

    // ---------- helpers ----------

    private List<CategoryView> buildCategoryTree(Map<Long, List<Map<String, Object>>> byParent, Long parentId) {
        List<CategoryView> views = new ArrayList<>();
        for (Map<String, Object> row : byParent.getOrDefault(parentId, List.of())) {
            long id = Rows.lngVal(row, "id");
            views.add(new CategoryView(id, Rows.str(row, "name"), Rows.lng(row, "parentId"),
                    Rows.intVal(row, "sortNo"), buildCategoryTree(byParent, id)));
        }
        return views;
    }

    private Map<String, Object> requireCourse(long tenantId, long id) {
        Map<String, Object> course = trainingMapper.findCourse(tenantId, id);
        if (course == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "课程不存在");
        }
        return course;
    }

    private Map<String, Object> requireArticle(long tenantId, long id) {
        Map<String, Object> article = trainingMapper.findArticle(tenantId, id);
        if (article == null) {
            throw new BizException(TalentErrorCode.RESOURCE_NOT_FOUND, "知识文章不存在");
        }
        return article;
    }

    private BigDecimal scoreFromAnswers(Object answers) {
        if (!(answers instanceof List<?> list) || list.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (Object item : list) {
            Object value = item instanceof Map<?, ?> map ? map.get("value") : item;
            if (value instanceof Number number) {
                sum = sum.add(new BigDecimal(number.toString()));
                count++;
            }
        }
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return sum.multiply(BigDecimal.valueOf(20))
                .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private String tail(long id) {
        String value = Long.toString(id);
        return value.length() <= 6 ? value : value.substring(value.length() - 6);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(TalentErrorCode.PARAM_INVALID, "JSON 字段格式非法", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
