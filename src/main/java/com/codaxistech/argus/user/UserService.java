package com.codaxistech.argus.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserDtos.Response> list() {
        return repository.findAllByOrderByCreatedAtAsc().stream().map(UserService::toResponse).toList();
    }

    @Transactional
    public UserDtos.Response create(UserDtos.CreateRequest request) {
        if (repository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name().trim());
        user.setRole(request.role());
        return toResponse(repository.save(user));
    }

    /** Checked here so the hash never leaves the package. */
    public Optional<UserDtos.Account> authenticate(String email, String rawPassword) {
        return repository.findByEmailIgnoreCase(email)
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .map(UserService::toAccount);
    }

    public Optional<UserDtos.Account> activeAccount(UUID id) {
        return repository.findById(id).filter(User::isActive).map(UserService::toAccount);
    }

    public Optional<Integer> currentTokenVersion(UUID id) {
        return repository.findById(id).filter(User::isActive).map(User::getTokenVersion);
    }

    @Transactional
    public void bumpTokenVersion(UUID id) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    private static UserDtos.Response toResponse(User user) {
        return new UserDtos.Response(user.getId(), user.getEmail(), user.getName(),
                user.getRole(), user.getCreatedAt(), user.getDisabledAt());
    }

    private static UserDtos.Account toAccount(User user) {
        return new UserDtos.Account(user.getId(), user.getEmail(), user.getName(),
                user.getRole(), user.getTokenVersion());
    }
}
