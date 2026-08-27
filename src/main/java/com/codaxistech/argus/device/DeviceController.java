package com.codaxistech.argus.device;

import com.codaxistech.argus.location.LocationFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/devices")
@Tag(name = "devices", description = "Embedded board registry and keys")
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {

    private final DeviceService service;
    private final LocationFacade locations;

    DeviceController(DeviceService service, LocationFacade locations) {
        this.service = service;
        this.locations = locations;
    }

    @GetMapping
    @Operation(operationId = "listDevices", summary = "List devices")
    public List<DeviceDtos.Response> list() {
        return service.list();
    }

    @GetMapping("/{code}")
    @Operation(operationId = "getDevice", summary = "Device detail with its last known position")
    public DeviceDtos.Detail detail(@PathVariable String code) {
        DeviceDtos.Response device = service.get(code);
        return new DeviceDtos.Detail(device,
                locations.lastFor(device.id(), device.code()).orElse(null));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "createDevice", summary = "Register a device",
            description = "The key comes back in plaintext here and in no other response.")
    public ResponseEntity<DeviceDtos.Created> create(
            @Valid @RequestBody DeviceDtos.CreateRequest request) {
        DeviceDtos.Created created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/devices/" + created.device().code()))
                .body(created);
    }

    @DeleteMapping("/{code}/key")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "revokeDeviceKey", summary = "Revoke the device key",
            description = "Takes effect immediately: the next ingest POST is refused.")
    public DeviceDtos.Response revokeKey(@PathVariable String code) {
        return service.revokeKey(code);
    }
}
