package com.fenglin.crm.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.PageResult;
import com.fenglin.common.security.PermissionGuard;
import com.fenglin.crm.support.CrmContext;
import com.fenglin.crm.service.ContractService;
import com.fenglin.crm.service.CustomerService;
import com.fenglin.crm.web.dto.CrmDtos.ContactCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.ContactView;
import com.fenglin.crm.web.dto.CrmDtos.ContractView;
import com.fenglin.crm.web.dto.CrmDtos.CustomerCreateRequest;
import com.fenglin.crm.web.dto.CrmDtos.CustomerDetailView;
import com.fenglin.crm.web.dto.CrmDtos.CustomerOwnerRequest;
import com.fenglin.crm.web.dto.CrmDtos.CustomerView;
import com.fenglin.crm.web.dto.CrmDtos.DuplicateCheckRequest;
import com.fenglin.crm.web.dto.CrmDtos.DuplicateCheckResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户与联系人接口（CRM-API-010 ~ 013）。
 * 扩展：CRM 文档未定义"客户建档 / 查重 / 归属变更"三个接口，按最小可用语义补充并在此标注。
 */
@RestController
public class CrmCustomerController {

    private final CustomerService customerService;
    private final ContractService contractService;

    public CrmCustomerController(CustomerService customerService, ContractService contractService) {
        this.customerService = customerService;
        this.contractService = contractService;
    }

    /** 扩展：客户建档（文档仅定义转换建客户，无独立建档接口）。 */
    @PostMapping("/api/crm/v1/customers")
    public ApiResponse<CustomerView> create(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody CustomerCreateRequest request) {
        PermissionGuard.require(permissions, "crm:customer:create");
        return ApiResponse.success(customerService.create(request));
    }

    /** 扩展：客户查重（手机号哈希精确 + 名称模糊）。 */
    @PostMapping("/api/crm/v1/customers/check-duplicate")
    public ApiResponse<DuplicateCheckResult> checkDuplicate(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody DuplicateCheckRequest request) {
        PermissionGuard.require(permissions, "crm:customer:view");
        return ApiResponse.success(customerService.checkDuplicate(request));
    }

    @GetMapping("/api/crm/v1/customers")
    public ApiResponse<PageResult<CustomerView>> list(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "segment", required = false) String segment,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, "crm:customer:view");
        return ApiResponse.success(customerService.list(keyword, ownerId, segment, status, page, size));
    }

    /** 员工自助：只返回 owner_id = 当前登录用户的客户。 */
    @GetMapping("/api/crm/v1/customers/my")
    public ApiResponse<PageResult<CustomerView>> myCustomers(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "segment", required = false) String segment,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        PermissionGuard.require(permissions, "crm:customer:self:view");
        return ApiResponse.success(customerService.list(keyword, CrmContext.userId(), segment, status, page, size));
    }

    /** 员工自助：仅允许读取本人的客户详情。 */
    @GetMapping("/api/crm/v1/customers/my/{id}")
    public ApiResponse<CustomerDetailView> myCustomer(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:customer:self:view");
        return ApiResponse.success(customerService.detail(id, CrmContext.userId()));
    }

    @GetMapping("/api/crm/v1/customers/{id}")
    public ApiResponse<CustomerDetailView> detail(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:customer:view");
        return ApiResponse.success(customerService.detail(id));
    }

    /** 扩展：客户归属变更。 */
    @PatchMapping("/api/crm/v1/customers/{id}/owner")
    public ApiResponse<CustomerView> changeOwner(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id,
            @RequestBody CustomerOwnerRequest request) {
        PermissionGuard.require(permissions, "crm:customer:manage");
        return ApiResponse.success(customerService.changeOwner(id, request));
    }

    @GetMapping("/api/crm/v1/customers/{id}/contacts")
    public ApiResponse<List<ContactView>> contacts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:contact:view");
        return ApiResponse.success(customerService.contacts(id));
    }

    @PostMapping("/api/crm/v1/contacts")
    public ApiResponse<ContactView> createContact(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestBody ContactCreateRequest request) {
        PermissionGuard.require(permissions, "crm:contact:manage");
        return ApiResponse.success(customerService.createContact(request));
    }

    /** 扩展：客户合同列表（回款闭环下一批补充）。 */
    @GetMapping("/api/crm/v1/customers/{id}/contracts")
    public ApiResponse<List<ContractView>> contracts(
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @PathVariable("id") long id) {
        PermissionGuard.require(permissions, "crm:contract:view");
        return ApiResponse.success(contractService.listByCustomer(id));
    }
}
