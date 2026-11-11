package com.fenglin.finance.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 云财接口 DTO：字段命名与 04_丰麟云财_05 接口文档对齐（JSON 输出 SNAKE_CASE，金额为字符串）。
 */
public final class FinanceDtos {

    private FinanceDtos() {
    }

    // ---------- 账套/科目/期间/预算 ----------

    public record LedgerCreateRequest(String ledgerCode, String ledgerName, String companyName,
                                      String baseCurrency, Integer fiscalYearStartMonth,
                                      String accountingStandard, String taxNo) {
    }

    public record LedgerView(long id, String ledgerCode, String ledgerName, String companyName,
                             String baseCurrency, String accountingStandard, String status) {
    }

    public record SubjectCreateRequest(String subjectCode, String subjectName, Long parentId,
                                       String subjectCategory, String balanceDirection,
                                       Boolean isLeaf, String auxiliaryTypes) {
    }

    public record SubjectView(long id, String subjectCode, String subjectName, Long parentId,
                              int subjectLevel, String subjectCategory, String balanceDirection,
                              boolean isLeaf, String auxiliaryTypes, String status) {
    }

    public record PeriodView(long id, String periodCode, int periodYear, int periodMonth,
                             String startDate, String endDate, String status) {
    }

    public record PeriodCloseResult(String periodCode, String status, BigDecimal trialDebit,
                                    BigDecimal trialCredit, String recordHash, Instant closedAt) {
    }

    public record BudgetCreateRequest(String budgetNo, String budgetName, Integer periodYear,
                                      String dimension, Long dimensionId, String subjectCode,
                                      BigDecimal budgetAmount, String controlMode, String currencyCode) {
    }

    public record BudgetView(long id, String budgetNo, String budgetName, int periodYear, String dimension,
                             Long dimensionId, String subjectCode, BigDecimal budgetAmount,
                             BigDecimal occupiedAmount, BigDecimal executedAmount, BigDecimal available,
                             String controlMode, String status) {
    }

    public record BudgetControlCheckRequest(Integer periodYear, String dimension, Long dimensionId,
                                            String subjectCode, BigDecimal amount) {
    }

    public record BudgetControlResult(Long budgetId, String controlMode, BigDecimal budgetAmount,
                                      BigDecimal occupiedAmount, BigDecimal executedAmount,
                                      BigDecimal available, boolean allowed, String message) {
    }

    // ---------- 凭证/报表 ----------

    public record VoucherEntryRequest(String summary, String subjectCode, String direction,
                                      BigDecimal amount, Map<String, Object> auxiliary,
                                      BigDecimal quantity, BigDecimal price) {
    }

    public record VoucherCreateRequest(Long ledgerId, String voucherWord, LocalDate voucherDate,
                                       String summary, String currencyCode, String sourceType,
                                       Long sourceBizId, List<VoucherEntryRequest> entries) {
    }

    public record VoucherEntryView(int lineNo, String summary, String subjectCode, String subjectName,
                                   String direction, BigDecimal amount, String currencyCode,
                                   BigDecimal exchangeRate, BigDecimal amountBase, String auxiliary,
                                   BigDecimal quantity, BigDecimal price) {
    }

    public record VoucherAuditLogView(long chainSeq, String action, long operatorId, String operatorName,
                                      Instant occurredAt, String recordHash, String prevHash, String signature) {
    }

    public record VoucherView(long id, long ledgerId, String voucherWord, String voucherNo, String periodCode,
                              String voucherDate, String summary, String currencyCode, BigDecimal totalDebit,
                              BigDecimal totalCredit, String sourceType, Long sourceBizId, String status,
                              Long auditedBy, Long postedBy, String chainHash,
                              List<VoucherEntryView> entries, List<VoucherAuditLogView> auditTrail) {
    }

    public record VoucherActionResult(long id, String voucherNo, String status, String chainHash,
                                      String workflowInstanceId) {
    }

    public record AuditTrailView(long voucherId, boolean chainValid, String lastHash,
                                 List<VoucherAuditLogView> logs) {
    }

    public record TrialBalanceLine(String subjectCode, String subjectName, String subjectCategory,
                                   String balanceDirection, BigDecimal openingDebit, BigDecimal openingCredit,
                                   BigDecimal periodDebit, BigDecimal periodCredit,
                                   BigDecimal closingDebit, BigDecimal closingCredit) {
    }

