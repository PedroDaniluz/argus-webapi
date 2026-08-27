package com.codaxistech.argus.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByCode(String code);

    boolean existsByCode(String code);

    List<Device> findAllByOrderByCodeAsc();
}
