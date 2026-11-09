package com.fenglin.crm.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * CRM 主链 DTO 集合。JSON 字段由全局 SNAKE_CASE 策略转换。
 */
public final class CrmDtos {

    private CrmDtos() {
    }

    // ---------- 线索 ----------

    public record LeadCreateRequest(String name, String contactName, String mobile, String email,
                                    String industry, String regionCode, String source, Long ownerId,
                                    String grade, String remark) {
    }

    public record LeadView(long leadId, String leadNo, String name, String contactName, String mobileMasked,
                           String email, String industry, String regionCode, String source, int score,
                           String grade, String status, Long ownerId, Long deptId, Instant lastActivityAt,
                           Long convertedCustomerId, Instant createdAt) {
    }

    public record LeadDetailView(LeadView lead, List<ActivityView> activities) {
    }

    public record DuplicateCheckRequest(String mobile, String name, String email) {
    }

    public record DuplicateMatch(long id, String no, String name, String mobileMasked) {
    }

    public record DuplicateCheckResult(List<DuplicateMatch> exactMatches, List<DuplicateMatch> fuzzyMatches) {
    }

    public record AssignRequest(Long ownerId, Long deptId, String reason) {
    }

    public record ConvertRequest(Boolean createCustomer, Boolean createContact, Boolean createOpportunity,
                                 String opportunityName, BigDecimal amount, String stage) {
    }

    public record ConvertResult(long leadId, long customerId, Long contactId, Long opportunityId,
                                String opportunityNo) {
    }

    // ---------- 客户 / 联系人 ----------

    public record CustomerCreateRequest(String name, String shortName, String uscc, String industry, String scale,
                                        String regionCode, String grade, String segment, Long ownerId, Long csmId,
                                        String mobile, BigDecimal annualRevenue, BigDecimal arr, String remark,
                                        Long sourceLeadId) {
    }

    public record CustomerView(long customerId, String customerNo, String name, String shortName, String uscc,
                               String industry, String scale, String regionCode, String grade, String segment,
                               Long ownerId, Long csmId, Long deptId, String mobileMasked, BigDecimal annualRevenue,
                               BigDecimal arr, String status, Instant createdAt) {
    }

    public record CustomerDetailView(CustomerView customer, List<ContactView> contacts,
                                     List<OpportunityView> opportunities, List<ActivityView> activities) {
    }

    public record CustomerOwnerRequest(Long ownerId, String reason) {
    }

    public record ContactCreateRequest(long customerId, String name, String position, String roleType,
                                       String mobile, String email, String wechat, Integer influence, Boolean isKey) {
    }

    public record ContactView(long contactId, long customerId, String name, String position, String roleType,
                              String mobileMasked, String email, String wechat, int influence, boolean isKey) {
    }

    // ---------- 商机 ----------

    public record OpportunityCreateRequest(String name, long customerId, BigDecimal amount, String stage,
                                           LocalDate expectedCloseDate, String source, String nextStep,
                                           BigDecimal winRate, Long ownerId) {
    }

    public record OpportunityView(long opportunityId, String opportunityNo, String name, long customerId,
                                  String stage, BigDecimal winRate, BigDecimal amount, BigDecimal weightedAmount,
                                  LocalDate expectedCloseDate, Long ownerId, String status, String nextStep,
                                  String lostReason, Instant createdAt) {
    }

    public record StageChangeRequest(String toStage, String opinion, BigDecimal amount,
                                     LocalDate expectedCloseDate, String lostReason, String nextStep) {
    }

    public record StageLogView(String fromStage, String toStage, BigDecimal amountSnapshot, Long changedBy,
                               Instant changedAt, String opinion) {
    }

    public record PipelineStage(String stage, long count, BigDecimal amount, BigDecimal weightedAmount) {
    }

    public record PipelineView(List<PipelineStage> stages, BigDecimal totalWeightedAmount, long openCount) {
    }

    // ---------- 报价 ----------

    public record QuotationItemRequest(String productCode, String productName, String unit, BigDecimal quantity,
                                       BigDecimal unitPrice, BigDecimal discountRate) {
    }

    public record QuotationCreateRequest(Long opportunityId, long customerId, BigDecimal taxRate,
                                         LocalDate validUntil, String remark, List<QuotationItemRequest> items) {
    }

    public record QuotationItemView(int lineNo, String productCode, String productName, String unit,
                                    BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate,
                                    BigDecimal amount) {
    }

    public record QuotationView(long quotationId, String quotationNo, Long opportunityId, long customerId,
                                int versionNo, BigDecimal totalAmount, BigDecimal discountRate, BigDecimal taxRate,
                                LocalDate validUntil, String status, String approveInstanceId,
                                List<QuotationItemView> items, Instant createdAt) {
    }

