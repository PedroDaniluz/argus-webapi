package com.codaxistech.argus.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> fields.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ApiError.of(
                400, "Bad Request", "payload invalido", req.getRequestURI(), fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> onConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.badRequest().body(ApiError.of(
                400, "Bad Request", "parametros invalidos", req.getRequestURI(), fields));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> onStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(ApiError.of(
                status.value(), status.getReasonPhrase(), ex.getReason(), req.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> onUnauthenticated(AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(
                401, "Unauthorized", "credencial ausente ou invalida", req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> onDenied(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                403, "Forbidden", "sem permissao para este recurso", req.getRequestURI()));
    }

    @ExceptionHandler(TypeMismatchException.class)
    ResponseEntity<ApiError> onTypeMismatch(TypeMismatchException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ApiError.of(
                400, "Bad Request",
                "valor invalido para o parametro: " + ex.getValue(), req.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> onConflict(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("violacao de integridade em {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                409, "Conflict", "recurso ja existe ou viola uma restricao", req.getRequestURI()));
    }

    /**
     * Rota inexistente, metodo errado, corpo ilegivel e parecidos ja chegam com o
     * status certo por {@link ErrorResponse}. Sem este desvio eles cairiam no 500
     * generico abaixo, e um 404 apareceria como falha do servidor.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> onUnexpected(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse response) {
            HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
            return ResponseEntity.status(status).body(ApiError.of(
                    status.value(), status.getReasonPhrase(), ex.getMessage(), req.getRequestURI()));
        }
        log.error("erro nao tratado em {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                500, "Internal Server Error", "erro interno", req.getRequestURI()));
    }
}
