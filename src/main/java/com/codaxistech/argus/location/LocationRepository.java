package com.codaxistech.argus.location;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findFirstByDeviceIdOrderByTsDesc(UUID deviceId);

    /**
     * Keyset pagination: {@code ts < cursor}, never OFFSET, which degrades linearly on
     * a large append-only table.
     */
    @Query("""
            SELECT l FROM Location l
            WHERE l.deviceId = :deviceId
              AND l.ts >= :from
              AND l.ts < :cursor
            ORDER BY l.ts DESC
            """)
    List<Location> history(@Param("deviceId") UUID deviceId,
                           @Param("from") Instant from,
                           @Param("cursor") Instant cursor,
                           Limit limit);
}
