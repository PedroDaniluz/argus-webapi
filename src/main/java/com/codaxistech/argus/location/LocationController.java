package com.codaxistech.argus.location;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/locations")
@Tag(name = "locations", description = "Position queries")
@SecurityRequirement(name = "bearerAuth")
public class LocationController {

    private final LocationService service;

    LocationController(LocationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "History for one device",
            description = "Newest first. To page, send the previous response's nextCursor back as to.")
    public LocationDtos.Page history(
            @Parameter(description = "device code", example = "trator-01")
            @RequestParam("device") String device,
            @Parameter(description = "inclusive lower bound, ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "exclusive cursor, ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit) {
        return service.history(device, from, to, limit);
    }

    @GetMapping("/latest")
    @Operation(summary = "Last position of each device")
    public List<LocationDtos.Response> latest() {
        return service.latest();
    }
}
