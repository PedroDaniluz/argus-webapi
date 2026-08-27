package com.codaxistech.argus.device;

import com.codaxistech.argus.location.LocationDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "use lowercase, digits and hyphen")
            String code,
            @NotBlank @Size(max = 120) String label
    ) {}

    public record Response(
            UUID id, String code, String label,
            Instant createdAt, Instant revokedAt, boolean active
    ) {}

    /** The key shows up here and nowhere else. */
    public record Created(Response device, String key) {}

    public record Detail(Response device, LocationDtos.Response lastLocation) {}

    public record Authenticated(UUID id, String code) {}
}
