package com.codaxistech.argus.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class LocationDtos {

    private LocationDtos() {
    }

    public record IngestRequest(
            @NotBlank String deviceCode,
            // No @Valid on purpose: one bad sample must not sink the batch, so samples
            // are checked in the service. @Size does reject the batch: 50 is the ceiling
            // of the LoRa transport coming next, enforced from day one.
            @NotEmpty @Size(max = 50) List<Sample> samples
    ) {}

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
    public record IngestResponse(int received, int stored, int duplicates) {}

    public record Response(
            Long id, String deviceCode, Instant ts,
            double lat, double lon,
            Float speedMps, Float courseDeg, Short sats, Float hdop,
            Instant receivedAt
    ) {}

    /** Send {@code nextCursor} back as {@code to} for the next page. Null at the end. */
    public record Page(List<Response> items, Instant nextCursor) {}

    public record Stored(List<Response> locations) {}
}
