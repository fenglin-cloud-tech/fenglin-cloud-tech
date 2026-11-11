package com.fenglin.collab.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 云协主链 DTO 集合。JSON 字段由全局 SNAKE_CASE 策略转换。
 */
public final class CollabDtos {

    private CollabDtos() {
    }

    // ---------- IM ----------

    public record ConversationCreateRequest(String convType, String title, List<Long> memberIds,
                                            String announcement) {
    }

    public record ConversationView(String conversationId, String convType, String title, int memberCount,
                                   String status, Long ownerId, String lastMessageSummary, Instant lastMessageAt,
                                   long unreadCount, boolean pinned, boolean mute, Instant createdAt) {
    }

    public record ConversationDetailView(ConversationView conversation, List<MemberView> members) {
    }

    public record MemberView(long memberId, String role, boolean mute, boolean pinned, String memberStatus,
                             Instant joinAt) {
    }

    public record MemberAddRequest(List<Long> memberIds) {
    }

    public record MessageSendRequest(String clientMsgId, String msgType, String content, Long replyToId,
                                     List<Long> mentionIds, String fileId) {
    }

    public record MessageView(long messageId, String conversationId, long senderId, String msgType, String summary,
                              String fileId, Long replyToId, long seq, boolean revoked, Instant sentAt) {
    }

    public record MessagePage(List<MessageView> records, Long nextCursor) {
    }

    public record RevokeRequest(String reason) {
    }

    public record ReactionRequest(String emoji) {
    }

    public record ReadResult(String conversationId, long unreadCount) {
    }

    // ---------- 文档 ----------

    public record DocCreateRequest(String title, String docType, String folderId, String secretLevel) {
    }

    public record DocView(String docId, String title, String docType, String secretLevel, int currentVersion,
                          String status, long ownerId, String myPermission, String content, boolean watermarkEnabled,
                          String shareScope, Instant updatedAt) {
    }

    public record DocUpdateRequest(String title, String content, Integer version) {
    }

    public record DocVersionView(int versionNo, String versionType, Long editorId, Instant createdAt) {
    }

    public record DocVersionCreateRequest(String versionType, String comment) {
    }

    public record DocVersionResult(int versionNo, String snapshotFileId, Long editorId) {
    }

    public record DocRollbackResult(int currentVersion, int rolledBackFrom) {
    }

    public record CollaboratorRequest(String subjectType, Long subjectId, String permission) {
    }

    public record CollaboratorView(long id, String subjectType, long subjectId, String permission,
                                   Long grantedBy) {
    }

    public record ShareLinkRequest(Integer expireDays, String accessCode) {
    }

    public record ShareLinkView(String linkToken, String url, Instant expireAt) {
    }

    public record CommentCreateRequest(String content, Long parentId, Map<String, Object> anchor,
                                       List<Long> mentionIds) {
    }

    public record CommentView(long commentId, Long parentId, long authorId, String content, boolean resolved,
                              Instant createdAt) {
    }

    // ---------- 日历 ----------

    public record CalendarCreateRequest(String name, String calType, String color, String visibleScope,
                                        Long deptId) {
    }

    public record CalendarView(String calendarId, String name, String calType, Long ownerId, String color,
                               String visibleScope) {
    }

    public record EventCreateRequest(String calendarId, String title, String description, String location,
                                     OffsetDateTime startAt, OffsetDateTime endAt, Boolean allDay, String rrule,
                                     List<Integer> reminderMinutes, List<Long> attendeeIds, Long roomId) {
    }

    public record EventView(String eventId, String calendarId, String title, Instant startAt, Instant endAt,
                            boolean allDay, String status, String meetingId, List<AttendeeView> attendees) {
    }

    public record AttendeeView(long attendeeId, String response, boolean organizer) {
    }

    public record EventUpdateRequest(String title, String description, String location, OffsetDateTime startAt,
                                     OffsetDateTime endAt, Boolean allDay) {
    }

    public record FreeBusyRequest(List<Long> userIds, OffsetDateTime startTime, OffsetDateTime endTime) {
    }

    public record BusySlot(Instant start, Instant end, String status) {
    }

    public record FreeBusyView(Map<Long, List<BusySlot>> busy) {
    }

    public record SuggestSlotsRequest(List<Long> userIds, Integer durationMinutes, Integer searchDays) {
    }

    public record SlotView(Instant start, Instant end, int availableCount, int score) {
    }

    public record SlotSuggestion(List<SlotView> slots) {
    }

    // ---------- 会议 ----------

    public record MeetingCreateRequest(String title, OffsetDateTime startAt, OffsetDateTime endAt,
                                       String meetingMode, Long roomId, Boolean externalAllowed, String agenda) {
    }

