package com.codaxistech.argus.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

record IngestLocationsRequest(
        @NotBlank @Schema(example = "tractor-01") String deviceCode,
        // No @Valid on purpose: one bad sample must not sink the batch, so samples are
        // checked in the service. @Size does reject the batch: 50 is the ceiling of the
        // LoRa transport coming next, enforced from day one.
        @NotEmpty @Size(max = 50) List<LocationSample> samples
) {}

record LocationSample(
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
record IngestLocationsResponse(int received, int stored, int duplicates) {}

/** The nullable readings are the ones the GNSS may not supply on a given fix. */
record LocationResponse(
        Long id,
        String deviceCode,
        Instant ts,
        double lat,
        double lon,
        @Schema(nullable = true) Float speedMps,
        @Schema(nullable = true) Float courseDeg,
        @Schema(nullable = true) Short sats,
        @Schema(nullable = true) Float hdop,
        Instant receivedAt
) {
    static LocationResponse from(Location location, String deviceCode) {
        return new LocationResponse(location.getId(), deviceCode, location.getTs(),
                location.getLat(), location.getLon(), location.getSpeedMps(),
                location.getCourseDeg(), location.getSats(), location.getHdop(),
                location.getReceivedAt());
    }
}

/** Send {@code nextCursor} back as {@code to} for the next page. Null at the end. */
record LocationPage(List<LocationResponse> items, @Schema(nullable = true) Instant nextCursor) {}

/** Published after commit so the SSE stream can push without hitting the database. */
record LocationsStored(List<LocationResponse> locations) {}
