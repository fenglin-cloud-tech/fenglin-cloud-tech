package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.talent.service.TrainingService;
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
import com.fenglin.talent.web.dto.TrainingDtos.SessionCreateRequest;
import com.fenglin.talent.web.dto.TrainingDtos.SessionCreateResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C2 培训与知识接口（API-110 ~ API-162）。
 */
@RestController
@RequestMapping("/api/talent/v1")
public class TalentTrainingController {

    private static final String COURSE_CREATE = "talent:admin:course:create";
    private static final String COURSE_LIST = "talent:course:list";
    private static final String SESSION_CREATE = "talent:admin:session:create";
    private static final String COURSE_ENROLL = "biz:talent:course:enroll";
    private static final String COURSE_CANCEL = "biz:talent:course:cancel";
    private static final String LEARNING_PROGRESS = "biz:talent:learning:progress";
    private static final String EXAM_SUBMIT = "biz:talent:exam:submit";
    private static final String CERT_LIST = "biz:talent:cert:list";
    private static final String KNOWLEDGE_CREATE = "talent:knowledge:create";
    private static final String KNOWLEDGE_PUBLISH = "talent:admin:knowledge:publish";
    private static final String KNOWLEDGE_SEARCH = "talent:knowledge:search";

    private final TrainingService trainingService;

    public TalentTrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    // ---------- 课程 / 班次 ----------

    @PostMapping("/training/courses")
    public ApiResponse<CourseCreateResult> createCourse(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody CourseCreateRequest request) {
        PermissionGuard.require(permissions, COURSE_CREATE);
        return ApiResponse.success(trainingService.createCourse(request));
    }

    @GetMapping("/training/courses")
    public ApiResponse<PageResult<CourseListItem>> listCourses(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "courseStatus", required = false) String courseStatus,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, COURSE_LIST);
        return ApiResponse.success(trainingService.listCourses(category, mode, courseStatus, keyword, page, size));
    }

    @PostMapping("/training/courses/{id}/publish")
    public ApiResponse<CourseStatusResult> publishCourse(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, COURSE_CREATE);
        return ApiResponse.success(trainingService.changeCourseStatus(id, "PUBLISHED"));
    }

    @PostMapping("/training/courses/{id}/offline")
    public ApiResponse<CourseStatusResult> offlineCourse(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, COURSE_CREATE);
        return ApiResponse.success(trainingService.changeCourseStatus(id, "OFFLINE"));
    }

    @PostMapping("/training/courses/{id}/sessions")
    public ApiResponse<SessionCreateResult> createSession(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody SessionCreateRequest request) {
        PermissionGuard.require(permissions, SESSION_CREATE);
        return ApiResponse.success(trainingService.createSession(id, request));
    }

    // ---------- 报名 / 学习 ----------

    @PostMapping("/training/enrollments")
    public ApiResponse<EnrollmentCreateResult> enroll(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody EnrollmentCreateRequest request) {
        PermissionGuard.require(permissions, COURSE_ENROLL);
        return ApiResponse.success(trainingService.enroll(request));
    }

    @DeleteMapping("/training/enrollments/{id}")
    public ApiResponse<EnrollmentCancelResult> cancel(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, COURSE_CANCEL);
        return ApiResponse.success(trainingService.cancelEnrollment(id));
    }

    @PostMapping("/training/learning/{id}/progress")
    public ApiResponse<LearningProgressResult> progress(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody LearningProgressRequest request) {
        PermissionGuard.require(permissions, LEARNING_PROGRESS);
        return ApiResponse.success(trainingService.reportProgress(id, request));
    }

    @PostMapping("/training/exams/{id}/submit")
    public ApiResponse<ExamSubmitResult> submitExam(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id, @RequestBody ExamSubmitRequest request) {
        PermissionGuard.require(permissions, EXAM_SUBMIT);
        return ApiResponse.success(trainingService.submitExam(id, request));
    }

    @GetMapping("/training/certificates")
    public ApiResponse<List<CertificateView>> certificates(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "employeeId", required = false) Long employeeId) {
        PermissionGuard.require(permissions, CERT_LIST);
        return ApiResponse.success(trainingService.myCertificates(employeeId));
    }

    // ---------- 知识库 ----------

    @PostMapping("/knowledge/categories")
    public ApiResponse<CategoryView> createCategory(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody CategoryCreateRequest request) {
        PermissionGuard.require(permissions, KNOWLEDGE_CREATE);
        return ApiResponse.success(trainingService.createCategory(request));
    }

    @GetMapping("/knowledge/categories")
    public ApiResponse<List<CategoryView>> categories(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, KNOWLEDGE_SEARCH);
        return ApiResponse.success(trainingService.listCategories());
    }

    @PostMapping("/knowledge/articles")
    public ApiResponse<ArticleCreateResult> createArticle(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ArticleCreateRequest request) {
        PermissionGuard.require(permissions, KNOWLEDGE_CREATE);
        return ApiResponse.success(trainingService.createArticle(request));
    }

    @PostMapping("/knowledge/articles/{id}/submit")
    public ApiResponse<ArticleStatusResult> submitArticle(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, KNOWLEDGE_CREATE);
        return ApiResponse.success(trainingService.submitArticle(id));
    }

    @PostMapping("/knowledge/articles/{id}/publish")
    public ApiResponse<ArticleStatusResult> publishArticle(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, KNOWLEDGE_PUBLISH);
        return ApiResponse.success(trainingService.publishArticle(id));
    }

    @GetMapping("/knowledge/articles")
    public ApiResponse<PageResult<ArticleListItem>> searchArticles(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, KNOWLEDGE_SEARCH);
        return ApiResponse.success(trainingService.searchArticles(categoryId, keyword, page, size));
    }
}
