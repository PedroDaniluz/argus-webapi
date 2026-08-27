package com.codaxistech.argus.location;

import com.codaxistech.argus.device.DeviceDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Under {@code /api/ingest} because that is the slice the X-Device-Key chain covers. */
@RestController
@RequestMapping("/api/ingest")
@Tag(name = "ingest", description = "Contract between firmware and backend")
@SecurityRequirement(name = "deviceKey")
public class LocationIngestController {

    private final LocationService service;

    LocationIngestController(LocationService service) {
        this.service = service;
    }

    /** 202, not 201: the board does not wait for consistency nor read the resource back. */
    @PostMapping("/locations")
    @Operation(operationId = "ingestLocations", summary = "Accept a batch of up to 50 samples",
            description = "Idempotent per (device, ts): resending the same buffer stores nothing twice.")
    public ResponseEntity<LocationDtos.IngestResponse> ingest(
            @AuthenticationPrincipal DeviceDtos.Authenticated device,
            @Valid @RequestBody LocationDtos.IngestRequest request) {

        // Otherwise a device with a valid key could write into another device's track.
        if (!device.code().equals(request.deviceCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "deviceCode does not match the presented key");
        }
        return ResponseEntity.accepted()
                .body(service.ingest(device.id(), device.code(), request.samples()));
    }
}
