package com.codaxistech.argus.device;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import com.codaxistech.argus.location.LocationDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    @Schema(name = "CreateDeviceRequest")
    public record CreateRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "use lowercase, digits and hyphen")
            String code,
            @NotBlank @Size(max = 120) String label
    ) {}

    /*
     * Every field is REQUIRED so the generated client stops typing all of them as
     * optional; only the genuinely absent ones carry nullable = true.
     */
    @Schema(name = "Device")
    public record Response(
            @Schema(requiredMode = RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = RequiredMode.REQUIRED) String label,
            @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
            @Schema(requiredMode = RequiredMode.REQUIRED, nullable = true) Instant revokedAt,
            @Schema(requiredMode = RequiredMode.REQUIRED) boolean active
    ) {}

    /** The key shows up here and nowhere else. */
    @Schema(name = "DeviceCreated")
    public record Created(
            @Schema(requiredMode = RequiredMode.REQUIRED) Response device,
            @Schema(requiredMode = RequiredMode.REQUIRED) String key
    ) {}

    @Schema(name = "DeviceDetail")
    public record Detail(
            @Schema(requiredMode = RequiredMode.REQUIRED) Response device,
            @Schema(requiredMode = RequiredMode.REQUIRED, nullable = true) LocationDtos.Response lastLocation
    ) {}

    public record Authenticated(UUID id, String code) {}
}
