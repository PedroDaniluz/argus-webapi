package com.codaxistech.argus.device;

import com.codaxistech.argus.common.AuthenticatedDevice;
import com.codaxistech.argus.common.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
@Transactional
class DeviceService {

    /**
     * The presented key is {@code <code>.<secret>}. The prefix is not a secret; it just
     * makes the lookup indexed, instead of BCrypt against every device on every request.
     */
    private static final char KEY_SEPARATOR = '.';
    private static final int SECRET_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    DeviceService(DeviceRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    List<DeviceResponse> list() {
        return repository.findAllByOrderByCodeAsc().stream().map(DeviceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    DeviceResponse get(String code) {
        return DeviceResponse.from(require(code));
    }

    @Transactional(readOnly = true)
    UUID requireIdByCode(String code) {
        return require(code).getId();
    }

    @Transactional(readOnly = true)
    Map<UUID, String> codesById() {
        return repository.findAllByOrderByCodeAsc().stream()
                .collect(Collectors.toMap(Device::getId, Device::getCode));
    }

    DeviceCreated create(CreateDeviceRequest request) {
        if (repository.existsByCode(request.code())) {
            throw DomainException.conflict("Device '%s' already exists".formatted(request.code()));
        }
        String secret = newSecret();
        Device saved = repository.save(
                new Device(request.code(), request.label().trim(), passwordEncoder.encode(secret)));

        return new DeviceCreated(DeviceResponse.from(saved),
                saved.getCode() + KEY_SEPARATOR + secret);
    }

    DeviceResponse revokeKey(String code) {
        Device device = require(code);
        device.revoke();
        log.info("revoked key for device {}", code);
        return DeviceResponse.from(device);
    }

    /** Empty for malformed, unknown, revoked or wrong: indistinguishable on purpose. */
    @Transactional(readOnly = true)
    Optional<AuthenticatedDevice> authenticate(String presentedKey) {
        int separator = presentedKey.indexOf(KEY_SEPARATOR);
        if (separator <= 0 || separator == presentedKey.length() - 1) {
            return Optional.empty();
        }
        String code = presentedKey.substring(0, separator);
        String secret = presentedKey.substring(separator + 1);
        return repository.findByCode(code)
                .filter(Device::isActive)
                .filter(device -> passwordEncoder.matches(secret, device.getKeyHash()))
                .map(device -> new AuthenticatedDevice(device.getId(), device.getCode()));
    }

    private String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Device require(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> DomainException.notFound("Device not found"));
    }
}
