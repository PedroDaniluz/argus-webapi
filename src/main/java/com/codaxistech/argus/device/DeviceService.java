package com.codaxistech.argus.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class DeviceService {

    /**
     * The presented key is {@code <code>.<secret>}. The prefix is not a secret; it just
     * makes the lookup indexed, instead of BCrypt against every device on every request.
     */
    static final char KEY_SEPARATOR = '.';
    private static final int SECRET_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    DeviceService(DeviceRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<DeviceDtos.Response> list() {
        return repository.findAllByOrderByCodeAsc().stream().map(DeviceService::toResponse).toList();
    }

    public DeviceDtos.Response get(String code) {
        return toResponse(require(code));
    }

    @Transactional
    public DeviceDtos.Created create(DeviceDtos.CreateRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "code already registered");
        }
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Device device = new Device();
        device.setCode(request.code());
        device.setLabel(request.label().trim());
        device.setKeyHash(passwordEncoder.encode(secret));
        Device saved = repository.save(device);

        return new DeviceDtos.Created(toResponse(saved), saved.getCode() + KEY_SEPARATOR + secret);
    }

    @Transactional
    public DeviceDtos.Response revokeKey(String code) {
        Device device = require(code);
        if (device.isActive()) {
            device.setRevokedAt(Instant.now());
            log.info("revoked key for device {}", code);
        }
        return toResponse(device);
    }

    /** Empty for malformed, unknown, revoked or wrong: indistinguishable on purpose. */
    public Optional<DeviceDtos.Authenticated> authenticate(String presentedKey) {
        int separator = presentedKey.indexOf(KEY_SEPARATOR);
        if (separator <= 0 || separator == presentedKey.length() - 1) {
            return Optional.empty();
        }
        String code = presentedKey.substring(0, separator);
        String secret = presentedKey.substring(separator + 1);
        return repository.findByCode(code)
                .filter(Device::isActive)
                .filter(d -> passwordEncoder.matches(secret, d.getKeyHash()))
                .map(d -> new DeviceDtos.Authenticated(d.getId(), d.getCode()));
    }

    private Device require(String code) {
        return repository.findByCode(code).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "device not found"));
    }

    private static DeviceDtos.Response toResponse(Device device) {
        return new DeviceDtos.Response(device.getId(), device.getCode(), device.getLabel(),
                device.getCreatedAt(), device.getRevokedAt(), device.isActive());
    }
}
