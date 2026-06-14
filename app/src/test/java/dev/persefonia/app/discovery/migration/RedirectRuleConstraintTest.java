package dev.persefonia.app.discovery.migration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RedirectRuleConstraintTest extends DiscoveryMigrationDatabase {
    @Test
    void activeSourceUrlIsUniqueWhileInactiveDuplicatesAreAllowed() {
        insertRedirect(Map.of("sourceUrl", "/duplicate-active"));
        assertRejected(Map.of("sourceUrl", "/duplicate-active"));

        insertRedirect(Map.of("sourceUrl", "/duplicate-inactive", "active", false));
        assertThatCode(() -> insertRedirect(Map.of("sourceUrl", "/duplicate-inactive", "active", false)))
                .doesNotThrowAnyException();
    }

    @Test
    void redirectSemanticsAreEnforced() {
        assertRejected(Map.of("sourceUrl", "/same", "targetUrl", "/same"));
        assertRejected(Map.of("statusCode", 303));
        assertRejected(Map.of("reason", "SLUG_CHANGED", "statusCode", 308));
        assertRejected(Map.of("sourceUrl", " "));
        assertRejected(Map.of("targetUrl", " "));
        assertRejected(Map.of("version", -1L));
    }

    @Test
    void sourceReferenceMustBeAllPresentOrAllAbsent() {
        Map<String, Object> partial = new HashMap<>();
        partial.put("sourceContext", "CONTENT_PUBLISHING");
        assertRejected(partial);

        assertThatCode(() -> insertRedirect(Map.of(
                "sourceContext", "CONTENT_PUBLISHING",
                "sourceType", "CONTENT_ITEM",
                "sourceEntityId", UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    private void assertRejected(Map<String, Object> overrides) {
        assertThatThrownBy(() -> insertRedirect(overrides)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
