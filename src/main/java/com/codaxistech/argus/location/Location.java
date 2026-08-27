package com.codaxistech.argus.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma amostra de posicao. O pacote e {@code location} e nao {@code position}
 * porque POSITION e palavra reservada em SQL.
 *
 * <p>{@code deviceId} e a coluna crua, nao um {@code @ManyToOne}: a chave
 * estrangeira continua valendo no banco e o pacote nao passa a depender da
 * entidade {@code Device}.
 */
@Entity
@Table(name = "location")
@Getter
@Setter
@NoArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    /** Hora do GNSS. Relogio atomico de satelite: preciso, sem deriva, sem NTP. */
    @Column(nullable = false)
    private Instant ts;

    /**
     * DOUBLE PRECISION porque float de 32 bits custa 1 a 2 m de erro, que e
     * exatamente a ordem de grandeza que estamos medindo.
     */
    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(name = "speed_mps")
    private Float speedMps;

    @Column(name = "course_deg")
    private Float courseDeg;

    private Short sats;

    private Float hdop;

    /** Quando chegou. A diferenca para {@code ts} diz quanto tempo ficou em buffer. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();
}
