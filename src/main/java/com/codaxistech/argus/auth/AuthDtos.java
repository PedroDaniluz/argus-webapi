package com.codaxistech.argus.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record LoginRequest(
        @NotBlank @Email @Schema(example = "admin@argus.local") String email,
        @NotBlank String password
) {}

record RefreshRequest(@NotBlank String refreshToken) {}

/** {@code expiresIn} in seconds. */
record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}
