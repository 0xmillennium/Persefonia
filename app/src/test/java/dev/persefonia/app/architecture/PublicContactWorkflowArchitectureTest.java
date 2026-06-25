package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PublicContactWorkflowArchitectureTest {
    private static final Pattern ROUTE_ANNOTATION = Pattern.compile(
            "@(?:GetMapping|PostMapping|RequestMapping)\\s*\\(([^)]*)\\)", Pattern.DOTALL);

    @Test
    void onlyExactPublicContactRoutesExist() throws Exception {
        String publicRoutes = routeAnnotations(joinedSources(List.of(Path.of("../web-public/src/main/java"))));
        String adminRoutes = routeAnnotations(joinedSources(List.of(Path.of("../web-admin/src/main/java"))));

        assertThat(publicRoutes)
                .contains("/contact")
                .doesNotContain("/contact/")
                .doesNotContain("/contact/**")
                .doesNotContain("/api/contact");
        assertThat(adminRoutes)
                .contains("/admin/contact")
                .doesNotContain("/admin/insights")
                .doesNotContain("/admin/analytics");
    }

    @Test
    void publicContactDoesNotUseMailPostCommitOrInsights() throws Exception {
        String sources = joinedSources(List.of(
                Path.of("../web-public/src/main/java/dev/persefonia/webpublic/contact"),
                Path.of("../communication/src/main/java/dev/persefonia/communication/application/command")));

        assertThat(sources)
                .doesNotContain("MailNotificationPort")
                .doesNotContain("JavaMailSender")
                .doesNotContain("PostCommitTaskExecutor")
                .doesNotContain("RecordAnalytics")
                .doesNotContain("AnalyticsCounter")
                .doesNotContain("CONTACT_FORM_SUBMITTED")
                .doesNotContain("analytics_counters");
    }

    @Test
    void publicContactTemplateHasNoImageMetadataOrUnsafeEmbeds() throws Exception {
        String template = Files.readString(Path.of("src/main/jte/site/contact/index.jte"));

        assertThat(template)
                .doesNotContain("og:image")
                .doesNotContain("twitter:image")
                .doesNotContain("href=\"\"")
                .doesNotContain("<iframe")
                .doesNotContain("<embed")
                .doesNotContain("<object");
    }

    private static String routeAnnotations(String source) {
        return ROUTE_ANNOTATION.matcher(source)
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
                        .map(PublicContactWorkflowArchitectureTest::read)
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