    public record TrialBalanceView(String periodCode, List<TrialBalanceLine> lines, BigDecimal totalDebit,
                                   BigDecimal totalCredit, boolean balanced) {
    }

    public record BalanceSheetLine(String subjectCode, String subjectName, String category, BigDecimal amount) {
    }

    public record BalanceSheetView(String periodCode, BigDecimal assets, BigDecimal liabilities,
                                   BigDecimal equity, BigDecimal profitLoss, BigDecimal totalAssets,
                                   BigDecimal totalLiabilitiesEquity, boolean balanced,
                                   List<BalanceSheetLine> lines) {
    }

    public record IncomeStatementLine(String subjectCode, String subjectName, String category,
                                      BigDecimal amount) {
    }

    public record IncomeStatementView(String periodCode, BigDecimal revenue, BigDecimal cost,
                                      BigDecimal netProfit, List<IncomeStatementLine> lines) {
    }

    // ---------- 应收应付 ----------

    public record ReceivableView(long id, String arNo, Long customerId, String customerName, String sourceType,
                                 Long projectId, String currencyCode, BigDecimal amount, BigDecimal settledAmount,
                                 BigDecimal outstanding, String billDate, String dueDate, String agingBucket,
                                 String status) {
    }

    public record PayableView(long id, String apNo, Long supplierId, String supplierName, String sourceType,
                              Long projectId, String currencyCode, BigDecimal amount, BigDecimal settledAmount,
                              BigDecimal outstanding, String billDate, String dueDate, String status) {
    }

    public record ReceiptCreateRequest(Long customerId, String customerName, String currencyCode,
                                       BigDecimal amount, String payMethod, Long bankAccountId,
                                       LocalDate receiptDate, List<Long> receivableIds) {
    }

    public record PaymentCreateRequest(Long supplierId, String supplierName, String currencyCode,
                                       BigDecimal amount, String payMethod, Long bankAccountId,
                                       LocalDate paymentDate, List<Long> payableIds) {
    }

    public record WriteOffResult(long id, String writeOffNo, long sourceId, BigDecimal amount) {
    }

    public record SettlementDocResult(long id, String docNo, String status, BigDecimal amount,
                                      BigDecimal allocatedAmount, Long voucherId,
                                      List<WriteOffResult> writeOffs) {
    }

    public record ReceiptView(long id, String receiptNo, Long customerId, String currencyCode, BigDecimal amount,
                              String payMethod, String receiptDate, String status, Long voucherId,
                              Instant createdAt) {
    }

    public record PaymentView(long id, String paymentNo, Long supplierId, String currencyCode, BigDecimal amount,
                              String payMethod, String paymentDate, String status, Long voucherId,
                              Instant createdAt) {
    }

    public record WriteOffRequest(BigDecimal amount, LocalDate writeOffDate) {
    }

    public record AgingLine(String partyName, String bucket, BigDecimal amount) {
    }

    public record AgingReport(String type, List<AgingLine> lines, BigDecimal total) {
    }

    // ---------- 报销/预算/发票 ----------

    public record ExpenseItemRequest(LocalDate expenseDate, String expenseCategory, String subjectCode,
                                     BigDecimal amount, BigDecimal taxAmount, Long invoiceId,
                                     Long attachmentId, String remark) {
    }

    public record ExpenseCreateRequest(Long applicantId, String applicantName, Long deptId, String claimType,
                                       String currencyCode, Long projectId, LocalDate claimDate,
                                       List<ExpenseItemRequest> items) {
    }

    public record ExpenseItemView(int lineNo, String expenseDate, String expenseCategory, String subjectCode,
                                  BigDecimal amount, BigDecimal taxAmount, Long invoiceId,
                                  Long attachmentId, String remark) {
    }

    public record ExpenseView(long id, String claimNo, String claimDate, long applicantId,
                              String applicantName, Long deptId, String claimType, String currencyCode,
                              BigDecimal totalAmount, BigDecimal taxAmount, Long budgetId,
                              BigDecimal budgetOccupied, Long projectId, String status, Long payableId,
                              Long voucherId, Instant submittedAt, Instant paidAt,
                              List<ExpenseItemView> items) {
    }

    public record ExpenseActionResult(long id, String claimNo, String status, String workflowInstanceId) {
    }

    public record ExpensePayRequest(LocalDate paymentDate, String payMethod, Long bankAccountId) {
    }

    public record ExpenseRejectRequest(String reason) {
    }

