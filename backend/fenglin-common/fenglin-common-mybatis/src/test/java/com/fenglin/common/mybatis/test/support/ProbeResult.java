package com.fenglin.common.mybatis.test.support;

import java.util.List;

public record ProbeResult(List<Long> ids, int backendPid, String currentTenantSetting) {
}
