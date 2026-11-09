package com.fenglin.rnd.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.rnd.service.MetricService;
import com.fenglin.rnd.web.dto.RndDtos.AlertCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.AlertUpdateRequest;
import com.fenglin.rnd.web.dto.RndDtos.AlertView;
import com.fenglin.rnd.web.dto.RndDtos.ContributionView;
import com.fenglin.rnd.web.dto.RndDtos.DoraView;
import com.fenglin.rnd.web.dto.RndDtos.InsightView;
import com.fenglin.rnd.web.dto.RndDtos.MetricCaliberCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.MetricCaliberView;
import com.fenglin.rnd.web.dto.RndDtos.MetricComputeRequest;
import com.fenglin.rnd.web.dto.RndDtos.MetricComputeResult;
import com.fenglin.rnd.web.dto.RndDtos.MetricConfigCreateRequest;
import com.fenglin.rnd.web.dto.RndDtos.MetricConfigUpdateRequest;
import com.fenglin.rnd.web.dto.RndDtos.MetricConfigView;
import com.fenglin.rnd.web.dto.RndDtos.MetricDataQualityView;
import com.fenglin.rnd.web.dto.RndDtos.MetricValueView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * E2 效能度量、洞察大盘、度量配置与预警接口。
 */
@RestController
@RequestMapping("/api/rnd/v1")
public class RndMetricController {

    private static final String METRIC_VIEW = "biz:devops:metric:view";
    private static final String INSIGHT_VIEW = "biz:devops:metric:insight";
    private static final String CONFIG_MANAGE = "tenant:devops:metric:manage";
    private static final String ALERT_MANAGE = "tenant:devops:alert:manage";

    private final MetricService metricService;

    public RndMetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    @PostMapping("/metrics/compute")
    public ApiResponse<MetricComputeResult> compute(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody MetricComputeRequest request) {
        PermissionGuard.require(permissions, METRIC_VIEW);
        return ApiResponse.success(metricService.compute(request));
    }

    @GetMapping("/metrics")
    public ApiResponse<List<MetricValueView>> snapshots(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "periodType", required = false) String periodType,
            @RequestParam(value = "periodKey", required = false) String periodKey) {
        PermissionGuard.require(permissions, METRIC_VIEW);
        return ApiResponse.success(metricService.snapshots(productId, periodType, periodKey));
    }

    @GetMapping("/metrics/contributions")
    public ApiResponse<List<ContributionView>> contributions(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "periodType", required = false) String periodType,
            @RequestParam(value = "periodKey", required = false) String periodKey) {
        PermissionGuard.require(permissions, METRIC_VIEW);
        return ApiResponse.success(metricService.contributions(productId, periodType, periodKey));
    }

    @GetMapping("/metrics/dora")
    public ApiResponse<DoraView> dora(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "periodType", required = false) String periodType,
            @RequestParam(value = "periodKey", required = false) String periodKey) {
        PermissionGuard.require(permissions, METRIC_VIEW);
        return ApiResponse.success(metricService.dora(productId, periodType, periodKey));
    }

    @GetMapping("/metrics/insight")
    public ApiResponse<InsightView> insight(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "periodType", required = false) String periodType,
            @RequestParam(value = "periodKey", required = false) String periodKey) {
        PermissionGuard.require(permissions, INSIGHT_VIEW);
        return ApiResponse.success(metricService.insight(productId, periodType, periodKey));
    }

    @GetMapping("/metric-configs")
    public ApiResponse<List<MetricConfigView>> configs(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, METRIC_VIEW);
        return ApiResponse.success(metricService.configs());
    }

    @PostMapping("/metric-configs")
    public ApiResponse<MetricConfigView> createConfig(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody MetricConfigCreateRequest request) {
        PermissionGuard.require(permissions, CONFIG_MANAGE);
        return ApiResponse.success(metricService.createConfig(request));
    }

    @PutMapping("/metric-configs/{metricCode}")
    public ApiResponse<MetricConfigView> updateConfig(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("metricCode") String metricCode,
            @RequestBody MetricConfigUpdateRequest request) {
        PermissionGuard.require(permissions, CONFIG_MANAGE);
        return ApiResponse.success(metricService.updateConfig(metricCode, request));
    }

    @GetMapping("/metric-calibers")
    public ApiResponse<List<MetricCaliberView>> calibers(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "metricCode", required = false) String metricCode) {
        PermissionGuard.require(permissions, CONFIG_MANAGE);
        return ApiResponse.success(metricService.calibers(metricCode));
    }

    @PostMapping("/metric-calibers")
    public ApiResponse<MetricCaliberView> publishCaliber(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody MetricCaliberCreateRequest request) {
        PermissionGuard.require(permissions, CONFIG_MANAGE);
        return ApiResponse.success(metricService.publishCaliber(request));
    }

    @GetMapping("/metric-data-quality")
    public ApiResponse<MetricDataQualityView> dataQuality(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "metricCode", required = false) String metricCode,
            @RequestParam(value = "periodType", required = false) String periodType,
            @RequestParam(value = "periodKey", required = false) String periodKey) {
        PermissionGuard.require(permissions, CONFIG_MANAGE);
        return ApiResponse.success(metricService.dataQuality(metricCode, periodType, periodKey));
    }

    @GetMapping("/metric-alerts")
    public ApiResponse<List<AlertView>> alerts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "metricCode", required = false) String metricCode) {
        PermissionGuard.require(permissions, ALERT_MANAGE);
        return ApiResponse.success(metricService.alerts(status, metricCode));
    }

    @PostMapping("/metric-alerts")
    public ApiResponse<AlertView> createAlert(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody AlertCreateRequest request) {
        PermissionGuard.require(permissions, ALERT_MANAGE);
        return ApiResponse.success(metricService.createAlert(request));
    }

    @PutMapping("/metric-alerts/{id}")
    public ApiResponse<AlertView> updateAlert(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody AlertUpdateRequest request) {
        PermissionGuard.require(permissions, ALERT_MANAGE);
        return ApiResponse.success(metricService.updateAlert(id, request));
    }

    @PostMapping("/metric-alerts/{id}/resolve")
    public ApiResponse<AlertView> resolveAlert(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, ALERT_MANAGE);
        return ApiResponse.success(metricService.resolveAlert(id));
    }

    @PostMapping("/metrics/alerts/evaluate")
    public ApiResponse<List<AlertView>> evaluateAlerts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam("productId") long productId,
            @RequestParam(value = "periodType", required = false) String periodType,
            @RequestParam(value = "periodKey", required = false) String periodKey) {
        PermissionGuard.require(permissions, ALERT_MANAGE);
        return ApiResponse.success(metricService.evaluateAlerts(productId, periodType, periodKey));
    }
}
