package com.codaxistech.argus.device;

import com.codaxistech.argus.common.AuthenticatedDevice;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The only way into {@code device} from outside. It hands back ids and codes,
 * never DTOs: a response shape is this package's business.
 */
@Component
public class DeviceFacade {

    private final DeviceService service;

    DeviceFacade(DeviceService service) {
        this.service = service;
    }

    public Optional<AuthenticatedDevice> authenticate(String presentedKey) {
        return service.authenticate(presentedKey);
    }

    /** @throws com.codaxistech.argus.common.DomainException 404 when the code is unknown */
    public UUID requireIdByCode(String code) {
        return service.requireIdByCode(code);
    }

    public Map<UUID, String> codesById() {
        return service.codesById();
    }
}
