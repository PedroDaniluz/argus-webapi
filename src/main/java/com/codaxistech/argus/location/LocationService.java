package com.codaxistech.argus.location;

import com.codaxistech.argus.device.DeviceFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class LocationService {

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    /**
     * Not JPA: ON CONFLICT DO NOTHING is what makes a buffer replay idempotent, and
     * RETURNING names the rows that landed without a second SELECT.
     */
    private static final String INSERT = """
            INSERT INTO location (device_id, ts, lat, lon, speed_mps, course_deg, sats, hdop)
            VALUES %s
            ON CONFLICT ON CONSTRAINT uq_location DO NOTHING
            RETURNING id, ts, lat, lon, speed_mps, course_deg, sats, hdop, received_at
            """;
    private static final String ROW = "(?, ?, ?, ?, ?, ?, ?, ?)";
    private static final int COLUMNS = 8;

    private final LocationRepository repository;
    private final JdbcTemplate jdbc;
    private final DeviceFacade devices;
    private final ApplicationEventPublisher events;

    LocationService(LocationRepository repository, JdbcTemplate jdbc,
                    DeviceFacade devices, ApplicationEventPublisher events) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.devices = devices;
        this.events = events;
    }

    /** After a buffer replay, duplicates and out-of-order samples are normal, not errors. */
    @Transactional
    IngestLocationsResponse ingest(UUID deviceId, String deviceCode,
                                              List<LocationSample> samples) {
        int received = samples.size();
        List<LocationSample> candidates = new ArrayList<>(received);
        Set<Long> seen = new HashSet<>();
        int invalid = 0;

        for (LocationSample sample : samples) {
            if (!isValid(sample)) {
                invalid++;
                continue;
            }
            if (!seen.add(sample.ts())) {
                continue;   // repeated inside this very batch
            }
            candidates.add(sample);
        }
        if (invalid > 0) {
            log.warn("device {}: dropped {} of {} samples as invalid", deviceCode, invalid, received);
        }
        if (candidates.isEmpty()) {
            return new IngestLocationsResponse(received, 0, received - invalid);
        }

        List<LocationResponse> stored = insert(deviceId, deviceCode, candidates);
        if (!stored.isEmpty()) {
            events.publishEvent(new LocationsStored(stored));
        }
        return new IngestLocationsResponse(received, stored.size(),
                received - invalid - stored.size());
    }

    private List<LocationResponse> insert(UUID deviceId, String deviceCode,
                                               List<LocationSample> samples) {
        String sql = INSERT.formatted(String.join(", ", Collections.nCopies(samples.size(), ROW)));
        RowMapper<LocationResponse> mapper = (rs, rowNum) -> fromRow(rs, deviceCode);

        return jdbc.query(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            int i = 1;
            for (LocationSample sample : samples) {
                ps.setObject(i++, deviceId);
                ps.setObject(i++, OffsetDateTime.ofInstant(
                        Instant.ofEpochSecond(sample.ts()), ZoneOffset.UTC));
                ps.setDouble(i++, sample.lat());
                ps.setDouble(i++, sample.lon());
                setFloat(ps, i++, sample.speedMps());
                setFloat(ps, i++, sample.courseDeg());
                setShort(ps, i++, sample.sats());
                setFloat(ps, i++, sample.hdop());
            }
            if (i - 1 != samples.size() * COLUMNS) {
                throw new IllegalStateException("parameter count does not match the INSERT");
            }
            return ps;
        }, mapper);
    }

    /** A bad GNSS read, not data: better dropped than drawn in the middle of the ocean. */
    private static boolean isValid(LocationSample sample) {
        if (sample == null || sample.ts() == null || sample.lat() == null || sample.lon() == null) {
            return false;
        }
        if (!Double.isFinite(sample.lat()) || !Double.isFinite(sample.lon())) {
            return false;
        }
        if (sample.lat() < -90 || sample.lat() > 90 || sample.lon() < -180 || sample.lon() > 180) {
            return false;
        }
        // 2000-01-01 to one day ahead: catches a clock with no fix, and epochs sent in ms.
        long now = Instant.now().getEpochSecond();
        return sample.ts() >= 946_684_800L && sample.ts() <= now + 86_400L;
    }

    /** Newest first. {@code to} doubles as the cursor. */
    LocationPage history(String deviceCode, Instant from, Instant to, Integer limit) {
        UUID deviceId = devices.requireIdByCode(deviceCode);
        int size = normalizeLimit(limit);
        Instant start = from != null ? from : Instant.EPOCH;
        Instant cursor = to != null ? to : Instant.now().plusSeconds(86_400);

        List<LocationResponse> items = repository
                .history(deviceId, start, cursor, Limit.of(size)).stream()
                .map(location -> LocationResponse.from(location, deviceCode))
                .toList();

        Instant next = items.size() < size ? null : items.getLast().ts();
        return new LocationPage(items, next);
    }

    /** Devices that never reported are left out. */
    List<LocationResponse> latest() {
        return devices.codesById().entrySet().stream()
                .map(device -> lastFor(device.getKey(), device.getValue()))
                .flatMap(Optional::stream)
                .toList();
    }

    Optional<LocationResponse> lastFor(UUID deviceId, String deviceCode) {
        return repository.findFirstByDeviceIdOrderByTsDesc(deviceId)
                .map(location -> LocationResponse.from(location, deviceCode));
    }

    private static int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }


    private static LocationResponse fromRow(ResultSet rs, String deviceCode)
            throws SQLException {
        return new LocationResponse(
                rs.getLong("id"),
                deviceCode,
                rs.getObject("ts", OffsetDateTime.class).toInstant(),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                getFloat(rs, "speed_mps"),
                getFloat(rs, "course_deg"),
                getShort(rs, "sats"),
                getFloat(rs, "hdop"),
                rs.getObject("received_at", OffsetDateTime.class).toInstant());
    }

    private static void setFloat(PreparedStatement ps, int index, Float value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.REAL);
        } else {
            ps.setFloat(index, value);
        }
    }

    private static void setShort(PreparedStatement ps, int index, Short value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.SMALLINT);
        } else {
            ps.setShort(index, value);
        }
    }

    private static Float getFloat(ResultSet rs, String column) throws SQLException {
        float value = rs.getFloat(column);
        return rs.wasNull() ? null : value;
    }

    private static Short getShort(ResultSet rs, String column) throws SQLException {
        short value = rs.getShort(column);
        return rs.wasNull() ? null : value;
    }
}
