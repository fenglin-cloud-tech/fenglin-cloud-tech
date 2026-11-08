package com.fenglin.talent.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * C5 OKR 与创新 DTO。
 */
public final class OkrDtos {

    private OkrDtos() {
    }

    public record PeriodCreateRequest(String periodName, String periodType, LocalDate startDate, LocalDate endDate,
                                      LocalDate reviewStart, LocalDate reviewEnd) {
    }

    public record PeriodCreateResult(Long id, String periodStatus) {
    }

    public record PeriodView(Long id, String periodName, String periodType, LocalDate startDate, LocalDate endDate,
                             LocalDate reviewStart, LocalDate reviewEnd, String periodStatus) {
    }

    public record ObjectiveCreateRequest(Long periodId, Long orgId, String objLevel, String objective,
                                         Long parentObjectiveId, String visibility) {
    }

    public record ObjectiveCreateResult(Long id, String objStatus) {
    }

    public record ObjectiveView(Long id, Long periodId, Long orgId, Long ownerId, String ownerName, String objLevel,
                                String objective, Integer progress, String confidence, Long parentObjectiveId,
                                String objStatus, BigDecimal finalScore, List<ObjectiveView> children) {
    }

    public record KrCreateRequest(String title, String metricType, BigDecimal startValue, BigDecimal targetValue,
                                  Integer weight) {
    }

    public record KrCreateResult(Long id, String krStatus) {
    }

    public record KrProgressRequest(BigDecimal currentValue, String confidence, String note, Integer version) {
    }

    public record KrProgressResult(Long id, Integer progress, String krStatus, Integer objectiveProgress) {
    }

    public record AlignmentCreateRequest(Long childObjectiveId, Long parentObjectiveId, String alignType,
                                         Integer weight) {
    }

    public record AlignmentCreateResult(Long id, String objStatus) {
    }

    public record AlignmentNode(ObjectiveView objective, String alignType, Integer weight,
                                List<AlignmentNode> children) {
    }

    public record AlignmentTree(ObjectiveView root, List<AlignmentNode> children) {
    }

    public record ObjectiveReviewRequest(BigDecimal finalScore, String summary) {
    }

    public record ObjectiveReviewResult(Long id, String objStatus, BigDecimal finalScore) {
    }

    public record ProposalCreateRequest(String title, String category, String content, Object teamMembers,
                                        String expectedBenefit, Object attachmentIds) {
    }

    public record ProposalCreateResult(Long id, String proposalNo, String propStatus) {
    }

    public record ProposalListItem(Long id, String proposalNo, String title, String category, String proposer,
                                   Integer votes, String propStatus) {
    }

    public record ProposalReviewRequest(BigDecimal score, String comment, String result) {
    }

    public record ProposalReviewResult(Long id, String rvResult) {
    }

    public record ProposalVoteRequest(BigDecimal score, String comment) {
    }

    public record ProposalVoteResult(Long id, Integer votes) {
    }

    public record ProposalStatusResult(Long id, String propStatus) {
    }
}
