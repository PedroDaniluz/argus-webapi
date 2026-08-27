package com.codaxistech.argus.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Package is {@code location}, not {@code position}: POSITION is reserved in SQL.
 *
 * <p>{@code deviceId} is the raw column, not a {@code @ManyToOne}: the foreign key
 * still holds, and this package keeps no dependency on the {@code Device} entity.
 */
@Entity
@Table(name = "location")
@Getter
class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    /** GNSS time: satellite atomic clock, no drift, no NTP. */
    @Column(nullable = false)
    private Instant ts;

    /** DOUBLE PRECISION: a 32-bit float costs 1 to 2 m, the scale being measured. */
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

    /** Distance from {@code ts} is how long the sample sat in a buffer. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected Location() {
    }
}
