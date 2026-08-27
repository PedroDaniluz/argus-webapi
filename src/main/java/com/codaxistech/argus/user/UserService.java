package com.codaxistech.argus.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    UserService(UserRepository repository, PasswordEncoder passwordEncoder,
                @Value("${argus.admin.email:}") String adminEmail,
                @Value("${argus.admin.password:}") String adminPassword) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    /**
     * Cria o primeiro ADMIN a partir do ambiente, e so enquanto a tabela estiver
     * vazia. Como nao existe auto-cadastro, sem isto nao ha primeiro login e a
     * instalacao fica inacessivel. Nao e migration porque senha em arquivo
     * versionado vira senha publica.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAdmin() {
        if (repository.count() > 0) {
            return;
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.error("nenhum usuario cadastrado e ARGUS_ADMIN_EMAIL/ARGUS_ADMIN_PASSWORD "
                    + "estao vazias: nao ha como fazer login. Preencha as duas e suba de novo.");
            return;
        }
        User admin = new User();
        admin.setEmail(adminEmail.trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setName("Administrador");
        admin.setRole(User.Role.ADMIN);
        repository.save(admin);
        log.info("ADMIN inicial criado para {}", admin.getEmail());
    }

    public List<UserDtos.Response> list() {
        return repository.findAllByOrderByCreatedAtAsc().stream().map(UserService::toResponse).toList();
    }

    @Transactional
    public UserDtos.Response create(UserDtos.CreateRequest request) {
        if (repository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email ja cadastrado");
        }
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name().trim());
        user.setRole(request.role());
        return toResponse(repository.save(user));
    }

    /**
     * Confere a senha aqui dentro para o hash nao precisar sair do pacote.
     * Usuario desabilitado nao autentica.
     */
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

    /** Revoga todo token ja emitido para o usuario. */
    @Transactional
    public void bumpTokenVersion(UUID id) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado"));
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
