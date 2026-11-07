package com.fenglin.common.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successUsesZeroCode() {
        ApiResponse<String> response = ApiResponse.success("ok");
        assertThat(response.code()).isEqualTo("0");
        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void failureCarriesErrorCodeAndMessage() {
        ApiResponse<Void> response = ApiResponse.failure(CommonErrorCode.TENANT_REQUIRED);
        assertThat(response.code()).isEqualTo("FL-COMMON-0421");
        assertThat(response.message()).isEqualTo("缺少有效的租户上下文");
        assertThat(response.data()).isNull();
    }

    @Test
    void pageResultCopiesRecords() {
        PageResult<String> page = PageResult.of(new java.util.ArrayList<>(java.util.List.of("a")), 1, 1, 20);
        assertThat(page.records()).containsExactly("a");
    }
}
