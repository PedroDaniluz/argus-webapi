package com.codaxistech.argus.location;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class LocationFacade {

    private final LocationService service;

    LocationFacade(LocationService service) {
        this.service = service;
    }

    public Optional<LocationDtos.Response> lastFor(UUID deviceId, String deviceCode) {
        return service.lastFor(deviceId, deviceCode);
    }
}
