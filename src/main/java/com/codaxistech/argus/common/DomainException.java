package com.codaxistech.argus.common;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {

    private final HttpStatus status;

    private DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static DomainException badRequest(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, message);
    }

    public static DomainException unauthorized(String message) {
        return new DomainException(HttpStatus.UNAUTHORIZED, message);
    }

    public static DomainException forbidden(String message) {
        return new DomainException(HttpStatus.FORBIDDEN, message);
    }

    public static DomainException notFound(String message) {
        return new DomainException(HttpStatus.NOT_FOUND, message);
    }

    public static DomainException conflict(String message) {
        return new DomainException(HttpStatus.CONFLICT, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
