package com.codaxistech.argus.location;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Unica porta de entrada de outros pacotes para o {@code location}.
 */
@Component
public class LocationFacade {

    private final LocationService service;

    LocationFacade(LocationService service) {
        this.service = service;
    }

    /** Ultima posicao conhecida do dispositivo, vazio se ele nunca reportou. */
    public Optional<LocationDtos.Response> lastFor(UUID deviceId, String deviceCode) {
        return service.lastFor(deviceId, deviceCode);
    }
}
