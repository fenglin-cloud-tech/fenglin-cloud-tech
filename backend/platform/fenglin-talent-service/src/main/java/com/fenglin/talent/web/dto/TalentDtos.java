package com.fenglin.talent.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 云才 C1 组织与人事主链 DTO 集合。JSON 字段由全局 SNAKE_CASE 策略转换。
 */
public final class TalentDtos {

    private TalentDtos() {
    }

    public record NameRef(Long id, String name) {
    }

    // ---------- 组织 ----------

    public record OrgNode(Long id, String orgCode, String orgName, Long parentId, int level, String orgType,
                          Long leaderId, List<OrgNode> children) {
    }

    public record OrgDetail(Long id, String orgCode, String orgName, Long parentId, String path, int level,
                            String orgType, NameRef leader, long employeeCount, String status) {
    }

    public record OrgCreateRequest(Long parentId, String orgCode, String orgName, String orgType, Long leaderId,
                                   Integer sortNo) {
    }

    public record OrgCreateResult(Long id, String orgCode, String path, int level) {
    }

    public record OrgUpdateRequest(String orgName, Long leaderId, Integer sortNo, String status, Integer version) {
    }

    public record OrgUpdateResult(Long id, Instant updatedAt) {
    }

    public record OrgMoveRequest(Long targetParentId) {
    }

    public record OrgMoveResult(Long id, String path, int level) {
    }

    public record OrgDeleteResult(Long id, String status) {
    }

    // ---------- 员工 ----------

    public record EmployeeCreateRequest(String empNo, String name, String gender, LocalDate birthDate, String phone,
                                        String idCard, String email, Long orgId, Long positionId, Long gradeId,
                                        Long managerId, String employmentType, LocalDate hireDate,
                                        String workLocation) {
    }

    public record EmployeeCreateResult(Long id, String empNo, String empStatus) {
    }

    public record EmployeeUpdateRequest(String name, String email, Long orgId, Long positionId, Long gradeId,
                                        Long managerId, String workLocation, Integer version) {
    }

    public record EmployeeUpdateResult(Long id, Instant updatedAt) {
    }

    public record EmployeeListItem(Long id, String empNo, String name, NameRef org, String position, String grade,
                                   String empStatus, LocalDate hireDate, String maskedPhone) {
    }

    public record EmployeeDetail(Long id, String empNo, String name, String gender, LocalDate birthDate,
                                 String maskedPhone, String maskedIdCard, String email, NameRef org, NameRef position,
                                 NameRef grade, NameRef manager, String employmentType, LocalDate hireDate,
                                 LocalDate formalDate, String empStatus, String workLocation, Integer version) {
    }

    // ---------- 岗位 / 职级 / 编制 ----------

    public record PositionCreateRequest(String positionCode, String positionName, Long orgId, String jobFamily,
                                        String jobSequence, Integer headcount, String description) {
    }

    public record PositionCreateResult(Long id, String positionCode) {
    }

    public record PositionView(Long id, String positionCode, String positionName, String jobFamily,
                               String jobSequence, int headcount, Long orgId, String status) {
    }

    public record GradeView(Long id, String gradeCode, String gradeName, String sequence, int gradeLevel,
                            BigDecimal bandMin, BigDecimal bandMax) {
    }

    public record HeadcountPlanCreateRequest(Integer planYear, Long orgId, Long positionId, Integer budgetHeadcount) {
    }

    public record HeadcountPlanCreateResult(Long id, String status) {
    }

    // ---------- 招聘 ----------

    public record RequisitionCreateRequest(Long positionId, Long orgId, Integer headcount, BigDecimal salaryMin,
                                           BigDecimal salaryMax, String urgency, String reasonType, Long ownerId,
                                           LocalDate expectDate, String description, String requirements) {
    }

    public record RequisitionCreateResult(Long id, String reqNo, String reqStatus) {
    }

    public record RequisitionListItem(Long id, String reqNo, String positionName, String orgName, int headcount,
                                      String reqStatus, NameRef owner, Instant createdAt) {
    }

    public record RequisitionSubmitResult(Long id, String reqStatus, String processInstanceId) {
    }

    public record CandidateCreateRequest(String name, String phone, String idCard, String email, String gender,
                                         LocalDate birthDate, String source, BigDecimal experienceYears,
                                         String currentCompany, BigDecimal expectedSalary, Object tags) {
    }

    public record CandidateCreateResult(Long id, String candStatus, boolean referralHint) {
    }

    public record CandidateListItem(Long id, String name, String source, String candStatus, BigDecimal experienceYears,
                                    String currentCompany, String maskedPhone, Instant createdAt) {
    }

    public record ApplicationCreateRequest(Long candidateId, Long requisitionId, String stage) {
    }

    public record ApplicationCreateResult(Long id, String appStatus) {
    }

    public record InterviewCreateRequest(Long applicationId, Integer roundNo, Long interviewerId,
                                         OffsetDateTime scheduledAt, Integer durationMin, String mode,
                                         String location) {
    }

    public record InterviewCreateResult(Long id, String intStatus) {
    }

    public record InterviewEvaluateRequest(BigDecimal score, String evaluation, String result, Integer version) {
    }

