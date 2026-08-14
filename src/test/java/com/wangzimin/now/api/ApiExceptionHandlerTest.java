package com.wangzimin.now.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    @Test
    void exposesFriendlyBusinessMessageForUnauthorizedLogin() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        var response = new ApiExceptionHandler().handleResponseStatus(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("用户名或密码错误", response.getBody().message());
        assertEquals("/api/v1/auth/login", response.getBody().path());
    }
}
