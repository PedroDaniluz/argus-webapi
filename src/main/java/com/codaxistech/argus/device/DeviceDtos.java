package com.codaxistech.argus.device;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

record CreateDeviceRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "use lowercase, digits and hyphen")
        @Schema(example = "tractor-01") String code,
        @NotBlank @Size(max = 120) @Schema(example = "Tractor 01") String label
) {}

record DeviceResponse(
        UUID id,
        String code,
        String label,
        Instant createdAt,
        @Schema(nullable = true) Instant revokedAt,
        boolean active
) {
    static DeviceResponse from(Device device) {
        return new DeviceResponse(device.getId(), device.getCode(), device.getLabel(),
                device.getCreatedAt(), device.getRevokedAt(), device.isActive());
    }
}

/** The key shows up here and nowhere else. */
record DeviceCreated(DeviceResponse device, String key) {}

