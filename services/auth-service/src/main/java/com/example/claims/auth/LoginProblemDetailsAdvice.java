package com.example.claims.auth;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Login failure message. Everything else is handled by the shared advice in libs/web-common; this one
 * runs first so a failed login isn't reported as "invalid access token".
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class LoginProblemDetailsAdvice {

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, AuthService.BAD_CREDENTIALS);
    }
}
