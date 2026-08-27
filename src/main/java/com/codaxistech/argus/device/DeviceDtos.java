package com.codaxistech.argus.device;

import com.codaxistech.argus.location.LocationDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "[a-z0-9][a-z0-9-]*", message = "use minusculas, digitos e hifen")
            String code,
            @NotBlank @Size(max = 120) String label
    ) {}

    public record Response(
            UUID id, String code, String label,
            Instant createdAt, Instant revokedAt, boolean active
    ) {}

    /**
     * A chave aparece uma unica vez, aqui. Depois disso so existe o hash — perdeu,
     * cria outro dispositivo.
     */
    public record Created(Response device, String key) {}

    public record Detail(Response device, LocationDtos.Response lastLocation) {}

    /** Dispositivo ja autenticado pelo header X-Device-Key. */
    public record Authenticated(UUID id, String code) {}
}
