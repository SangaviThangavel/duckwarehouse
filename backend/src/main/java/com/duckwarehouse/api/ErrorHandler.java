package com.duckwarehouse.api;

import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);
    @ExceptionHandler(ApiException.class)
    ProblemDetail business(ApiException error) {
        return problem(error.status(), error.code(), error.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException error) {
        var fields = new LinkedHashMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        var result = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Please correct the invalid fields.");
        result.setProperty("errors", fields);
        return result;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformed() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid JSON, unknown field, or unsupported value.");
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail database(DataAccessException error) {
        log.warn("Database operation failed", error);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "The database is unavailable. Try again later.");
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setProperty("code", code);
        return result;
    }
}