    public record InvoiceCreateRequest(String invoiceCode, String invoiceNumber, String invoiceType,
                                       String buyerName, String sellerName, BigDecimal amount,
                                       BigDecimal taxRate, LocalDate invoiceDate, Long sourceBizId, Long fileId) {
    }

    public record InvoiceView(long id, String invoiceCode, String invoiceNumber, String invoiceType,
                              String buyerName, String sellerName, BigDecimal amount, BigDecimal taxRate,
                              BigDecimal taxAmount, BigDecimal totalAmount, String invoiceDate,
                              String verifyStatus, String status, Long sourceBizId) {
    }

    // ---------- 银行 ----------

    public record BankAccountCreateRequest(String accountNo, String accountName, String bankName,
                                           String bankCode, String currencyCode, String accountType,
                                           String subjectCode, BigDecimal balance) {
    }

    public record BankAccountView(long id, String accountNoMasked, String accountName, String bankName,
                                  String currencyCode, String accountType, String subjectCode,
                                  BigDecimal balance, String status) {
    }

    public record BankTransactionItem(String txnNo, LocalDate txnDate, String direction, BigDecimal amount,
                                      BigDecimal balance, String counterparty, String summary) {
    }

    public record BankTxnImportRequest(Long accountId, List<BankTransactionItem> transactions) {
    }

    public record BankTxnImportResult(long accountId, int imported, int skipped) {
    }

    public record BankTransactionView(long id, long accountId, String txnNo, String txnDate,
                                      String direction, BigDecimal amount, BigDecimal balance,
                                      String counterparty, String summary, String reconcileStatus,
                                      Long reconId) {
    }

    public record ReconcileRequest(Long accountId, String periodCode) {
    }

    public record ReconciliationView(long id, String reconNo, long accountId, String periodCode,
                                     BigDecimal bookBalance, BigDecimal bankBalance, BigDecimal unsettledIn,
                                     BigDecimal unsettledOut, BigDecimal adjustedBalance, String status,
                                     long unmatchedCount) {
    }

    // ---------- 项目 ----------

    public record ProjectCreateRequest(String projectName, String projectType, Long customerId,
                                       String customerName, String contractNo, BigDecimal contractAmount,
                                       BigDecimal budgetAmount, BigDecimal estimatedTotalCost,
                                       LocalDate startDate, LocalDate endDate, Long managerId, Long deptId,
                                       String currencyCode, String description) {
    }

    public record ProjectView(long id, String projectNo, String projectName, String projectType,
                              Long customerId, String customerName, String contractNo,
                              BigDecimal contractAmount, BigDecimal budgetAmount,
                              BigDecimal estimatedTotalCost, BigDecimal actualCost,
                              BigDecimal recognizedRevenue, String startDate, String endDate,
                              Long managerId, Long deptId, String currencyCode, String status,
                              String description) {
    }

    public record ProjectApproveResult(long id, String projectNo, String status, String workflowInstanceId) {
    }

    public record MemberCreateRequest(Long employeeId, String employeeName, String memberRole,
                                      BigDecimal allocationPct, BigDecimal costRate, LocalDate joinDate) {
    }

    public record MemberView(long id, long employeeId, String employeeName, String memberRole,
                             BigDecimal allocationPct, BigDecimal costRate, String joinDate, String status) {
    }

    public record MilestoneCreateRequest(String milestoneName, LocalDate planDate, String deliverable,
                                         BigDecimal weight) {
    }

    public record MilestoneView(long id, String milestoneName, String planDate, String actualDate,
                                String deliverable, BigDecimal weight, String status) {
    }

    public record TimesheetCreateRequest(Long employeeId, String employeeName, Long projectId,
                                         Long milestoneId, LocalDate workDate, BigDecimal hours,
                                         String taskDesc) {
    }

    public record TimesheetView(long id, String timesheetNo, long employeeId, String employeeName,
                                long projectId, String workDate, BigDecimal hours, BigDecimal costRate,
                                BigDecimal costAmount, String status, String rejectReason) {
    }

    public record TimesheetApproveRequest(List<Long> timesheetIds) {
    }

    public record TimesheetApproveResult(int approvedCount, int rejectedCount, BigDecimal costTotal,
                                         String workflowInstanceId) {
    }

    public record TimesheetRejectRequest(String reason) {
    }

    public record CostCollectionRequest(Long projectId, String costType, Long sourceBizId,
                                        String subjectCode, BigDecimal amount, LocalDate costDate,
                                        String description) {
    }

    public record CostView(long id, String costNo, long projectId, String costType, Long sourceBizId,
                           BigDecimal amount, String costDate, String description) {
    }

