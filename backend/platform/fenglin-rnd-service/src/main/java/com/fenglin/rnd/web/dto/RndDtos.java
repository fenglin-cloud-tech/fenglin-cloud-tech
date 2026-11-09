package com.fenglin.rnd.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 云研接口 DTO 集合：请求与视图record，JSON 由全局 SNAKE_CASE 策略输出。
 */
public final class RndDtos {

    private RndDtos() {
    }

    public record ProductCreateRequest(String productCode, String name, String productLine, Long ownerId,
                                       String visibility, Integer sprintCycleDays, String description) {
    }

    public record ProductView(Long id, String productCode, String name, String productLine, Long ownerId,
                              String visibility, String status, Integer sprintCycleDays, String description,
                              String createdAt) {
    }

    public record MemberCreateRequest(Long userId, String roleCode, String dataScope) {
    }

    public record MemberView(Long id, Long productId, Long userId, String roleCode, String dataScope,
                             String createdAt) {
    }

    public record StatusView(Long id, String statusCode, String statusName, String category, String color,
                             Integer sortNo) {
    }

    public record RequirementCreateRequest(Long productId, String title, String reqType, String priority,
                                           Long assigneeId, Long sprintId, Long parentId, BigDecimal estimateHours,
                                           String acceptanceCriteria, String planStartAt, String planEndAt) {
    }

    public record RequirementView(Long id, Long productId, String reqNo, String title, String reqType,
                                  String priority, Long statusId, String statusCode, String statusName,
                                  String statusCategory, Long sprintId, Long parentId, Long assigneeId,
                                  Long reporterId, BigDecimal estimateHours, String acceptanceCriteria,
                                  String planStartAt, String planEndAt, String deliveredAt, Integer escaped,
                                  String createdAt) {
    }

    public record RequirementTransitionRequest(String statusCode) {
    }

    public record RequirementReviewResult(String approvalInstanceId, String statusCode) {
    }

    public record RelationCreateRequest(Long dstRequirementId, String relationType) {
    }

    public record SprintAssignRequest(Long sprintId) {
    }

    public record SprintMemberRequest(Long userId, BigDecimal availableHours) {
    }

    public record SprintCreateRequest(Long productId, String name, String goal, LocalDate startDate,
                                      LocalDate endDate, BigDecimal capacityHours,
                                      List<SprintMemberRequest> members) {
    }

    public record SprintView(Long id, Long productId, String sprintNo, String name, String goal,
                             LocalDate startDate, LocalDate endDate, BigDecimal capacityHours, String status,
                             Integer requirementCount, Integer taskCount, Integer doneTaskCount, String createdAt) {
    }

    public record BurndownPoint(String date, BigDecimal remainingHours, BigDecimal idealHours) {
    }

    public record BurndownView(Long sprintId, String name, Integer totalTasks, Integer doneTasks,
                              List<BurndownPoint> points) {
    }

    public record BoardColumnRequest(String columnName, List<String> statusCodes, Integer wipLimit) {
    }

    public record BoardCreateRequest(Long productId, String name, String boardType, Long sprintId,
                                     List<BoardColumnRequest> columns) {
    }

    public record BoardColumnView(Long id, String columnName, List<String> statusCodes, Integer wipLimit,
                                  Integer sortNo, Integer itemCount) {
    }

    public record BoardView(Long id, Long productId, String name, String boardType, Long sprintId,
                            List<BoardColumnView> columns) {
    }

    public record MoveRequest(String bizType, Long bizId, Long columnId) {
    }

    public record TaskCreateRequest(Long productId, String title, String taskType, Long sprintId,
                                    Long requirementId, Long defectId, Long assigneeId, BigDecimal estimateHours,
                                    LocalDate dueDate, String priority) {
    }

    public record TaskView(Long id, Long productId, String taskNo, String title, String taskType, String status,
                           String priority, Long sprintId, Long requirementId, Long assigneeId,
                           BigDecimal estimateHours, BigDecimal actualHours, LocalDate dueDate, String completedAt,
                           String createdAt) {
    }

    public record TaskTransitionRequest(String status) {
    }

    public record WorklogCreateRequest(Long taskId, Long defectId, LocalDate workDate, BigDecimal hours,
                                       String remark) {
    }

    public record WorklogView(Long id, Long taskId, Long defectId, Long userId, LocalDate workDate,
                              BigDecimal hours, String remark, String createdAt) {
    }

    public record DefectCreateRequest(Long productId, String title, String severity, String priority, String env,
                                      String foundStage, Long sprintId, Long requirementId, Long testCaseId,
                                      Long assigneeId, String reproduceSteps) {
    }

    public record DefectView(Long id, Long productId, String defectNo, String title, String severity,
                             String priority, String status, String env, String foundStage, Long sprintId,
                             Long requirementId, Long assigneeId, Long reporterId, String reproduceSteps,
                             Integer reopenCount, String fixedAt, String closedAt, String createdAt) {
    }

    public record DefectTransitionRequest(String status) {
    }

    public record TestCaseCreateRequest(Long productId, String title, String module, String level, String caseType,
                                        String preconditions, String stepsJson, Long requirementId) {
    }

