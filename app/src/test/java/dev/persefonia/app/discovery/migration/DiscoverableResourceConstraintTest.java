package dev.persefonia.app.discovery.migration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DiscoverableResourceConstraintTest extends DiscoveryMigrationDatabase {
    @Test
    void uniqueNullsNotDistinctKeyRejectsDuplicateButSourceReferenceMayOwnMultipleKeys() {
        UUID sourceId = UUID.randomUUID();
        Map<String, Object> first = new HashMap<>();
        first.put("sourceEntityId", sourceId);
        first.put("language", null);
        insertResource(first);

        Map<String, Object> duplicateKey = new HashMap<>(first);
        duplicateKey.put("id", UUID.randomUUID());
        duplicateKey.put("publicUrl", "/other");
        duplicateKey.put("canonicalUrl", "https://example.test/other");
        assertThatThrownBy(() -> insertResource(duplicateKey)).isInstanceOf(DataIntegrityViolationException.class);

        assertThatCode(() -> insertResource(Map.of(
                "sourceEntityId", sourceId,
                "resourceType", "PAGE")))
                .doesNotThrowAnyException();
    }

    @Test
    void publicAndCanonicalUrlsAreUnique() {
        insertResource(Map.of("publicUrl", "/unique", "canonicalUrl", "https://example.test/unique"));

        assertRejected(Map.of("publicUrl", "/unique"));
        assertRejected(Map.of("canonicalUrl", "https://example.test/unique"));
    }

    @Test
    void requiredTextPathAndVersionConstraintsRejectInvalidValues() {
        assertRejected(Map.of("publicUrl", " "));
        assertRejected(Map.of("publicUrl", "https://example.test/absolute"));
        assertRejected(Map.of("canonicalUrl", " "));
        assertRejected(Map.of("title", " "));
        assertRejected(Map.of("summary", " "));
        assertRejected(Map.of("searchText", " "));
        assertRejected(Map.of("version", -1L));
    }

    @Test
    void enumLikeConstraintsRejectUnknownValues() {
        assertRejected(Map.of("resourceType", "UNKNOWN"));
        assertRejected(Map.of("routePurpose", "UNKNOWN"));
        assertRejected(Map.of("language", "DE"));
        assertRejected(Map.of("indexingPolicy", "UNKNOWN"));
        assertRejected(Map.of("searchEligibility", "UNKNOWN"));
        assertRejected(Map.of("sitemapEligibility", "UNKNOWN"));
        assertRejected(Map.of("feedEligibility", "UNKNOWN"));
    }

    private void assertRejected(Map<String, Object> overrides) {
        assertThatThrownBy(() -> insertResource(overrides)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
