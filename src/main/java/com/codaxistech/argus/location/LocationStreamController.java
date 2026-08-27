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
 * SSE, not WebSocket: the flow is server to browser only and EventSource reconnects
 * on its own. The browser opens it directly against this API, so CORS has to allow
 * the dashboard origin.
 */
@RestController
@RequestMapping("/api/locations")
@Tag(name = "locations", description = "Position queries")
@SecurityRequirement(name = "bearerAuth")
public class LocationStreamController {

    private static final Logger log = LoggerFactory.getLogger(LocationStreamController.class);

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final long timeoutMillis;

    LocationStreamController(@Value("${argus.stream.timeout}") Duration timeout) {
        this.timeoutMillis = timeout.isZero() ? 0L : timeout.toMillis();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(operationId = "streamLocations", summary = "Position stream (SSE)",
            description = "Emits one 'location' event per stored sample and a :heartbeat comment "
                    + "every 30s. The token goes in the Authorization header, so use a client "
                    + "that can set headers (fetch, or an EventSource polyfill).")
    public SseEmitter stream(
            @Parameter(description = "filter by device code; omit for every device")
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
            // Immediate first byte, so no proxy decides the connection is idle.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            subscriptions.remove(subscription);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** After commit, never during: a rollback could still erase what was pushed. */
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

    /** How a client notices a dropped connection instead of waiting forever. */
    @Scheduled(fixedDelayString = "${argus.stream.heartbeat}")
    void heartbeat() {
        for (Subscription subscription : subscriptions) {
            send(subscription, SseEmitter.event().comment("heartbeat"));
        }
    }

    private void send(Subscription subscription, SseEmitter.SseEventBuilder event) {
        try {
            subscription.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            // Client is gone. EventSource will reconnect.
            log.debug("dropping stream subscriber: {}", e.toString());
            subscriptions.remove(subscription);
            subscription.emitter().complete();
        }
    }

    private record Subscription(SseEmitter emitter, String deviceCode) {

        boolean accepts(LocationDtos.Response location) {
            return deviceCode == null || deviceCode.equals(location.deviceCode());
        }
    }
}
