package com.fenglin.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    void exposesTenantDuringRequestAndClearsAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantHeaders.REQUEST_ID, "req-1");
        request.addHeader(TenantHeaders.TENANT_ID, "42");
        request.addHeader(TenantHeaders.USER_ID, "7");
        request.addHeader(TenantHeaders.CLIENT_TYPE, "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<TenantContext.Snapshot> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(TenantContext.get()));

        assertThat(seen.get().tenantId()).isEqualTo(42L);
        assertThat(seen.get().userId()).isEqualTo(7L);
        assertThat(seen.get().clientType()).isEqualTo("ADMIN");
        assertThat(seen.get().requestId()).isEqualTo("req-1");
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void rejectsNonNumericTenantHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantHeaders.TENANT_ID, "abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("chain must not be invoked");
        });

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void crossTenantFlagIsVisibleOnlyWhenInjected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantHeaders.CROSS_TENANT, "on");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> crossTenant = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> crossTenant.set(TenantContext.isCrossTenant()));

        assertThat(crossTenant.get()).isTrue();
        assertThat(TenantContext.isCrossTenant()).isFalse();
    }
}
