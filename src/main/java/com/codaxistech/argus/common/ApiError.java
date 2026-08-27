package com.codaxistech.argus.common;

import java.time.Instant;
import java.util.Map;

/**
 * Corpo unico de erro da API. Formato estavel para o painel nao ter que adivinhar
 * o que veio do Spring e o que veio da aplicacao.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fields
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError of(int status, String error, String message, String path,
                              Map<String, String> fields) {
        return new ApiError(Instant.now(), status, error, message, path, fields);
    }
}
