package com.codaxistech.argus.device;

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
 * An embedded board. It POSTs to the API rather than to Postgres: database
 * credentials in firmware would hand write access to anyone holding a board.
 */
@Entity
@Table(name = "device")
@Getter
class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String label;

    /** BCrypt of the secret. Plaintext exists only in the creation response. */
    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Device() {
    }

    Device(String code, String label, String keyHash) {
        this.code = code;
        this.label = label;
        this.keyHash = keyHash;
    }

    boolean isActive() {
        return revokedAt == null;
    }

    void revoke() {
        if (isActive()) {
            this.revokedAt = Instant.now();
        }
    }
}
