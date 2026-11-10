package com.fenglin.talent.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.talent.mapper.EmployeeMapper;
import com.fenglin.talent.support.TalentContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 业务域内部接口：IAM 绑定员工前校验员工是否属于当前租户。
 */
@RestController
public class TalentInternalController {

    private final EmployeeMapper employeeMapper;

    public TalentInternalController(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Transactional(readOnly = true)
    @GetMapping("/internal/v1/talent/employees/{id}/exists")
    public ApiResponse<Map<String, Object>> employeeExists(@PathVariable long id) {
        boolean exists = employeeMapper.countById(TalentContext.tenantId(), id) > 0;
        return ApiResponse.success(Map.of("exists", exists));
    }
}
