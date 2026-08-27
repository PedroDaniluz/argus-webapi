package com.codaxistech.argus.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class LocationDtos {

    private LocationDtos() {
    }

    public record IngestRequest(
            @NotBlank String deviceCode,
            /*
             * Sem @Valid na lista de proposito: amostra ruim nao pode derrubar o
             * lote inteiro. A validacao de cada amostra acontece no service, que
             * descarta e conta. O @Size vale para o lote e derruba mesmo — 50 e o
             * teto do transporte LoRa da fase seguinte, imposto desde ja.
             */
            @NotEmpty @Size(max = 50) List<Sample> samples
    ) {}

    public record Sample(
            @Schema(description = "epoch em segundos, UTC, vindo do GNSS", example = "1735000000")
            @NotNull Long ts,
            @NotNull Double lat,
            @NotNull Double lon,
            Float speedMps,
            Float courseDeg,
            Short sats,
            Float hdop
    ) {}

    /**
     * {@code received} conta o que chegou, {@code stored} o que virou linha e
     * {@code duplicates} o que ja existia. A soma pode ficar abaixo de
     * {@code received}: a diferenca sao amostras descartadas por invalidas.
     */
    public record IngestResponse(int received, int stored, int duplicates) {}

    public record Response(
            Long id, String deviceCode, Instant ts,
            double lat, double lon,
            Float speedMps, Float courseDeg, Short sats, Float hdop,
            Instant receivedAt
    ) {}

    /**
     * {@code nextCursor} e o {@code ts} da ultima linha devolvida: mande de volta
     * como {@code to} para pegar a pagina seguinte. Nulo quando acabou.
     */
    public record Page(List<Response> items, Instant nextCursor) {}

    /** Publicado depois de gravar, para o SSE empurrar sem consultar o banco. */
    public record Stored(List<Response> locations) {}
}
