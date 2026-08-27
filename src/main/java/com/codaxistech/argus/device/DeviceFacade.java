package com.codaxistech.argus.device;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Unica porta de entrada de outros pacotes para o {@code device}.
 */
@Component
public class DeviceFacade {

    private final DeviceService service;

    DeviceFacade(DeviceService service) {
        this.service = service;
    }

    public Optional<DeviceDtos.Authenticated> authenticate(String presentedKey) {
        return service.authenticate(presentedKey);
    }

    /** @throws org.springframework.web.server.ResponseStatusException 404 se nao existir */
    public DeviceDtos.Response byCode(String code) {
        return service.get(code);
    }

    public List<DeviceDtos.Response> all() {
        return service.list();
    }
}