    public record InterviewEvaluateResult(Long id, String intStatus, String result) {
    }

    public record MyInterviewItem(Long id, String candidateName, String positionName, int roundNo,
                                  Instant scheduledAt, String mode, String intStatus) {
    }

    public record OfferCreateRequest(Long candidateId, Long requisitionId, Long positionId, Long gradeId,
                                     BigDecimal baseSalary, BigDecimal bonus, LocalDate offerDate,
                                     LocalDate expectJoinDate) {
    }

    public record OfferStatusResult(Long id, String offerStatus) {
    }

    // ---------- 入转调离 ----------

    public record OnboardingCreateRequest(Long offerId, Long employeeId, LocalDate onboardDate, Long mentorId,
                                          Object checklist) {
    }

    public record OnboardingCreateResult(Long id, String obStatus) {
    }

    public record OnboardingCompleteResult(Long id, String obStatus, String employeeStatus) {
    }

    public record ProbationEvaluateRequest(String evaluation, String result, Integer extendMonths) {
    }

    public record ProbationEvaluateResult(Long id, String probStatus) {
    }

    public record TransferAfter(Long orgId, Long positionId, Long gradeId, BigDecimal salary) {
    }

    public record TransferCreateRequest(Long employeeId, String transferType, TransferAfter after,
                                        LocalDate effectiveDate, String reason) {
    }

    public record TransferCreateResult(Long id, String tfStatus, String processInstanceId) {
    }

    public record TransferEmployee(Long orgId, Long positionId, Long gradeId) {
    }

    public record TransferEffectiveResult(Long id, String tfStatus, TransferEmployee employee) {
    }

    public record ResignationCreateRequest(Long employeeId, String resignType, LocalDate applyDate,
                                           LocalDate lastWorkDate, String reasonCategory) {
    }

    public record ResignationApproveRequest(Boolean approved, String opinion) {
    }

    public record ResignationStatusResult(Long id, String rsStatus) {
    }

    // ---------- 考勤 ----------

    public record AttendanceClockRequest(String clockType, String source) {
    }

    public record AttendanceRecordView(Long id, Long employeeId, LocalDate workDate, Instant clockInAt,
                                       Instant clockOutAt, String source, String attStatus, int workMinutes,
                                       int lateMinutes, int earlyMinutes, String exceptionNote, Integer version) {
    }

    public record AttendanceHandleRequest(String status, String note) {
    }

    public record AttendanceSummaryView(long normalDays, long lateDays, long earlyDays, long lateEarlyDays,
                                        long missingDays, long absentDays, long workMinutes, long lateMinutes,
                                        long earlyMinutes) {
    }

    // ---------- 假期 ----------

    public record LeaveTypeView(Long id, String typeCode, String typeName, BigDecimal annualQuotaDays,
                                boolean paid) {
    }

    public record LeaveBalanceView(Long id, Long leaveTypeId, String typeCode, String typeName, int year,
                                   BigDecimal totalDays, BigDecimal usedDays, BigDecimal availableDays,
                                   Integer version) {
    }

    public record LeaveRequestCreateRequest(Long employeeId, Long leaveTypeId, LocalDate startDate,
                                            LocalDate endDate, BigDecimal days, String reason,
                                            String idempotencyKey) {
    }

    public record LeaveRequestResult(Long id, String requestNo, String lrStatus, String processInstanceId,
                                     BigDecimal usedDays) {
    }

    public record LeaveRequestView(Long id, String requestNo, Long employeeId, String employeeName,
                                   String leaveTypeName, LocalDate startDate, LocalDate endDate, BigDecimal days,
                                   String lrStatus, String approveOpinion, Instant createdAt) {
    }

    public record LeaveApprovalRequest(Boolean approved, String opinion) {
    }

    // ---------- 收口补充 ----------

    public record EmployeeImportRow(String empNo, String name, String gender, LocalDate birthDate, String phone,
                                    String idCard, String email, Long orgId, Long positionId, Long gradeId,
                                    Long managerId, String employmentType, LocalDate hireDate,
                                    String workLocation) {
    }

    public record EmployeeImportRequest(List<EmployeeImportRow> rows) {
    }

    public record EmployeeImportError(int index, String empNo, String code, String message) {
    }

    public record EmployeeImportResult(int total, int success, int failed, List<EmployeeImportError> errors) {
    }

    public record ResumeParseRequest(String resumeText) {
    }

    public record ParsedResume(String name, String phone, String email, BigDecimal experienceYears) {
    }

    public record ResumeParseResult(Long candidateId, ParsedResume parsed, boolean applied) {
    }

    public record ApprovalRequest(Boolean approved, String opinion) {
    }

    public record RequisitionApprovalResult(Long id, String reqStatus) {
    }

    public record TransferApprovalResult(Long id, String tfStatus, TransferEmployee employee) {
    }

    public record OrgHeadcount(Long orgId, String orgName, long count) {
    }

    public record HeadcountReport(long total, Map<String, Long> byStatus, List<OrgHeadcount> byOrg,
                                  Map<String, Long> byEmploymentType, BigDecimal avgTenureMonths) {
    }
}
