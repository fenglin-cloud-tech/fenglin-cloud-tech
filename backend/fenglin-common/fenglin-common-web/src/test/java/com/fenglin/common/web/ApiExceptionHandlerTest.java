package com.fenglin.common.web;

import com.fenglin.common.core.ApiResponse;
import com.fenglin.common.core.BizException;
import com.fenglin.common.core.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsBizExceptionToDeclaredStatus() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBizException(
                new BizException(CommonErrorCode.FORBIDDEN));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().code()).isEqualTo("FL-COMMON-0403");
    }

    @Test
    void mapsMethodNotSupportedTo405() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET")));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().code()).isEqualTo("FL-COMMON-0405");
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/probe");
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpected(
                new IllegalStateException("secret"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("FL-COMMON-0500");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }
}
