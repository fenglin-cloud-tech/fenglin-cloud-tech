package com.fenglin.service.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.service.service.CsatAssetService;
import com.fenglin.service.service.RpaService;
import com.fenglin.service.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务运营报表：工单、SLA、坐席负荷、满意度、RPA。
 */
@RestController
@RequestMapping("/api/service/v1/reports")
public class ReportController {

    private final TicketService ticketService;
    private final CsatAssetService csatAssetService;
    private final RpaService rpaService;

    public ReportController(TicketService ticketService, CsatAssetService csatAssetService, RpaService rpaService) {
        this.ticketService = ticketService;
        this.csatAssetService = csatAssetService;
        this.rpaService = rpaService;
    }

    @GetMapping("/tickets")
    public ApiResponse<Map<String, Object>> tickets(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        PermissionGuard.require(permissions, "service:report:view");
        return ApiResponse.success(ticketService.ticketReport(from, to));
    }

    @GetMapping("/sla")
    public ApiResponse<Map<String, Object>> sla(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        PermissionGuard.require(permissions, "service:report:view");
        return ApiResponse.success(ticketService.slaReport(from, to));
    }

    @GetMapping("/workload")
    public ApiResponse<List<Map<String, Object>>> workload(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        PermissionGuard.require(permissions, "service:report:view");
        return ApiResponse.success(ticketService.workload(from, to));
    }

    @GetMapping("/csat")
    public ApiResponse<Map<String, Object>> csat(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        PermissionGuard.require(permissions, "service:csat:view");
        return ApiResponse.success(csatAssetService.csatReport(from, to));
    }

    /** 满意度逐单明细：工单号、评分、评论、渠道与提交时间，支持时间区间与分页。 */
    @GetMapping("/csat/detail")
    public ApiResponse<PageResult<Map<String, Object>>> csatDetail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, "service:csat:view");
        return ApiResponse.success(csatAssetService.csatDetail(from, to, page, size));
    }

    @GetMapping("/rpa")
    public ApiResponse<Map<String, Object>> rpa(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, "service:rpa:report:view");
        return ApiResponse.success(rpaService.summary());
    }

    @GetMapping("/robot-utilization")
    public ApiResponse<List<Map<String, Object>>> utilization(
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        PermissionGuard.require(permissions, "service:rpa:report:view");
        return ApiResponse.success(rpaService.utilization());
    }
}
