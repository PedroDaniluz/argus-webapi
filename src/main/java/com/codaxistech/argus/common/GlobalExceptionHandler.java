package com.codaxistech.argus.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> onDomain(DomainException ex, HttpServletRequest request) {
        return respond(ex.getStatus(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onInvalidBody(MethodArgumentNotValidException ex,
                                           HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return respond(HttpStatus.BAD_REQUEST, "invalid payload", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> onConstraint(ConstraintViolationException ex,
                                          HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return respond(HttpStatus.BAD_REQUEST, "invalid parameters", request, fields);
    }

    @ExceptionHandler(TypeMismatchException.class)
    ResponseEntity<ApiError> onTypeMismatch(TypeMismatchException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "invalid value: " + ex.getValue(), request, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> onStatus(ResponseStatusException ex, HttpServletRequest request) {
        return respond(statusOf(ex.getStatusCode()), ex.getReason(), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> onUnauthenticated(AuthenticationException ex,
                                               HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "missing or invalid credentials", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> onDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "not allowed on this resource", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> onConflict(DataIntegrityViolationException ex,
                                        HttpServletRequest request) {
        log.warn("integrity violation on {}", request.getRequestURI(), ex);
        return respond(HttpStatus.CONFLICT, "conflicts with an existing resource", request, null);
    }

    /**
     * Unknown route, wrong method, unreadable body: these already carry the right
     * status. Without this they would fall through to the 500 below.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> onUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse response) {
            return respond(statusOf(response.getStatusCode()), ex.getMessage(), request, null);
        }
        log.error("unhandled error on {}", request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "internal error", request, null);
    }

    private static HttpStatus statusOf(HttpStatusCode code) {
        HttpStatus status = HttpStatus.resolve(code.value());
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message,
                                                    HttpServletRequest request,
                                                    Map<String, String> fields) {
        return ResponseEntity.status(status).body(
                ApiError.of(status, message, request.getRequestURI(), fields));
    }
}