    /** 扩展：报价列表（01 文档未定义列表接口，员工端「我的报价」与客户详情需要）。 */
    public record QuotationListItemView(long quotationId, String quotationNo, Long opportunityId, long customerId,
                                        String customerName, int versionNo, BigDecimal totalAmount,
                                        String status, Long ownerId, Instant createdAt) {
    }

    public record QuotationSubmitRequest(String comment) {
    }

    public record QuotationSubmitResult(long quotationId, String status, String approveInstanceId,
                                        boolean approvalRequired) {
    }

    public record ApprovalResultRequest(String result, String comment) {
    }

    // ---------- 合同 / 回款计划 ----------

    public record ContractCreateRequest(long customerId, String slaLevel, String servicePackage, BigDecimal amount,
                                        LocalDate startDate, LocalDate endDate,
                                        List<PaymentPlanRequest> paymentPlans) {
    }

    public record PaymentPlanRequest(Integer periodNo, LocalDate dueDate, BigDecimal amount) {
    }

    public record PaymentPlanView(long planId, int periodNo, LocalDate dueDate, BigDecimal amount,
                                  BigDecimal paidAmount, BigDecimal remainingAmount, String status, int overdueDays,
                                  String collectionFlag, String collectionNote) {
    }

    /** 扩展：合同列表（01 文档未定义列表接口，员工端「我的合同」与回款提醒需要）。 */
    public record ContractListItemView(long contractId, String contractNo, long customerId, String customerName,
                                       String slaLevel, String servicePackage, BigDecimal amount,
                                       LocalDate startDate, LocalDate endDate, String status, Long ownerId,
                                       Instant createdAt) {
    }

    public record ContractView(long contractId, String contractNo, long customerId, String slaLevel,
                               String servicePackage, BigDecimal amount, LocalDate startDate, LocalDate endDate,
                               String status, List<PaymentPlanView> paymentPlans, Instant createdAt) {
    }

    // ---------- 活动 ----------

    public record ActivityCreateRequest(String bizType, long bizId, String activityType, String subject,
                                        String content, Integer durationMin, String result, String nextPlan,
                                        Instant occurredAt) {
    }

    public record ActivityView(long activityId, String bizType, long bizId, String activityType, String subject,
                               String content, Integer durationMin, String result, String nextPlan,
                               Instant occurredAt, Long ownerId) {
    }

    // ---------- 回款 / 核销 / 催收（扩展） ----------

    public record PaymentRegisterRequest(Long planId, BigDecimal amount, LocalDate receivedDate, String method,
                                         String voucherNo, String remark) {
    }

    public record PaymentRecordView(long paymentId, long contractId, long planId, BigDecimal amount,
                                    LocalDate receivedDate, String method, String voucherNo, String remark,
                                    String status, Instant createdAt) {
    }

    public record OverduePlanView(long planId, long contractId, String contractNo, long customerId,
                                  String customerName, int periodNo, LocalDate dueDate, BigDecimal amount,
                                  BigDecimal paidAmount, BigDecimal remainingAmount, int overdueDays,
                                  String collectionFlag, String collectionNote) {
    }

    /** 扩展：回款提醒（未结清且到期日不晚于未来 N 天，按负责人过滤）。 */
    public record PaymentReminderView(long planId, long contractId, String contractNo, long customerId,
                                      String customerName, int periodNo, LocalDate dueDate, BigDecimal amount,
                                      BigDecimal paidAmount, BigDecimal remainingAmount, int overdueDays,
                                      String state, String collectionFlag, String collectionNote) {
    }

    public record CollectionFlagRequest(String collectionFlag, String note) {
    }

    // ---------- 合同变更 / 续签 / 报价转合同（扩展） ----------

    public record ContractAmendRequest(BigDecimal amount, String slaLevel, String servicePackage,
                                       LocalDate endDate, String reason, List<PaymentPlanRequest> paymentPlans) {
    }

    public record ContractRenewRequest(LocalDate startDate, LocalDate endDate, BigDecimal amount, String slaLevel,
                                       String servicePackage, String reason, List<PaymentPlanRequest> paymentPlans) {
    }

    public record ContractChangeView(long changeId, String changeNo, long contractId, String changeType,
                                     BigDecimal beforeAmount, BigDecimal afterAmount,
                                     LocalDate beforeStartDate, LocalDate beforeEndDate,
                                     LocalDate afterStartDate, LocalDate afterEndDate, String reason,
                                     Long newContractId, String status, Instant createdAt) {
    }

    public record QuotationConvertRequest(String slaLevel, String servicePackage, LocalDate startDate,
                                          LocalDate endDate, String reason,
                                          List<PaymentPlanRequest> paymentPlans) {
    }

}
