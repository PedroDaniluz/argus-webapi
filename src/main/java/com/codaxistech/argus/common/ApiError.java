package com.codaxistech.argus.common;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/** {@code fields} is empty rather than null, so a client never has to null-check it. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fields
) {

    public static ApiError of(HttpStatus status, String message, String path,
                              Map<String, String> fields) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                message != null ? message : status.getReasonPhrase(), path,
                fields != null ? fields : Map.of());
    }
}
