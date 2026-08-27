package com.codaxistech.argus.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    /** {@code expiresIn} em segundos, para o cliente agendar o refresh. */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn
    ) {}
}
