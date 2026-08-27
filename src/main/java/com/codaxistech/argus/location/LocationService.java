package com.codaxistech.argus.location;

import com.codaxistech.argus.device.DeviceDtos;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LocationService {

    /** Teto de linhas por pagina de historico. Protege o painel de si mesmo. */
    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    /**
     * A ingestao nao passa por JPA. Precisa de ON CONFLICT DO NOTHING, que e o que
     * torna o replay de buffer idempotente, e o RETURNING diz exatamente quais
     * linhas entraram — sem um SELECT extra so para descobrir o que era duplicata.
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

    /**
     * Grava um lote. Amostra invalida e descartada, amostra repetida e ignorada
     * pelo banco, e nenhuma das duas derruba o resto — depois de um replay de
     * buffer, duplicata e amostra fora de ordem sao o caso normal, nao excecao.
     */
    @Transactional
    public LocationDtos.IngestResponse ingest(UUID deviceId, String deviceCode,
                                              List<LocationDtos.Sample> samples) {
        int received = samples.size();
        List<LocationDtos.Sample> candidates = new ArrayList<>(received);
        Set<Long> seen = new HashSet<>();
        int invalid = 0;

        for (LocationDtos.Sample sample : samples) {
            if (!isValid(sample)) {
                invalid++;
                continue;
            }
            if (!seen.add(sample.ts())) {
                continue;   // repetida dentro do proprio lote
            }
            candidates.add(sample);
        }
        if (invalid > 0) {
            log.warn("dispositivo {}: {} de {} amostras descartadas por invalidas",
                    deviceCode, invalid, received);
        }
        if (candidates.isEmpty()) {
            return new LocationDtos.IngestResponse(received, 0, received - invalid);
        }

        List<LocationDtos.Response> stored = insert(deviceId, deviceCode, candidates);
        if (!stored.isEmpty()) {
            events.publishEvent(new LocationDtos.Stored(stored));
        }
        int duplicates = received - invalid - stored.size();
        return new LocationDtos.IngestResponse(received, stored.size(), duplicates);
    }

    private List<LocationDtos.Response> insert(UUID deviceId, String deviceCode,
                                               List<LocationDtos.Sample> samples) {
        String values = String.join(", ", java.util.Collections.nCopies(samples.size(), ROW));
        String sql = INSERT.formatted(values);
        RowMapper<LocationDtos.Response> mapper = (rs, rowNum) -> toResponse(rs, deviceCode);

        return jdbc.query(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            int i = 1;
            for (LocationDtos.Sample sample : samples) {
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
                throw new IllegalStateException("numero de parametros nao bate com o INSERT");
            }
            return ps;
        }, mapper);
    }

    /**
     * Coordenada fora de faixa, epoch absurdo ou campo obrigatorio ausente sao
     * defeito de leitura do GNSS, nao dado. Vale mais descartar do que gravar uma
     * posicao que o painel vai desenhar no meio do oceano.
     */
    private static boolean isValid(LocationDtos.Sample sample) {
        if (sample == null || sample.ts() == null || sample.lat() == null || sample.lon() == null) {
            return false;
        }
        if (sample.lat() < -90 || sample.lat() > 90 || sample.lon() < -180 || sample.lon() > 180) {
            return false;
        }
        if (!Double.isFinite(sample.lat()) || !Double.isFinite(sample.lon())) {
            return false;
        }
        // 2000-01-01 ate um dia no futuro: cobre relogio ainda sem fix e evita
        // que uma amostra com epoch em milissegundos entre como ano 56000.
        long now = Instant.now().getEpochSecond();
        return sample.ts() >= 946_684_800L && sample.ts() <= now + 86_400L;
    }

    /**
     * Historico de um dispositivo, do mais recente para o mais antigo.
     * {@code to} funciona como cursor: repasse o {@code nextCursor} da pagina
     * anterior e a leitura continua de onde parou, sem OFFSET.
     */
    public LocationDtos.Page history(String deviceCode, Instant from, Instant to, Integer limit) {
        DeviceDtos.Response device = devices.byCode(deviceCode);
        int size = normalizeLimit(limit);
        Instant start = from != null ? from : Instant.EPOCH;
        Instant cursor = to != null ? to : Instant.now().plusSeconds(86_400);

        List<LocationDtos.Response> items = repository
                .history(device.id(), start, cursor, Limit.of(size)).stream()
                .map(location -> toResponse(location, device.code()))
                .toList();

        // So oferece proxima pagina quando encheu: pagina curta ja e o fim.
        Instant next = items.size() < size ? null : items.getLast().ts();
        return new LocationDtos.Page(items, next);
    }

    /** Ultima posicao conhecida de cada dispositivo. Quem nunca reportou fica de fora. */
    public List<LocationDtos.Response> latest() {
        return devices.all().stream()
                .map(device -> lastFor(device.id(), device.code()))
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<LocationDtos.Response> lastFor(UUID deviceId, String deviceCode) {
        return repository.findFirstByDeviceIdOrderByTsDesc(deviceId)
                .map(location -> toResponse(location, deviceCode));
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static LocationDtos.Response toResponse(Location location, String deviceCode) {
        return new LocationDtos.Response(
                location.getId(), deviceCode, location.getTs(),
                location.getLat(), location.getLon(),
                location.getSpeedMps(), location.getCourseDeg(),
                location.getSats(), location.getHdop(), location.getReceivedAt());
    }

    private static LocationDtos.Response toResponse(ResultSet rs, String deviceCode)
            throws SQLException {
        return new LocationDtos.Response(
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
