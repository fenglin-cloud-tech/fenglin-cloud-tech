package com.fenglin.talent.service;

import com.fenglin.common.core.BizException;
import com.fenglin.talent.TalentErrorCode;
import com.fenglin.talent.client.IamBindingClient;
import com.fenglin.talent.support.TalentContext;
import org.springframework.stereotype.Service;

/**
 * 员工自助数据的身份解析：账号 → hub_user.employee_id 绑定，不再拿 userId 当 employeeId。
 */
@Service
public class EmployeeBindingService {

    private final IamBindingClient iamBindingClient;

    public EmployeeBindingService(IamBindingClient iamBindingClient) {
        this.iamBindingClient = iamBindingClient;
    }

    public Long currentEmployeeIdOrNull() {
        return iamBindingClient.findEmployeeId(TalentContext.tenantId(), TalentContext.userId());
    }

    public long requireCurrentEmployeeId() {
        Long employeeId = currentEmployeeIdOrNull();
        if (employeeId == null) {
            throw new BizException(TalentErrorCode.EMPLOYEE_NOT_BOUND);
        }
        return employeeId;
    }
}
