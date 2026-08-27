package com.codaxistech.argus.user;

import com.codaxistech.argus.common.AuthenticatedUser;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** The only way into {@code user} from outside. */
@Component
public class UserFacade {

    private final UserService service;

    UserFacade(UserService service) {
        this.service = service;
    }

    public Optional<AuthenticatedUser> authenticate(String email, String rawPassword) {
        return service.authenticate(email, rawPassword);
    }

    public Optional<AuthenticatedUser> activeAccount(UUID userId) {
        return service.activeAccount(userId);
    }

    /** Empty when the user is gone or disabled. */
    public Optional<Integer> currentTokenVersion(UUID userId) {
        return service.currentTokenVersion(userId);
    }
}
