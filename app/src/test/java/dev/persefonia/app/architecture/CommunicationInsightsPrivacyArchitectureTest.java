package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CommunicationInsightsPrivacyArchitectureTest {
    private static final List<String> COMMUNICATION_FORBIDDEN_COLUMNS = List.of(
            "raw_ip",
            "ip_address",
            "hashed_ip",
            "ip_hash",
            "user_agent",
            "user_agent_summary",
            "user_agent_hash",
            "rate_limit_key",
            "client_fingerprint",
            "session_id",
            "visitor_id",
            "tracking_cookie_id",
            "country_code");
    private static final List<String> INSIGHTS_FORBIDDEN_COLUMNS = List.of(
            "raw_ip",
            "ip_address",
            "hashed_ip",
            "ip_hash",
            "user_agent",
            "user_agent_summary",
            "user_agent_hash",
            "session_id",
            "visitor_id",
            "tracking_cookie_id",
            "country_code",
            "referrer",
            "referrer_domain");
    private static final List<String> RAW_ANALYTICS_TABLES = List.of(
            "analytics_events",
            "raw_events",
            "page_view_events",
            "search_events");

    @Test
    void communicationMigrationDoesNotAddPrivacyForbiddenColumns() throws Exception {
        String migrationSql = joinedSources(Path.of("src/main/resources/db/migration"));

        assertThat(migrationSql).doesNotContain(COMMUNICATION_FORBIDDEN_COLUMNS.toArray(String[]::new));
    }

    @Test
    void insightsMigrationDoesNotAddRawIdentityColumnsOrRawEventTables() throws Exception {
        String migrationSql = joinedSources(Path.of("src/main/resources/db/migration"));

        assertThat(migrationSql).doesNotContain(INSIGHTS_FORBIDDEN_COLUMNS.toArray(String[]::new));
        assertThat(migrationSql).doesNotContain(RAW_ANALYTICS_TABLES.toArray(String[]::new));
    }

    @Test
    void onlyExactPublicContactRouteIsIntroducedAndAdminInsightsRemainClosed() throws Exception {
        String routeAnnotations = routeAnnotations(joinedSources(Path.of("../web-public/src/main/java"))
                + joinedSources(Path.of("../web-admin/src/main/java")));

        assertThat(routeAnnotations)
                .contains("/contact")
                .doesNotContain("/contact/")
                .doesNotContain("/contact/**")
                .doesNotContain("/api/contact")
                .doesNotContain("/admin/contact")
                .doesNotContain("/admin/insights")
                .doesNotContain("/admin/analytics");
    }

    private static String routeAnnotations(String source) {
        return Pattern.compile("@(?:GetMapping|PostMapping|RequestMapping)\\s*\\(([^)]*)\\)", Pattern.DOTALL)
                .matcher(source)
                .results()
                .map(match -> match.group(1))
                .reduce("", String::concat);
    }

    private static String joinedSources(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".sql"))
                    .map(CommunicationInsightsPrivacyArchitectureTest::read)
                    .reduce("", String::concat);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
