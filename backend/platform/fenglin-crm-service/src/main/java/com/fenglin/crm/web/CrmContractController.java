package com.fenglin.crm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.crm.service.ContractService;
import com.fenglin.crm.web.dto.CrmDtos.ContractCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.ContractView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合同接口。01_丰麟云销_05 未定义合同创建/查询接口（4.14 仅有表设计），
 * 本批按最小可用语义补充，并标注为扩展。
 */
@RestController
public class CrmContractController {

    private final ContractService contractService;

    public CrmContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    /** 扩展：合同建档 + 回款计划一次性提交。 */
    @PostMapping("/api/crm/v1/contracts")
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
}
