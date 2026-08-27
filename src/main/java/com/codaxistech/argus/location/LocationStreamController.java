package com.codaxistech.argus.location;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tempo real por SSE. O fluxo e so servidor para navegador, e o EventSource
 * reconecta sozinho — WebSocket seria bidirecional para um problema que nao e.
 *
 * <p>O navegador abre este stream direto na API, sem atravessar o Next, entao o
 * CORS precisa liberar a origem do painel.
 */
@RestController
@RequestMapping("/api/locations")
@Tag(name = "locations", description = "Consulta de posicoes")
@SecurityRequirement(name = "bearerAuth")
public class LocationStreamController {

    private static final Logger log = LoggerFactory.getLogger(LocationStreamController.class);

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final long timeoutMillis;

    LocationStreamController(@Value("${argus.stream.timeout}") Duration timeout) {
        // 0 desliga o timeout do lado do servidor; o heartbeat cuida do resto.
        this.timeoutMillis = timeout.isZero() ? 0L : timeout.toMillis();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream de posicoes (SSE)",
            description = "Emite um evento 'location' por amostra gravada e um comentario "
                    + ":heartbeat a cada 30s. O token vai no header Authorization, entao "
                    + "use um cliente que permita header (fetch/EventSource polyfill).")
    public SseEmitter stream(
            @Parameter(description = "filtra por code de dispositivo; ausente, manda todos")
            @RequestParam(name = "device", required = false) String device) {

        SseEmitter emitter = new SseEmitter(timeoutMillis);
        Subscription subscription = new Subscription(emitter, device);
        subscriptions.add(subscription);

        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> {
            subscriptions.remove(subscription);
            emitter.complete();
        });
        emitter.onError(e -> subscriptions.remove(subscription));

        try {
            // Primeiro byte imediato: firma a conexao antes de qualquer proxy no
            // caminho decidir que ela esta ociosa.
            emitter.send(SseEmitter.event().comment("conectado"));
        } catch (IOException e) {
            subscriptions.remove(subscription);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * Depois do commit, nunca durante: empurrar antes de a transacao fechar
     * mandaria para o painel uma posicao que um rollback ainda pode apagar.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onStored(LocationDtos.Stored event) {
        for (LocationDtos.Response location : event.locations()) {
            for (Subscription subscription : subscriptions) {
                if (subscription.accepts(location)) {
                    send(subscription, SseEmitter.event()
                            .id(String.valueOf(location.id()))
                            .name("location")
                            .data(location, MediaType.APPLICATION_JSON));
                }
            }
        }
    }

    /**
     * Comentario SSE a cada 30s. O Traefik ja esta com idleTimeout=0, mas o
     * heartbeat continua valendo: e o que faz o cliente perceber que a conexao
     * caiu, em vez de ficar esperando um evento que nunca vem.
     */
    @Scheduled(fixedDelayString = "${argus.stream.heartbeat}")
    void heartbeat() {
        if (subscriptions.isEmpty()) {
            return;
        }
        for (Subscription subscription : subscriptions) {
            send(subscription, SseEmitter.event().comment("heartbeat"));
        }
    }

    private void send(Subscription subscription, SseEmitter.SseEventBuilder event) {
        try {
            subscription.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            // Cliente sumiu. Nao e erro: o EventSource reconecta e abre outra.
            log.debug("assinante do stream removido: {}", e.toString());
            subscriptions.remove(subscription);
            subscription.emitter().complete();
        }
    }

    /** {@code deviceCode} nulo significa "todos os dispositivos". */
    private record Subscription(SseEmitter emitter, String deviceCode) {

        boolean accepts(LocationDtos.Response location) {
            return deviceCode == null || deviceCode.equals(location.deviceCode());
        }
    }
}
