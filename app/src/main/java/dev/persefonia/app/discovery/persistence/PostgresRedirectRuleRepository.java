package dev.persefonia.app.discovery.persistence;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.domain.RedirectRule;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import dev.persefonia.discovery.domain.SourceEntityRef;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresRedirectRuleRepository implements RedirectRuleRepository {
    private static final String COLUMNS = """
            id, source_url, target_url, status_code, reason, source_context, source_type, source_entity_id,
            active, created_at, updated_at, version
            """;

    private static final String INSERT = """
            INSERT INTO discovery.redirect_rules (
                id, source_url, target_url, status_code, reason, source_context, source_type, source_entity_id,
                active, created_at, updated_at, version
            ) VALUES (
                :id, :sourceUrl, :targetUrl, :statusCode, :reason, :sourceContext, :sourceType, :sourceEntityId,
                :active, :createdAt, :updatedAt, :version
            )
            RETURNING
            """ + COLUMNS;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final RedirectRuleMapper mapper = new RedirectRuleMapper();

    PostgresRedirectRuleRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RedirectRule save(RedirectRule redirectRule) {
        Objects.requireNonNull(redirectRule, "redirectRule");
        return one(INSERT, parameters(redirectRule));
    }

    @Override
    public Optional<RedirectRule> findById(RedirectRuleId id) {
        Objects.requireNonNull(id, "id");
        return optional("SELECT " + COLUMNS + """
                FROM discovery.redirect_rules
                WHERE id = :id
                """, new MapSqlParameterSource("id", id.value()));
    }

    @Override
    public Optional<RedirectRule> findActiveBySourceUrl(PublicUrl sourceUrl) {
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        return optional("SELECT " + COLUMNS + """
                FROM discovery.redirect_rules
                WHERE source_url = :sourceUrl
                  AND active = true
                """, new MapSqlParameterSource("sourceUrl", sourceUrl.value()));
    }

    @Override
    public List<RedirectRule> findBySourceRef(SourceEntityRef sourceRef) {
        Objects.requireNonNull(sourceRef, "sourceRef");
        return query("SELECT " + COLUMNS + """
                FROM discovery.redirect_rules
                WHERE source_context = :sourceContext
                  AND source_type = :sourceType
                  AND source_entity_id = :sourceEntityId
                ORDER BY created_at, id
                """, sourceRefParameters(sourceRef));
    }

    @Override
    public Optional<RedirectRule> deactivate(RedirectRuleId id, Instant updatedAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(updatedAt, "updatedAt");
        List<RedirectRule> updated = query("""
                UPDATE discovery.redirect_rules
                SET active = false,
                    updated_at = :updatedAt,
                    version = version + 1
                WHERE id = :id
                  AND active = true
                RETURNING
                """ + COLUMNS, new MapSqlParameterSource()
                .addValue("id", id.value())
                .addValue("updatedAt", Timestamp.from(updatedAt)));
        return updated.isEmpty() ? findById(id) : Optional.of(updated.getFirst());
    }

    private MapSqlParameterSource parameters(RedirectRule rule) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", rule.id().value())
                .addValue("sourceUrl", rule.sourceUrl().value())
                .addValue("targetUrl", rule.targetUrl().value())
                .addValue("statusCode", rule.statusCode().value())
                .addValue("reason", rule.reason().name())
                .addValue("active", rule.active())
                .addValue("createdAt", Timestamp.from(rule.createdAt()))
                .addValue("updatedAt", Timestamp.from(rule.updatedAt()))
                .addValue("version", rule.version().value());
        rule.sourceRef().ifPresentOrElse(
                sourceRef -> parameters
                        .addValue("sourceContext", sourceRef.sourceContext().name())
                        .addValue("sourceType", sourceRef.sourceType().name())
                        .addValue("sourceEntityId", sourceRef.sourceEntityId().value()),
                () -> parameters
                        .addValue("sourceContext", null)
                        .addValue("sourceType", null)
                        .addValue("sourceEntityId", null));
        return parameters;
    }

    private MapSqlParameterSource sourceRefParameters(SourceEntityRef sourceRef) {
        return new MapSqlParameterSource()
                .addValue("sourceContext", sourceRef.sourceContext().name())
                .addValue("sourceType", sourceRef.sourceType().name())
                .addValue("sourceEntityId", sourceRef.sourceEntityId().value());
    }

    private RedirectRule one(String sql, MapSqlParameterSource parameters) {
        return query(sql, parameters).stream()
                .findFirst()
                .orElseThrow(() -> new DiscoveryPersistenceException("Expected a redirect rule row"));
    }

    private Optional<RedirectRule> optional(String sql, MapSqlParameterSource parameters) {
        return query(sql, parameters).stream().findFirst();
    }

    private List<RedirectRule> query(String sql, MapSqlParameterSource parameters) {
        return jdbc().query(sql, parameters, mapper::fromRow);
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new DiscoveryPersistenceException("JDBC discovery infrastructure is not available");
        }
        return available;
    }
}