    public record TestCaseView(Long id, Long productId, String caseNo, String title, String module, String level,
                               String caseType, String preconditions, String steps, Long requirementId,
                               String reviewStatus, String status, String createdAt) {
    }

    public record TestPlanCreateRequest(Long productId, String name, Long sprintId, Long buildId,
                                        LocalDate startDate, LocalDate endDate) {
    }

    public record TestPlanView(Long id, Long productId, String planNo, String name, Long sprintId, Long buildId,
                               String status, Integer caseCount, Integer passCount, Integer failCount,
                               String createdAt) {
    }

    public record ExecutionCreateRequest(Long executorId) {
    }

    public record ExecutionItemRequest(Long caseId, String result, String remark) {
    }

    public record ExecutionView(Long id, Long planId, Long executorId, String result, Integer passCount,
                                Integer failCount, String executedAt) {
    }

    public record RepoCreateRequest(Long productId, String repoName, String vcsType, String repoUrl,
                                    String defaultBranch, String accessToken, String webhookSecret) {
    }

    public record RepoView(Long id, Long productId, String repoName, String vcsType, String repoUrl,
                           String defaultBranch, String syncStatus, String tokenMasked, String lastSyncAt,
                           String createdAt) {
    }

    public record SyncResult(Long repoId, String syncStatus, Integer syncedCommits, String syncedAt) {
    }

    public record CommitIngestRequest(Long repoId, String commitSha, String branch, String authorName,
                                      String authorEmail, Integer addLines, Integer delLines, Integer filesChanged,
                                      Integer isMerge, String message, String committedAt) {
    }

    public record CommitView(Long id, Long repoId, String commitSha, String branch, String authorName,
                             String message, Integer addLines, Integer delLines, Integer filesChanged,
                             Integer isMerge, Integer isExcluded, String committedAt) {
    }

    public record CommitLinkRequest(Long commitId, String bizType, Long bizId, String linkType) {
    }

    public record PipelineCreateRequest(Long productId, String name, String ciType, String triggerMode,
                                        Long repoId) {
    }

    public record PipelineView(Long id, Long productId, String name, String ciType, String triggerMode,
                               String status, String createdAt) {
    }

    public record TriggerRequest(String triggerType) {
    }

    public record RunFinishRequest(String status, String artifactName, String artifactVersion, String artifactUrl,
                                   String qualityGate) {
    }

    public record RunView(Long id, Long pipelineId, String runNo, String status, String triggerType,
                          String startedAt, String finishedAt, Long durationMs, Long buildId, String buildNo,
                          String artifactName, String artifactVersion, String qualityGate) {
    }

    public record ReleaseCreateRequest(Long productId, String releaseVersion, String env, Long buildId,
                                       String windowStart, String windowEnd) {
    }

    public record ReleaseView(Long id, Long productId, String releaseNo, String releaseVersion, String env,
                              Long buildId, String status, String approvalInstanceId, String windowStart,
                              String windowEnd, String deployedAt, String createdAt) {
    }

    public record DeployRequest(String strategy) {
    }

    public record DeploymentView(Long id, Long releaseId, String env, String result, String strategy,
                                 Long operatorId, String startedAt, String finishedAt) {
    }

    public record MetricComputeRequest(Long productId, String periodType, String periodKey, Long userId) {
    }

    public record MetricValueView(String metricCode, String metricName, String metricGroup,
                                  BigDecimal metricValue, String unit, String dataQuality, String caliberVersion) {
    }

    public record MetricComputeResult(Long productId, String periodType, String periodKey,
                                      List<MetricValueView> metrics, String doraLevel) {
    }

    public record ContributionView(Long userId, Long productId, String periodType, String periodKey,
                                   Integer commitCount, Integer addLines, Integer delLines, Integer effectiveCommits,
                                   BigDecimal effectiveCommitRate, BigDecimal complexityScore) {
    }

    public record DoraView(Long productId, String periodType, String periodKey, BigDecimal deployFreq,
                           BigDecimal leadTimeHours, BigDecimal changeFailureRate, BigDecimal mttrHours,
                           String level, String computedAt) {
    }

    public record InsightView(Long productId, String periodKey, List<MetricValueView> metrics,
                              DoraView dora, Integer requirementCount, Integer defectCount,
                              Integer commitCount, Integer releaseCount, String dataQuality) {
    }

    public record MetricConfigCreateRequest(String metricCode, String metricName, String metricGroup,
                                            String formula, BigDecimal weight, BigDecimal targetValue, String unit) {
    }

    public record MetricConfigView(Long id, Long tenantId, String metricCode, String metricName, String metricGroup,
                                   String formula, String caliberVersion, BigDecimal weight, BigDecimal targetValue,
                                   String unit, String status) {
    }

    public record AlertCreateRequest(String metricCode, String scopeType, Long scopeId, BigDecimal threshold,
                                     String compareOp, String notifyChannel) {
    }

    public record AlertView(Long id, String metricCode, String scopeType, Long scopeId, BigDecimal threshold,
                            String compareOp, BigDecimal triggeredValue, String status, String notifyChannel,
                            String triggeredAt) {
    }
}
