package dev.persefonia.app.insights.persistence;

import dev.persefonia.insights.application.command.RecordInsightObservationCommand;
import dev.persefonia.insights.application.port.InsightsCounterRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcInsightsCounterRepositoryAdapter implements InsightsCounterRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final Clock clock;

    JdbcInsightsCounterRepositoryAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public void increment(RecordInsightObservationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID dimensionId = findDimension(command).orElseGet(() -> createDimension(command));
        incrementCounter(command, dimensionId);
    }

    private java.util.Optional<UUID> findDimension(RecordInsightObservationCommand command) {
        return jdbc().query("""
                SELECT id
                FROM insights.analytics_dimensions
                WHERE metric = :metric AND surface = :surface
                """, Map.of(
                "metric", command.metric().name(),
                "surface", command.surface().name()), (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class))
                .stream()
                .findFirst();
    }

    private UUID createDimension(RecordInsightObservationCommand command) {
        UUID dimensionId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc().update("""
                INSERT INTO insights.analytics_dimensions (
                    id, metric, surface, created_at
                ) VALUES (
                    :id, :metric, :surface, :createdAt
                )
                ON CONFLICT (metric, surface) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", dimensionId)
                .addValue("metric", command.metric().name())
                .addValue("surface", command.surface().name())
                .addValue("createdAt", Timestamp.from(now)));
        return findDimension(command).orElse(dimensionId);
    }

    private void incrementCounter(RecordInsightObservationCommand command, UUID dimensionId) {
        UUID counterId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc().update("""
                INSERT INTO insights.analytics_counters (
                    id, metric, period_start, period_granularity, dimension_id, count,
                    first_seen_at, last_seen_at, version
                ) VALUES (
                    :id, :metric, :periodStart, 'DAY', :dimensionId, :amount,
                    :now, :now, 0
                )
                ON CONFLICT (metric, period_start, period_granularity, dimension_id)
                DO UPDATE SET
                    count = insights.analytics_counters.count + EXCLUDED.count,
                    last_seen_at = EXCLUDED.last_seen_at,
                    version = insights.analytics_counters.version + 1
                """, new MapSqlParameterSource()
                .addValue("id", counterId)
                .addValue("metric", command.metric().name())
                .addValue("periodStart", Date.valueOf(command.date()))
                .addValue("dimensionId", dimensionId)
                .addValue("amount", command.amount())
                .addValue("now", Timestamp.from(now)));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException("JDBC is unavailable");
        }
        return available;
    }
}
