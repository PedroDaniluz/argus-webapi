package com.codaxistech.argus.device;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@Tag(name = "devices", description = "Embedded board registry and keys")
@SecurityRequirement(name = "bearerAuth")
class DeviceController {

    private final DeviceService service;

    DeviceController(DeviceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "listDevices", summary = "List devices")
    List<DeviceResponse> list() {
        return service.list();
    }

    @GetMapping("/{code}")
    @Operation(operationId = "getDevice", summary = "Device detail",
            description = "The last position comes from GET /api/locations/latest.")
    DeviceResponse detail(@PathVariable String code) {
        return service.get(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "createDevice", summary = "Register a device",
            description = "The key comes back in plaintext here and in no other response.")
    DeviceCreated create(@Valid @RequestBody CreateDeviceRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{code}/key")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "revokeDeviceKey", summary = "Revoke the device key",
            description = "Takes effect immediately: the next ingest POST is refused.")
    DeviceResponse revokeKey(@PathVariable String code) {
        return service.revokeKey(code);
    }
}