    public record RevenueRecognizeRequest(String periodCode, BigDecimal amount) {
    }

    public record RevenueRecognizeResult(long projectId, String method, BigDecimal recognizedRevenue,
                                         BigDecimal delta, Long voucherId) {
    }

    public record SettlementCreateRequest(BigDecimal settlementAmount, String currencyCode, String remark) {
    }

    public record SettlementView(long id, String settlementNo, long projectId, BigDecimal settlementAmount,
                                 BigDecimal settledAmount, String currencyCode, String status,
                                 Long receivableId, Long voucherId, Instant settledAt) {
    }

    public record ProjectDetail(ProjectView project, List<MemberView> members,
                                List<MilestoneView> milestones, List<CostView> costs,
                                List<SettlementView> settlements, List<TimesheetView> timesheets) {
    }

    public record ProfitabilityView(long projectId, BigDecimal contractAmount, BigDecimal actualCost,
                                    BigDecimal recognizedRevenue, BigDecimal grossProfit,
                                    BigDecimal grossMargin, BigDecimal costBudgetUsage,
                                    List<String> warnings) {
    }

    // ---------- 资产 ----------

    public record AssetCategoryCreateRequest(String categoryCode, String categoryName, Integer usefulLifeMonths,
                                             BigDecimal residualRate, String depreciationMethod,
                                             String assetAccountCode, String depreciationAccountCode,
                                             String expenseAccountCode) {
    }

    public record AssetCategoryView(long id, String categoryCode, String categoryName, int usefulLifeMonths,
                                    BigDecimal residualRate, String depreciationMethod,
                                    String assetAccountCode, String depreciationAccountCode,
                                    String expenseAccountCode, String status) {
    }

    public record AssetCardCreateRequest(String assetName, Long categoryId, String specification,
                                         String location, Long deptId, Long custodianId, LocalDate enableDate,
                                         BigDecimal originalValue, Boolean itAssetFlag, String source,
                                         String deviceType, String brandModel, String serialNo,
                                         String ipAddress, String macAddress, String osVersion,
                                         List<String> softwareList) {
    }

    public record AssetCardView(long id, String assetNo, String assetName, long categoryId,
                                String specification, String location, Long deptId, Long custodianId,
                                String enableDate, BigDecimal originalValue,
                                BigDecimal accumulatedDepreciation, BigDecimal netValue,
                                BigDecimal residualRate, int usefulLifeMonths, String depreciationMethod,
                                String status, boolean itAssetFlag) {
    }

    public record DepreciationRunRequest(Long ledgerId, String periodCode) {
    }

    public record DepreciationRunResult(String batchNo, String periodCode, int assetCount,
                                        BigDecimal totalAmount, Long voucherId) {
    }

    public record DisposeRequest(BigDecimal disposalIncome, LocalDate disposalDate, String reason) {
    }

    public record DisposeResult(long assetId, String changeNo, BigDecimal gainLoss, Long voucherId,
                                String status) {
    }

    public record StocktakingCreateRequest(String stocktakingName, String scopeType, Long deptId,
                                           LocalDate planStartDate, LocalDate planEndDate) {
    }

    public record StocktakingView(long id, String stocktakingNo, String stocktakingName, String scopeType,
                                  Long deptId, String planStartDate, String planEndDate,
                                  String actualEndDate, int totalCount, int normalCount,
                                  int surplusCount, int deficitCount, String status) {
    }

    public record StocktakingItemRequest(Long assetId, String actualLocation, Long actualCustodianId,
                                         String result, String remark) {
    }

    public record StocktakingItemView(long id, Long assetId, String assetNo, String assetName,
                                      String expectedLocation, String actualLocation, String result,
                                      String remark, Instant countedAt) {
    }

    public record StocktakingDetail(StocktakingView stocktaking, List<StocktakingItemView> items) {
    }

    public record ItAssetBorrowRequest(Long borrowerId, LocalDate dueDate) {
    }

    public record ItAssetView(long id, long assetId, String deviceType, String brandModel, String serialNo,
                              String ipAddress, String borrowStatus, Long borrowerId, String borrowDate,
                              String dueDate, String cmdbCiId, String syncStatus) {
    }

    public record ItAssetSyncRequest(String cmdbCiId, Boolean success) {
    }

    // ---------- 碳/ESG ----------

