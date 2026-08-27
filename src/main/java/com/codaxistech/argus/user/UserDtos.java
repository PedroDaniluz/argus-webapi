package com.codaxistech.argus.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

record CreateUserRequest(
        @NotBlank @Email @Schema(example = "operator@argus.local") String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 120) String name,
        @NotNull Role role
) {}

record UserResponse(
        UUID id,
        String email,
        String name,
        Role role,
        Instant createdAt,
        @Schema(nullable = true) Instant disabledAt
) {
    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                user.getCreatedAt(), user.getDisabledAt());
    }
}
