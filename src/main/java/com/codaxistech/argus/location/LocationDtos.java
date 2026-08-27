package com.codaxistech.argus.location;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class LocationDtos {

    private LocationDtos() {
    }

    @Schema(name = "IngestLocationsRequest")
    public record IngestRequest(
            @NotBlank String deviceCode,
            // No @Valid on purpose: one bad sample must not sink the batch, so samples
            // are checked in the service. @Size does reject the batch: 50 is the ceiling
            // of the LoRa transport coming next, enforced from day one.
            @NotEmpty @Size(max = 50) List<Sample> samples
    ) {}

    @Schema(name = "LocationSample")
    public record Sample(
            @Schema(description = "epoch seconds, UTC, straight from the GNSS", example = "1735000000")
            @NotNull Long ts,
            @NotNull Double lat,
            @NotNull Double lon,
            Float speedMps,
            Float courseDeg,
            Short sats,
            Float hdop
    ) {}

    /** {@code stored} plus {@code duplicates} falls short of {@code received} when samples were invalid. */
    @Schema(name = "IngestLocationsResponse")
    public record IngestResponse(int received, int stored, int duplicates) {}

    /*
     * Every field is REQUIRED so the generated client stops typing all of them as
     * optional; only the genuinely absent ones carry nullable = true. The four
     * nullable ones are the readings the GNSS may not supply on a given fix.
     */
    @Schema(name = "Location")
    public record Response(
            @Schema(requiredMode = RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = RequiredMode.REQUIRED) String deviceCode,
            @Schema(requiredMode = RequiredMode.REQUIRED) Instant ts,
            @Schema(requiredMode = RequiredMode.REQUIRED) double lat,
            @Schema(requiredMode = RequiredMode.REQUIRED) double lon,
            @Schema(requiredMode = RequiredMode.REQUIRED, nullable = true) Float speedMps,
            @Schema(requiredMode = RequiredMode.REQUIRED, nullable = true) Float courseDeg,
            @Schema(requiredMode = RequiredMode.REQUIRED, nullable = true) Short sats,
            @Schema(requiredMode = RequiredMode.REQUIRED, nullable = true) Float hdop,
            @Schema(requiredMode = RequiredMode.REQUIRED) Instant receivedAt
    ) {}

    /** Send {@code nextCursor} back as {@code to} for the next page. Null at the end. */
    @Schema(name = "LocationPage")
    public record Page(
            @Schema(requiredMode = RequiredMode.REQUIRED) List<Response> items,
            @Schema(requiredMode = RequiredMode.REQUIRED, nullable = true) Instant nextCursor
    ) {}

    public record Stored(List<Response> locations) {}
}
