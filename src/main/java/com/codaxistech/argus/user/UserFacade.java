package com.codaxistech.argus.user;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

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

    public Optional<Integer> currentTokenVersion(UUID userId) {
        return service.currentTokenVersion(userId);
    }
}
