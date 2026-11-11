package com.fenglin.finance.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import com.fenglin.finance.service.TenantFinanceInitService;
import com.fenglin.finance.web.dto.FinanceDtos.TenantInitView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间内部接口，仅供 tenant-service 开通/补齐租户时调用。
 *
 * <p>不经过网关对外暴露，用共享内部令牌校验；账套类接口的用户态权限不适用。</p>
 */
@RestController
@RequestMapping("/api/finance/v1/internal")
public class FinanceInternalController {

    private final TenantFinanceInitService initService;
    private final String internalToken;

    public FinanceInternalController(TenantFinanceInitService initService,
                                     @Value("${fenglin.internal.token:fenglin-internal-dev}") String internalToken) {
        this.initService = initService;
        this.internalToken = internalToken;
    }

    @PostMapping("/tenants/{tenantId}/finance-init")
    public ApiResponse<TenantInitView> initTenant(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable("tenantId") long tenantId,
            @RequestBody(required = false) InitRequest request) {
        if (token == null || !token.equals(internalToken)) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        String tenantName = request == null ? null : request.tenantName();
        return ApiResponse.success(initService.initialize(tenantId, tenantName));
    }

    public record InitRequest(String tenantName) {
    }
}