    public record MeetingView(String meetingId, String title, long organizerId, Long roomId, Instant startAt,
                              Instant endAt, String meetingMode, String joinCode, String status) {
    }

    public record MeetingDetailView(MeetingView meeting, CheckinStats checkins, MinutesView minutes,
                                    List<ActionView> actions) {
    }

    public record CheckinStats(int checkedIn, List<Long> userIds) {
    }

    public record CheckinRequest(String method, String code) {
    }

    public record CheckinResult(boolean checkedIn, Instant checkinAt) {
    }

    public record MinutesCreateRequest(Long templateId, List<String> topics, List<String> conclusions,
                                       String content, Boolean publish) {
    }

    public record MinutesView(String minutesId, String status, Instant publishedAt, String summary, String content,
                              Instant createdAt) {
    }

    public record InviteView(String meetingId, String title, long organizerId, Instant startAt, Instant endAt,
                             String meetingMode, String status, String response) {
    }

    public record InviteRespondRequest(String response) {
    }

    public record MinutesResult(String minutesId, String status) {
    }

    public record ActionItemRequest(String content, Long assigneeId, OffsetDateTime dueAt) {
    }

    public record ActionCreateRequest(String minutesId, List<ActionItemRequest> actions) {
    }

    public record ActionResult(List<Long> todoIds) {
    }

    public record ActionView(long actionId, String content, Long assigneeId, Instant dueAt, Long todoId,
                             String status) {
    }

    public record RoomCreateRequest(String name, String location, Integer capacity, List<String> equipments,
                                    Long deptId, Boolean bookable) {
    }

    public record RoomView(long roomId, String name, String location, int capacity, List<String> equipments,
                           boolean bookable, String status) {
    }

    public record ReserveRequest(OffsetDateTime startAt, OffsetDateTime endAt, String meetingId) {
    }

    public record ReserveResult(String reservationId, String status) {
    }

    // ---------- OKR ----------

    public record PeriodUpsertRequest(String name, LocalDate startDate, LocalDate endDate, String scorePolicy,
                                      String status) {
    }

    public record PeriodView(String periodId, String name, LocalDate startDate, LocalDate endDate, String status) {
    }

    public record ObjectiveCreateRequest(String periodId, String objType, Long deptId, String title,
                                         String description, String visibility) {
    }

    public record ObjectiveView(String objectiveId, String periodId, long ownerId, String objType, String title,
                                String visibility, String status, List<KrView> keyResults) {
    }

    public record KrCreateRequest(String title, String metricType, BigDecimal targetValue, BigDecimal currentValue,
                                  String confidence) {
    }

    public record KrView(String krId, String title, String metricType, BigDecimal targetValue,
                         BigDecimal currentValue, BigDecimal progress, String confidence) {
    }

    public record AlignRequest(String parentObjectiveId, String alignType, BigDecimal weight) {
    }

    public record AlignResult(long alignmentId) {
    }

    public record ProgressRequest(BigDecimal progress, String confidence, String note) {
    }

    public record ProgressResult(BigDecimal progress, String confidence, Instant reportedAt) {
    }

    public record GraphNode(String id, String title, String objType, Long ownerId) {
    }

    public record GraphEdge(String childId, String parentId, String alignType) {
    }

