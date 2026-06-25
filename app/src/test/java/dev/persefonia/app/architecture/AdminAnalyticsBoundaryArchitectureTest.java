package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AdminAnalyticsBoundaryArchitectureTest {
    private static final Path WEB_ADMIN_ANALYTICS =
            Path.of("../web-admin/src/main/java/dev/persefonia/webadmin/analytics");
    private static final Path WEB_ADMIN_MAIN = Path.of("../web-admin/src/main/java");
    private static final Path WEB_PUBLIC_MAIN = Path.of("../web-public/src/main/java");
    private static final Path INSIGHTS_MAIN = Path.of("../insights/src/main/java");
    private static final Path ADMIN_ANALYTICS_TEMPLATES = Path.of("src/main/jte/admin/analytics");

    @Test
    void adminAnalyticsSurfaceIsReadOnlyWithoutInfrastructureOrWriteShortcuts() throws IOException {
        String sources = joinedSources(List.of(WEB_ADMIN_ANALYTICS));

        assertThat(sources)
                .contains("AdminAnalyticsSummaryQueryService")
                .doesNotContain("JdbcTemplate")
                .doesNotContain("NamedParameterJdbcTemplate")
                .doesNotContain("RedisTemplate")
                .doesNotContain("StringRedisTemplate")
                .doesNotContain("JavaMailSender")
                .doesNotContain("InsightsCounterRepository")
                .doesNotContain("RecordInsightObservation")
                .doesNotContain(".increment(")
                .doesNotContain("@PostMapping")
                .doesNotContain("@PutMapping")
                .doesNotContain("@DeleteMapping")
                .doesNotContain("@PatchMapping");
    }

    @Test
    void adminAnalyticsExposesOnlyAGetRoute() throws IOException {
        String adminRoutes = routeAnnotations(joinedSources(List.of(WEB_ADMIN_MAIN)));

        assertThat(adminRoutes).contains("/admin/analytics");

        String mutationRoutes = mutationRouteAnnotations(joinedSources(List.of(WEB_ADMIN_MAIN)));
        assertThat(mutationRoutes).doesNotContain("/admin/analytics");
    }

    @Test
    void noPublicAnalyticsOrTrackingRouteExists() throws IOException {
        String publicRoutes = routeAnnotations(joinedSources(List.of(WEB_PUBLIC_MAIN)));

        assertThat(publicRoutes)
                .doesNotContain("/analytics")
                .doesNotContain("/track")
                .doesNotContain("/api/analytics")
                .doesNotContain("/api/track")
                .doesNotContain("/insights")
                .doesNotContain("/api/insights");
    }

    @Test
    void analyticsJdbcAdapterLivesInAppCompositionRootNotWebOrInsightsModule() throws IOException {
        assertThat(joinedSources(List.of(WEB_ADMIN_ANALYTICS, INSIGHTS_MAIN)))
                .doesNotContain("NamedParameterJdbcTemplate")
                .doesNotContain("JdbcTemplate");

        assertThat(Files.exists(Path.of(
                "src/main/java/dev/persefonia/app/insights/persistence/"
                        + "JdbcAdminAnalyticsSummaryQueryService.java")))
                .isTrue();
    }

    @Test
    void adminAnalyticsTemplateRendersNoRawVisitorOrPathData() throws IOException {
        String templates = joinedSources(List.of(ADMIN_ANALYTICS_TEMPLATES));

        assertThat(templates)
                .doesNotContain("ipAddress")
                .doesNotContain("userAgent")
                .doesNotContain("referrer")
                .doesNotContain("searchTerm")
                .doesNotContain("sessionId")
                .doesNotContain("visitorId")
                .doesNotContain("publicPath")
                .doesNotContain("rawPath")
                .doesNotContain("${raw");
    }

    private static String routeAnnotations(String source) {
        return Pattern.compile("@(?:GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)"
                        + "\\s*\\(([^)]*)\\)", Pattern.DOTALL)
                .matcher(source)
                .results()
                .map(match -> match.group(1))
                .reduce("", String::concat);
    }

    private static String mutationRouteAnnotations(String source) {
        return Pattern.compile("@(?:PostMapping|PutMapping|DeleteMapping|PatchMapping)"
                        + "\\s*\\(([^)]*)\\)", Pattern.DOTALL)
                .matcher(source)
                .results()
                .map(match -> match.group(1))
                .reduce("", String::concat);
    }

    private static String joinedSources(List<Path> roots) throws IOException {
        StringBuilder joined = new StringBuilder();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                joined.append(paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".jte"))
                        .map(AdminAnalyticsBoundaryArchitectureTest::read)
                        .reduce("", String::concat));
            }
        }
        return joined.toString();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
