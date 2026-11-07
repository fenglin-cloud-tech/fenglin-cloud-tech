package com.fenglin.common.security;

import com.fenglin.common.core.BizException;
import com.fenglin.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientTypeGuardTest {

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    void allowsMatchingClientType() {
        TenantContext.set(1L, 1L, "ADMIN", "req", false);
        assertThatCode(() -> ClientTypeGuard.require(ClientType.ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMismatchedClientType() {
        TenantContext.set(1L, 1L, "PORTAL", "req", false);
        assertThatThrownBy(() -> ClientTypeGuard.require(ClientType.ADMIN))
                .isInstanceOf(BizException.class);
    }
}