    public record AlignmentGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
    }

    // ---------- 审批 ----------

    public record TemplateCreateRequest(String code, String name, String category, Map<String, Object> formSchema,
                                        String processKey, Boolean enabled, List<Long> deptScope) {
    }

    public record TemplateView(String templateId, String code, String name, String category, String processKey,
                               boolean enabled, Map<String, Object> formSchema) {
    }

    /** COL-01b：模板编辑。code 不可修改；未传的字段沿用原值。 */
    public record TemplateUpdateRequest(String name, String category, Map<String, Object> formSchema,
                                        String processKey, Boolean enabled, List<Long> deptScope) {
    }

    public record InstanceCreateRequest(String templateId, Map<String, Object> formData, Long approverId,
                                        String idempotencyKey) {
    }

    public record InstanceView(String instanceId, String instanceNo, String templateId, long initiatorId,
                               Map<String, Object> formData, String status, String currentNode, Instant createdAt,
                               List<TaskView> tasks) {
    }

    public record TaskView(long taskId, String instanceId, String nodeKey, String nodeName, Long assigneeId,
                           String status, String action, Instant handledAt) {
    }

    public record TaskActionRequest(String comment) {
    }

    public record RejectRequest(String comment, String returnTo) {
    }

    public record TransferRequest(Long assigneeId, String reason) {
    }

    public record AddSignRequest(Long assigneeId, String position) {
    }

    public record TaskResult(String taskStatus, String instanceStatus) {
    }

    // ---------- 工作台 ----------

    public record WidgetPlacement(String code, Map<String, Object> position, Map<String, Object> config) {
    }

    public record LayoutSaveRequest(List<WidgetPlacement> widgets) {
    }

    public record LayoutView(String layoutType, List<WidgetPlacement> widgets) {
    }

    public record WidgetCreateRequest(String code, String name, String widgetType, String defaultSize,
                                      Boolean enabled) {
    }

    public record TodoView(long todoId, String sourceType, String sourceId, String title, Instant dueAt,
                           String priority, String status) {
    }

    public record NotificationView(long notificationId, String category, String title, String content,
                                   boolean read, Instant createdAt) {
    }

    public record NotificationReadResult(long notificationId, boolean read) {
    }

    // ---------- 管理端 ----------

    public record SensitiveWordPolicyRequest(List<String> words, String action, String scope, Boolean enabled) {
    }

    public record SensitiveWordPolicyView(long policyId, int wordCount, String action, String scope,
                                          boolean enabled, List<String> words) {
    }

    public record ConversationAuditView(String convId, String title, String convType, int memberCount,
                                        long msgCount, String status, Instant createdAt) {
    }

    public record ConversationAuditPage(List<ConversationAuditView> records, long total, int page, int size) {
    }

    public record DocPolicyRequest(Boolean confidentialNoLink, Boolean confidentialNoDownload,
                                   String watermarkTemplate, Integer maxConcurrentEditors) {
    }

    public record DocPolicyView(boolean confidentialNoLink, boolean confidentialNoDownload,
                                String watermarkTemplate, int maxConcurrentEditors) {
    }

    // ---------- ONA ----------

    public record OnaNodeView(long id, String type, String name, Long deptId) {
    }

    public record OnaEdgeView(String source, String target, BigDecimal weight, boolean crossDept) {
    }

    public record OnaSummary(int nodeCount, int edgeCount, BigDecimal density) {
    }

    public record OnaGraphView(List<OnaNodeView> nodes, List<OnaEdgeView> edges, OnaSummary summary) {
    }

    public record OnaMetricView(long nodeId, String nodeType, BigDecimal degreeCentrality, BigDecimal betweenness,
                                BigDecimal closeness, BigDecimal clusteringCoef, BigDecimal structuralHole,
                                boolean keyConnector, BigDecimal crossDeptRatio) {
    }

    public record OnaKeyConnectorView(long nodeId, String nodeType, BigDecimal degreeCentrality,
                                      BigDecimal crossDeptRatio, long connections) {
    }

    public record OnaSnapshotView(String snapshotId, String periodType, String periodKey,
                                  BigDecimal networkDensity, BigDecimal avgDegree, BigDecimal crossDeptRatio,
                                  int keyConnectorCount, int siloCount, Instant generatedAt) {
    }

    public record MeetingEfficiencyView(BigDecimal efficiencyScore, BigDecimal decisionRate,
                                        BigDecimal onTimeRate, BigDecimal actionCompletionRate,
                                        BigDecimal overStaffRate, BigDecimal overTimeRate,
                                        BigDecimal costEstimate, List<String> warnings) {
    }

    public record DeptNetworkNodeView(String deptId, long nodeCount, long internalEdges) {
    }

    public record DeptNetworkLinkView(String sourceDeptId, String targetDeptId, long edgeCount,
                                      BigDecimal weight) {
    }

    public record DeptNetworkView(int deptCount, boolean sampleSufficient, List<DeptNetworkNodeView> departments,
                                  List<DeptNetworkLinkView> links) {
    }

    public record SelfTrendPoint(String periodKey, int degree) {
    }

    public record OnaCollaboratorView(long userId, Long deptId, BigDecimal weight) {
    }

    public record SelfProfileView(int myDegree, BigDecimal myCrossDeptRatio,
                                  List<OnaCollaboratorView> topCollaborators, List<SelfTrendPoint> trend) {
    }

    public record OnaConfigRequest(BigDecimal weightIm, BigDecimal weightDoc, BigDecimal weightMeeting,
                                   BigDecimal weightApproval, BigDecimal weightOkr, Integer anonymityThreshold,
                                   BigDecimal keyConnectorTopRatio, String profileVisibleScope) {
    }

    public record OnaConfigView(BigDecimal weightIm, BigDecimal weightDoc, BigDecimal weightMeeting,
                                BigDecimal weightApproval, BigDecimal weightOkr, int anonymityThreshold,
                                BigDecimal keyConnectorTopRatio, String profileVisibleScope) {
    }
}
