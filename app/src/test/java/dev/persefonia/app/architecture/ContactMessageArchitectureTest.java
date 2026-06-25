package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ContactMessageArchitectureTest {
    private static final List<String> REQUEST_METADATA_TERMS = List.of(
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
            "country_code",
            "HttpServletRequest");

    @Test
    void contactMessageProductionApisDoNotAcceptRequestMetadata() throws Exception {
        String communicationSources = joinedSources(Path.of("../communication/src/main/java"));

        assertThat(communicationSources).doesNotContain(REQUEST_METADATA_TERMS.toArray(String[]::new));
    }

    @Test
    void onlyExactPublicContactRouteIsOpenedAndAdminInsightsRemainClosed() throws Exception {
        String routeAnnotations = routeAnnotations(
                joinedSources(Path.of("../web-public/src/main/java"))
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

    @Test
    void webModulesDoNotUsePersistenceRedisOrMailShortcuts() throws Exception {
        String webSources = joinedSources(Path.of("../web-public/src/main/java"))
                + joinedSources(Path.of("../web-admin/src/main/java"));

        assertThat(webSources)
                .doesNotContain("Repository")
                .doesNotContain("JdbcTemplate")
                .doesNotContain("NamedParameterJdbcTemplate")
                .doesNotContain("RedisTemplate")
                .doesNotContain("StringRedisTemplate")
                .doesNotContain("JavaMailSender");
    }

    @Test
    void contactFoundationDoesNotWriteInsightsOrIntroduceRedisOrSpringMailAdapters() throws Exception {
        String contactRelatedProductionSources = joinedSources(Path.of("../communication/src/main/java"))
                + joinedSources(Path.of("src/main/java/dev/persefonia/app/communication"))
                + joinedSources(Path.of("../web-public/src/main/java"))
                + joinedSources(Path.of("../web-admin/src/main/java"));
        String contactProductionSourcesOutsideMailAdapter = joinedSources(Path.of("../communication/src/main/java"))
                + joinedSources(Path.of("src/main/java/dev/persefonia/app/communication/application"))
                + joinedSources(Path.of("src/main/java/dev/persefonia/app/communication/persistence"))
                + joinedSources(Path.of("../web-public/src/main/java"))
                + joinedSources(Path.of("../web-admin/src/main/java"));

        assertThat(contactRelatedProductionSources)
                .doesNotContain("analytics_counters")
                .doesNotContain("analytics_dimensions")
                .doesNotContain("RecordAnalytics")
                .doesNotContain("AnalyticsCounter")
                .doesNotContain("StringRedisTemplate")
                .doesNotContain("RedisTemplate");
        assertThat(contactProductionSourcesOutsideMailAdapter)
                .doesNotContain("JavaMailSender");
    }

    private static String routeAnnotations(String source) {
        return Pattern.compile("@(?:GetMapping|PostMapping|RequestMapping)\\s*\\(([^)]*)\\)", Pattern.DOTALL)
                .matcher(source)
                .results()
                .map(match -> match.group(1))
                .reduce("", String::concat);
    }

    private static String joinedSources(Path root) throws IOException {
        if (!Files.exists(root)) {
            return "";
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".sql"))
                    .map(ContactMessageArchitectureTest::read)
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
