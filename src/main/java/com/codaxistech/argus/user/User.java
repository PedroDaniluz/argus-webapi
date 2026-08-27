package com.codaxistech.argus.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Table is {@code app_user} because {@code user} is reserved in Postgres. */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    public enum Role {
        ADMIN, OPERATOR, VIEWER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Token's {@code tv} claim. Bumping it revokes every issued token. */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "disabled_at")
    private Instant disabledAt;

    public boolean isActive() {
        return disabledAt == null;
    }
}
