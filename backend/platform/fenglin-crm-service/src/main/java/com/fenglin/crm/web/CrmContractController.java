package com.fenglin.crm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.crm.service.ContractService;
import com.fenglin.crm.web.dto.CrmDtos.ContractAmendRequest;
import com.fenglin.crm.web.dto.CrmDtos.ContractChangeView;
import com.fenglin.crm.web.dto.CrmDtos.ContractCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.ContractRenewRequest;
import com.fenglin.crm.web.dto.CrmDtos.ContractView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同接口。01_丰麟云销_05 仅定义 CRM-API-051 创建服务合同（/api/crm/v1/service-contracts），
 * 未定义合同查询、变更、续签；本批按最小可用语义补充，并统一标注为扩展，
 * 同时保留 /api/crm/v1/contracts 别名以兼容第一批调用方。
 */
@RestController
public class CrmContractController {

    private final ContractService contractService;

    public CrmContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    /** 扩展：合同建档 + 回款计划一次性提交；路径与 CRM-API-051 对齐。 */
    @PostMapping({"/api/crm/v1/contracts", "/api/crm/v1/service-contracts"})
    public ApiResponse<ContractView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ContractCreateRequest request) {
        PermissionGuard.require(permissions, "crm:contract:manage");
        return ApiResponse.success(contractService.create(request));
    }

    /** 扩展：合同详情（含回款计划）。 */
    @GetMapping("/api/crm/v1/contracts/{id}")
    public ApiResponse<ContractView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:contract:view");
        return ApiResponse.success(contractService.detail(id));
    }

    /** 扩展：合同变更（金额/服务等级/服务包/到期日 + 替换未回款计划）。 */
    @PostMapping("/api/crm/v1/contracts/{id}/changes")
    public ApiResponse<ContractView> amend(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody ContractAmendRequest request) {
        PermissionGuard.require(permissions, "crm:contract:manage");
        return ApiResponse.success(contractService.amend(id, request));
    }

    /** 扩展：合同变更/续签记录。 */
    @GetMapping("/api/crm/v1/contracts/{id}/changes")
    public ApiResponse<List<ContractChangeView>> changes(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:contract:view");
        return ApiResponse.success(contractService.changes(id));
    }

    /** 扩展：合同续签，生成新合同并保留来源合同。 */
    @PostMapping("/api/crm/v1/contracts/{id}/renew")
    public ApiResponse<ContractView> renew(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody ContractRenewRequest request) {
        PermissionGuard.require(permissions, "crm:contract:manage");
        return ApiResponse.success(contractService.renew(id, request));
    }
}
