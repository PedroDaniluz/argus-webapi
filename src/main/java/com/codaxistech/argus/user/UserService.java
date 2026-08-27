package com.codaxistech.argus.user;

import com.codaxistech.argus.common.AuthenticatedUser;
import com.codaxistech.argus.common.DomainException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    List<UserResponse> list() {
        return repository.findAllByOrderByCreatedAtAsc().stream().map(UserResponse::from).toList();
    }

    UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw DomainException.conflict("Email '%s' already registered".formatted(email));
        }
        return UserResponse.from(repository.save(new User(
                email, passwordEncoder.encode(request.password()),
                request.name().trim(), request.role())));
    }

    /** Checked here so the hash never leaves the package. */
    @Transactional(readOnly = true)
    Optional<AuthenticatedUser> authenticate(String email, String rawPassword) {
        return repository.findByEmailIgnoreCase(email)
                .filter(User::isActive)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .map(UserService::toAuthenticated);
    }

    @Transactional(readOnly = true)
    Optional<AuthenticatedUser> activeAccount(UUID id) {
        return repository.findById(id).filter(User::isActive).map(UserService::toAuthenticated);
    }

    private static AuthenticatedUser toAuthenticated(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getName(),
                user.getRole(), user.getTokenVersion());
    }

    @Transactional(readOnly = true)
    Optional<Integer> currentTokenVersion(UUID id) {
        return repository.findById(id).filter(User::isActive).map(User::getTokenVersion);
    }
}
