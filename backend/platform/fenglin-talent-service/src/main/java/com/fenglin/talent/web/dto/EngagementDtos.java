package com.fenglin.talent.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * C4 敬业度与离职预测 DTO。
 */
public final class EngagementDtos {

    private EngagementDtos() {
    }

    public record SurveyCreateRequest(String title, String surveyType, OffsetDateTime startAt,
                                      OffsetDateTime endAt, Boolean anonymous, Object questions, Object dimensions,
                                      Object targetScope) {
    }

    public record SurveyCreateResult(Long id, String surveyNo, String surveyStatus) {
    }

    public record SurveyListItem(Long id, String surveyNo, String title, String surveyType, String surveyStatus,
                                 Instant startAt, Instant endAt, Integer responseCount) {
    }

    public record SurveyPublishResult(Long id, String surveyStatus, long targetCount) {
    }

    public record SurveyQuestionsResult(Long surveyId, boolean anonymous, Object questions, Object myDraft) {
    }

    public record Answer(String qid, Object value) {
    }

    public record SurveySubmitRequest(List<Answer> answers, Integer durationSec, Long employeeId) {
    }

    public record SurveySubmitResult(boolean submitted) {
    }

    public record OrgBreakdown(Long orgId, BigDecimal score, long sample) {
    }

    public record SurveyAnalysis(BigDecimal overall, BigDecimal enps, Object dimScores, Object trend,
                                 List<OrgBreakdown> orgBreakdown) {
    }

    public record Scope(Long orgId) {
    }

    public record RiskScoreTriggerRequest(Scope scope, Boolean async) {
    }

    public record RiskScoreTriggerResult(Long taskId, long estimatedCount) {
    }

    public record RiskItem(Long employeeId, String name, String orgName, Integer riskScore, String riskLevel,
                           Integer trend, Object factors, Instant scoredAt) {
    }

    public record InterventionCreateRequest(Long employeeId, Long riskId, String measureType, String content,
                                            Long ownerId) {
    }

    public record InterventionCreateResult(Long id, String ivStatus) {
    }

    public record InterventionUpdateRequest(String followUp, String status) {
    }

    public record InterventionUpdateResult(Long id, String ivStatus) {
    }
}
