package com.fenglin.talent.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * C2 培训与知识 DTO。
 */
public final class TrainingDtos {

    private TrainingDtos() {
    }

    public record CourseCreateRequest(String courseCode, String title, String category, Long coverFileId,
                                      String description, String lecturer, String mode, BigDecimal durationHours,
                                      BigDecimal credit, Object targetAudience) {
    }

    public record CourseCreateResult(Long id, String courseStatus) {
    }

    public record CourseListItem(Long id, String courseCode, String title, String category, String mode,
                                 BigDecimal durationHours, BigDecimal credit, String courseStatus) {
    }

    public record CourseStatusResult(Long id, String courseStatus) {
    }

    public record SessionCreateRequest(OffsetDateTime startAt, OffsetDateTime endAt, Integer capacity,
                                       OffsetDateTime enrollDeadline, String venue) {
    }

    public record SessionCreateResult(Long id, String sessionStatus) {
    }

    public record EnrollmentCreateRequest(Long courseId, Long sessionId, Long employeeId) {
    }

    public record EnrollmentCreateResult(Long id, String enrollStatus, Boolean waitlist, Integer waitlistNo) {
    }

    public record EnrollmentCancelResult(Long id, String enrollStatus, Long promotedWaitlistUserId) {
    }

    public record LearningProgressRequest(Integer progress, Integer studySeconds, String position) {
    }

    public record LearningProgressResult(Long id, Integer progress, String learnStatus) {
    }

    public record ExamSubmitRequest(Object answers, BigDecimal score) {
    }

    public record ExamSubmitResult(BigDecimal score, boolean passed, String learnStatus) {
    }

    public record CertificateView(Long id, String certNo, String certName, LocalDate issueDate,
                                  LocalDate expireDate) {
    }

    public record CategoryCreateRequest(String name, Long parentId, Integer sortNo) {
    }

    public record CategoryView(Long id, String name, Long parentId, Integer sortNo, List<CategoryView> children) {
    }

    public record ArticleCreateRequest(String title, Long categoryId, String content, Object tags) {
    }

    public record ArticleCreateResult(Long id, String artStatus) {
    }

    public record ArticleStatusResult(Long id, String artStatus, Integer artVersion) {
    }

    public record ArticleListItem(Long id, String title, String category, String author, Integer viewCount,
                                  Instant publishedAt) {
    }
}
