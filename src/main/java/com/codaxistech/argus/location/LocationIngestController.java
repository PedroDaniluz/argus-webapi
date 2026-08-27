package com.codaxistech.argus.location;

import com.codaxistech.argus.common.AuthenticatedDevice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.codaxistech.argus.common.DomainException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Under {@code /api/ingest} because that is the slice the X-Device-Key chain covers. */
@RestController
@RequestMapping("/api/ingest")
@Tag(name = "ingest", description = "Contract between firmware and backend")
@SecurityRequirement(name = "deviceKey")
class LocationIngestController {

    private final LocationService service;

    LocationIngestController(LocationService service) {
        this.service = service;
    }

    /** 202, not 201: the board does not wait for consistency nor read the resource back. */
    @PostMapping("/locations")
    @Operation(operationId = "ingestLocations", summary = "Accept a batch of up to 50 samples",
            description = "Idempotent per (device, ts): resending the same buffer stores nothing twice.")
    ResponseEntity<IngestLocationsResponse> ingest(
            @AuthenticationPrincipal AuthenticatedDevice device,
            @Valid @RequestBody IngestLocationsRequest request) {

        // Otherwise a device with a valid key could write into another device's track.
        if (!device.code().equals(request.deviceCode())) {
            throw DomainException.forbidden(
                    "deviceCode does not match the presented key");
        }
        return ResponseEntity.accepted()
                .body(service.ingest(device.id(), device.code(), request.samples()));
    }
}
