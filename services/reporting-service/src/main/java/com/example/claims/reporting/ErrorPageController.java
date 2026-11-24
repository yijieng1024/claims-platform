package com.example.claims.reporting;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Errors thrown outside MVC (e.g. in security filters) end up on /error. Re-throw them here so
 * {@link ProblemDetailsAdvice} renders them as ProblemDetail instead of Boot's default error JSON.
 */
@RestController
class ErrorPageController implements ErrorController {

    @RequestMapping("/error")
    void error(HttpServletRequest request) throws Throwable {
        Throwable ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        while (ex instanceof ServletException && ex.getCause() != null) {
            ex = ex.getCause();
        }
        if (ex != null) {
            throw ex;
        }
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        throw new ResponseStatusException(status instanceof Integer code ? HttpStatus.valueOf(code) : HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
