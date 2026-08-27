package com.codaxistech.argus.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 120) String name,
            @NotNull User.Role role
    ) {}

    public record Response(
            UUID id, String email, String name, User.Role role,
            Instant createdAt, Instant disabledAt
    ) {}

    /** What {@code auth} needs for its claims. No password hash leaves this package. */
    public record Account(
            UUID id, String email, String name, User.Role role, int tokenVersion
    ) {}
}
