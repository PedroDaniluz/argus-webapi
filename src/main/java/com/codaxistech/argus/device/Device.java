package com.codaxistech.argus.device;

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
 * Placa embarcada. Nunca fala com o Postgres direto: credencial de banco em
 * firmware daria escrita a quem tivesse a placa na mao, e sem revogacao
 * individual. Por isso a placa faz POST na API com uma chave propria.
 */
@Entity
@Table(name = "device")
@Getter
@Setter
@NoArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Identificador humano, tipo 'trator-01'. */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String label;

    /** BCrypt do segredo. A chave em claro so existe na resposta da criacao. */
    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
