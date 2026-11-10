package com.fenglin.finance.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.finance.service.CarbonService;
import com.fenglin.finance.support.FinanceContext;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonActivityCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonActivityView;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonCalculateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.CarbonCalculateResult;
import com.fenglin.finance.web.dto.FinanceDtos.EsgIndicatorCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.EsgIndicatorView;
import com.fenglin.finance.web.dto.FinanceDtos.EsgReportCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.EsgReportView;
import com.fenglin.finance.web.dto.FinanceDtos.FactorCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.FactorView;
import com.fenglin.finance.web.dto.FinanceDtos.TargetCreateRequest;
import com.fenglin.finance.web.dto.FinanceDtos.TargetView;
import org.springframework.http.MediaType;
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
 * D4 碳排放核算与 ESG 披露接口。
 */
@RestController
@RequestMapping("/api/finance/v1")
public class FinanceCarbonController {

    private static final String FACTOR = "tenant:finance:carbon:factor:manage";
    private static final String TARGET = "tenant:finance:carbon:target:manage";
    private static final String ESG_PUBLISH = "tenant:finance:esg:report:publish";
    private static final String CARBON_SUBMIT = "biz:finance:carbon:submit";
    private static final String CARBON_VERIFY = "biz:finance:carbon:verify";
    private static final String CARBON_CALCULATE = "biz:finance:carbon:calculate";
    private static final String ESG_MANAGE = "biz:finance:esg:manage";
    private static final String REPORT_VIEW = "biz:finance:report:view";

    private final CarbonService carbonService;

    public FinanceCarbonController(CarbonService carbonService) {
        this.carbonService = carbonService;
    }

    @PostMapping("/carbon/factors")
    public ApiResponse<FactorView> createFactor(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody FactorCreateRequest request) {
        PermissionGuard.require(permissions, FACTOR);
        return ApiResponse.success(carbonService.createFactor(request));
    }

    @GetMapping("/carbon/factors")
    public ApiResponse<List<FactorView>> listFactors(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "activityType", required = false) String activityType) {
        PermissionGuard.require(permissions, CARBON_CALCULATE);
        return ApiResponse.success(carbonService.listFactors(scope, activityType));
    }

    @PostMapping("/carbon/activities")
    public ApiResponse<CarbonActivityView> createActivity(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody CarbonActivityCreateRequest request) {
        PermissionGuard.require(permissions, CARBON_SUBMIT);
        return ApiResponse.success(carbonService.createActivity(request));
    }

    @GetMapping("/carbon/activities/my")
    public ApiResponse<List<CarbonActivityView>> myActivities(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodCode", required = false) String periodCode,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, CARBON_SUBMIT);
        return ApiResponse.success(carbonService.listMine(periodCode, scope, status,
                FinanceContext.size(size), FinanceContext.offset(page, size)));
    }

    @GetMapping("/carbon/activities")
    public ApiResponse<List<CarbonActivityView>> listActivities(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodCode", required = false) String periodCode,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, CARBON_SUBMIT);
        return ApiResponse.success(carbonService.listActivities(periodCode, scope, status,
                FinanceContext.size(size), FinanceContext.offset(page, size)));
    }

    @PostMapping("/carbon/activities/{id}/verify")
    public ApiResponse<CarbonActivityView> verify(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, CARBON_VERIFY);
        return ApiResponse.success(carbonService.verify(id));
    }

    @PostMapping("/carbon/activities/{id}/lock")
    public ApiResponse<CarbonActivityView> lock(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, CARBON_VERIFY);
        return ApiResponse.success(carbonService.lock(id));
    }

    @PostMapping("/carbon/calculate")
    public ApiResponse<CarbonCalculateResult> calculate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody CarbonCalculateRequest request) {
        PermissionGuard.require(permissions, CARBON_CALCULATE);
        return ApiResponse.success(carbonService.calculate(request));
    }

    @PostMapping("/carbon/targets")
    public ApiResponse<TargetView> createTarget(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody TargetCreateRequest request) {
        PermissionGuard.require(permissions, TARGET);
        return ApiResponse.success(carbonService.createTarget(request));
    }

    @GetMapping("/carbon/targets")
    public ApiResponse<List<TargetView>> listTargets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, CARBON_CALCULATE);
        return ApiResponse.success(carbonService.listTargets());
    }

    @PostMapping("/esg/indicators")
    public ApiResponse<EsgIndicatorView> createIndicator(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody EsgIndicatorCreateRequest request) {
        PermissionGuard.require(permissions, ESG_MANAGE);
        return ApiResponse.success(carbonService.createIndicator(request));
    }

    @GetMapping("/esg/indicators")
    public ApiResponse<List<EsgIndicatorView>> listIndicators(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "periodYear", required = false) Integer periodYear) {
        PermissionGuard.require(permissions, REPORT_VIEW);
        return ApiResponse.success(carbonService.listIndicators(periodYear));
    }

    @PostMapping("/esg/reports")
    public ApiResponse<EsgReportView> createReport(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody EsgReportCreateRequest request) {
        PermissionGuard.require(permissions, ESG_MANAGE);
        return ApiResponse.success(carbonService.createReport(request));
    }

    @GetMapping("/esg/reports")
    public ApiResponse<List<EsgReportView>> listReports(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, REPORT_VIEW);
        return ApiResponse.success(carbonService.listReports(FinanceContext.size(size),
                FinanceContext.offset(page, size)));
    }

    @PostMapping("/esg/reports/{id}/publish")
    public ApiResponse<EsgReportView> publish(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, ESG_PUBLISH);
        return ApiResponse.success(carbonService.publish(id));
    }

    @GetMapping(value = "/esg/reports/{id}/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public String export(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, REPORT_VIEW);
        return carbonService.exportReport(id);
    }
}
