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

/**
 * Entrada dos dispositivos. Fica sob {@code /api/ingest} porque essa e a fatia
 * que a cadeia de seguranca do {@code X-Device-Key} cobre — nenhum endpoint de
 * usuario mora aqui.
 */
@RestController
@RequestMapping("/api/ingest")
@Tag(name = "ingest", description = "Contrato entre firmware e backend")
@SecurityRequirement(name = "deviceKey")
public class LocationIngestController {

    private final LocationService service;

    LocationIngestController(LocationService service) {
        this.service = service;
    }

    /**
     * 202 e nao 201: a ingestao e assincrona do ponto de vista da placa, que nao
     * espera consistencia nem consulta o recurso criado depois.
     */
    @PostMapping("/locations")
    @Operation(summary = "Recebe um lote de ate 50 amostras",
            description = "Idempotente por (dispositivo, ts): reenviar o mesmo buffer nao duplica.")
    public ResponseEntity<LocationDtos.IngestResponse> ingest(
            @AuthenticationPrincipal DeviceDtos.Authenticated device,
            @Valid @RequestBody LocationDtos.IngestRequest request) {

        // O deviceCode do corpo tem que casar com a chave apresentada, senao um
        // dispositivo com chave valida poderia escrever no rastro de outro.
        if (!device.code().equals(request.deviceCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "deviceCode nao corresponde a chave apresentada");
        }

        LocationDtos.IngestResponse response =
                service.ingest(device.id(), device.code(), request.samples());
        return ResponseEntity.accepted().body(response);
    }
}
