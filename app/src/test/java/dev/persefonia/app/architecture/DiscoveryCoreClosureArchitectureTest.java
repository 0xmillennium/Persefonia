package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DiscoveryCoreClosureArchitectureTest {
    // The exact Atom feed route (@GetMapping("/feed.xml")) is an accepted public surface; RSS/Atom
    // aliases, feed wildcards/collections, and listing routes remain forbidden.
    private static final Pattern FORBIDDEN_PUBLIC_ROUTE = Pattern.compile(
            "@(?:GetMapping|PostMapping|RequestMapping)\\s*\\([^\\n]*(rss|atom|listing|/feeds|/feed\\.xml/|\"/feed\")",
            Pattern.CASE_INSENSITIVE);

    @Test
    void discoveryMigrationKeepsDiscoverableResourcesCurrentOnlyAndRedirectRulesActivatable() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V4__discovery.sql"));
        String discoverableResourcesTable = tableDefinition(migration, "discovery.discoverable_resources");
        String redirectRulesTable = tableDefinition(migration, "discovery.redirect_rules");

        assertThat(discoverableResourcesTable).doesNotContain("active boolean");
        assertThat(migration).doesNotContain("discoverable_resource_history");
        assertThat(migration).doesNotContain("discoverable_resources_history");
        assertThat(redirectRulesTable).contains("active boolean NOT NULL");
    }

    @Test
    void discoveryCoreDoesNotIntroduceDeferredPublicSurfacesOrEventMechanics() throws Exception {
        try (var migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            assertThat(migrations.map(path -> path.getFileName().toString()))
                    .contains(
                            "V1__create_schemas.sql",
                            "V2__iam.sql",
                            "V3__publishing.sql",
                            "V4__discovery.sql");
        }

        assertNoForbiddenPublicRoutes("../web-public/src/main/java");
        assertNoRouteText("src/main/java", "outbox");
        assertNoRouteTextOutsidePostCommitFoundation("src/main/java", "afterCommit", "TransactionSynchronization");
    }

    private static String tableDefinition(String migration, String tableName) {
        int start = migration.indexOf("CREATE TABLE " + tableName);
        assertThat(start).isNotNegative();
        int end = migration.indexOf("\n);", start);
        assertThat(end).isGreaterThan(start);
        return migration.substring(start, end);
    }

    private static void assertNoRouteText(String root, String... forbiddenText) throws Exception {
        try (var paths = Files.walk(Path.of(root))) {
            assertThat(paths
                            .filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> containsAny(path, forbiddenText))
                            .map(Path::toString))
                    .isEmpty();
        }
    }

    private static void assertNoRouteTextOutsidePostCommitFoundation(String root, String... forbiddenText) throws Exception {
        try (var paths = Files.walk(Path.of(root))) {
            assertThat(paths
                            .filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> !path.toString().contains("/dev/persefonia/app/transaction/"))
                            .filter(path -> containsAny(path, forbiddenText))
                            .map(Path::toString))
                    .isEmpty();
        }
    }

    private static void assertNoForbiddenPublicRoutes(String root) throws Exception {
        try (var paths = Files.walk(Path.of(root))) {
            assertThat(paths
                            .filter(path -> path.toString().endsWith(".java"))
                            .filter(DiscoveryCoreClosureArchitectureTest::containsForbiddenPublicRoute)
                            .map(Path::toString))
                    .isEmpty();
        }
    }

    private static boolean containsAny(Path path, String... forbiddenText) {
        try {
            String source = Files.readString(path);
            for (String text : forbiddenText) {
                if (source.contains(text)) {
                    return true;
                }
            }
            return false;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static boolean containsForbiddenPublicRoute(Path path) {
        try {
            return FORBIDDEN_PUBLIC_ROUTE.matcher(Files.readString(path)).find();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