    public record FactorCreateRequest(String factorCode, String factorName, String scope, String activityType,
                                      String activityUnit, BigDecimal factorValue, String factorUnit,
                                      String factorVersion, String source, LocalDate effectiveFrom,
                                      LocalDate effectiveTo) {
    }

    public record FactorView(long id, String factorCode, String factorName, String scope, String activityType,
                             String activityUnit, BigDecimal factorValue, String factorUnit,
                             String factorVersion, String source, String effectiveFrom, String effectiveTo,
                             String status) {
    }

    public record CarbonActivityCreateRequest(Long orgId, Long deptId, String activityType, String scope,
                                              LocalDate activityDate, BigDecimal amount, String unit,
                                              Long factorId, String dataSource, Long evidenceFileId) {
    }

    public record CarbonActivityView(long id, String activityNo, Long orgId, Long deptId,
                                     String activityType, String scope, String activityDate, String periodCode,
                                     BigDecimal amount, String unit, Long factorId, String dataSource,
                                     String status, Long verifiedBy, Long lockedBy) {
    }

    public record CarbonCalculateRequest(Long orgId, String periodCode, String scope, Boolean recalc) {
    }

    public record CarbonCalculateResult(String batchNo, String periodCode, int activityCount,
                                        BigDecimal totalEmission, Map<String, BigDecimal> byScope) {
    }

    public record TargetCreateRequest(String targetName, Integer targetYear, Integer baseYear,
                                      BigDecimal baseEmission, BigDecimal targetEmission, Long ownerId) {
    }

    public record TargetView(long id, String targetNo, String targetName, int targetYear, int baseYear,
                             BigDecimal baseEmission, BigDecimal targetEmission, BigDecimal reductionRate,
                             BigDecimal currentEmission, BigDecimal progress, String status) {
    }

    public record EsgIndicatorCreateRequest(String indicatorCode, String indicatorName, String category,
                                            String unit, Integer periodYear, BigDecimal targetValue,
                                            BigDecimal actualValue, String dataSource) {
    }

    public record EsgIndicatorView(long id, String indicatorCode, String indicatorName, String category,
                                   String unit, int periodYear, BigDecimal targetValue, BigDecimal actualValue,
                                   String dataSource, String status) {
    }

    public record EsgReportCreateRequest(String reportName, Integer periodYear, String reportType,
                                         String reportBoundary) {
    }

    public record EsgReportView(long id, String reportNo, String reportName, int periodYear,
                                String reportType, String reportBoundary, String status, String summary,
                                Instant publishedAt) {
    }

    public record EsgReportExport(String fileName, String contentType, String content) {
    }

    // ---------- 云财收口：科目/模板/发票/币种/规则/付款计划 ----------

    public record SubjectUpdateRequest(String subjectName, String subjectCategory, String balanceDirection,
                                       Boolean isLeaf, String auxiliaryTypes) {
    }

    public record VoucherTemplateUpsertRequest(String templateCode, String templateName, String bizType,
                                               String debitSubjectCode, String creditSubjectCode,
                                               Map<String, Object> auxiliaryMapping, String conditionExpr) {
    }

    public record VoucherTemplateView(long id, String templateCode, String templateName, String bizType,
                                      String debitSubjectCode, String creditSubjectCode,
                                      Map<String, Object> auxiliaryMapping, String conditionExpr, String status) {
    }

    public record VoucherImportRequest(Long ledgerId, List<VoucherCreateRequest> vouchers) {
    }

    public record CurrencyUpsertRequest(String currencyCode, String currencyName, String symbol,
                                        Integer decimalPlaces) {
    }

    public record CurrencyView(long id, String currencyCode, String currencyName, String symbol,
                               int decimalPlaces, String status) {
    }

    public record ExchangeRateUpsertRequest(String fromCurrency, String toCurrency, BigDecimal rate,
                                            LocalDate effectiveDate, String source) {
    }

    public record ExchangeRateView(long id, String fromCurrency, String toCurrency, BigDecimal rate,
                                   String effectiveDate, String source) {
    }

    public record BusinessRuleUpsertRequest(String ruleCode, String ruleName, String ruleType,
                                            Map<String, Object> config) {
    }

    public record BusinessRuleView(long id, String ruleCode, String ruleName, String ruleType,
                                   Map<String, Object> config, String status) {
    }

    public record PaymentPlanLine(long payableId, String apNo, String supplierName, String currencyCode,
                                  String dueDate, String plannedPayDate, BigDecimal amount,
                                  BigDecimal settledAmount, BigDecimal outstanding) {
    }
}
