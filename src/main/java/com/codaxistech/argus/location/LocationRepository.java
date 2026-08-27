package com.codaxistech.argus.location;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findFirstByDeviceIdOrderByTsDesc(UUID deviceId);

    /**
     * Paginacao por keyset: {@code ts < cursor}, nunca OFFSET. Em tabela
     * append-only grande o OFFSET degrada linearmente, porque o banco precisa
     * varrer e descartar tudo que ficou para tras.
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
