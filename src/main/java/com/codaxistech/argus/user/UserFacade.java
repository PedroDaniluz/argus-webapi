package com.codaxistech.argus.user;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Unica porta de entrada de outros pacotes para o {@code user}. Ninguem de fora
 * injeta {@link UserRepository} nem {@link UserService}.
 */
@Component
public class UserFacade {

    private final UserService service;

    UserFacade(UserService service) {
        this.service = service;
    }

    public Optional<UserDtos.Account> authenticate(String email, String rawPassword) {
        return service.authenticate(email, rawPassword);
    }

    public Optional<UserDtos.Account> activeAccount(UUID userId) {
        return service.activeAccount(userId);
    }

    /** Vazio quando o usuario nao existe ou esta desabilitado. */
    public Optional<Integer> currentTokenVersion(UUID userId) {
        return service.currentTokenVersion(userId);
    }
}
