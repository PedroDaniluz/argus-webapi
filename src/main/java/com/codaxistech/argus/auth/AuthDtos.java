package com.codaxistech.argus.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
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

    /** {@code expiresIn} in seconds. */
    public record TokenResponse(
            @Schema(requiredMode = RequiredMode.REQUIRED) String accessToken,
            @Schema(requiredMode = RequiredMode.REQUIRED) String refreshToken,
            @Schema(requiredMode = RequiredMode.REQUIRED) long expiresIn
    ) {}
}
