package dev.persefonia.app.discovery.persistence;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.domain.RedirectRule;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.discovery.domain.SourceEntityRef;
import dev.persefonia.discovery.domain.Version;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

final class RedirectRuleMapper {
    RedirectRule fromRow(ResultSet row, int rowNumber) throws SQLException {
        return RedirectRule.create(
                new RedirectRuleId(row.getObject("id", java.util.UUID.class)),
                new PublicUrl(row.getString("source_url")),
                new PublicUrl(row.getString("target_url")),
                statusCode(row.getInt("status_code")),
                enumValue(RedirectReason.class, row.getString("reason")),
                sourceRef(row),
                row.getBoolean("active"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                Version.of(row.getLong("version")));
    }

    private SourceEntityRef sourceRef(ResultSet row) throws SQLException {
        String sourceContext = row.getString("source_context");
        if (sourceContext == null) {
            return null;
        }
        return new SourceEntityRef(
                enumValue(SourceContext.class, sourceContext),
                enumValue(SourceType.class, row.getString("source_type")),
                new SourceEntityId(row.getObject("source_entity_id", java.util.UUID.class)));
    }

    private RedirectStatusCode statusCode(int value) {
        return Arrays.stream(RedirectStatusCode.values())
                .filter(status -> status.value() == value)
                .findFirst()
                .orElseThrow(() -> new DiscoveryPersistenceException("Unknown persisted redirect status code: " + value));
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new DiscoveryPersistenceException("Unknown persisted " + type.getSimpleName() + ": " + value, exception);
        }
    }
}
