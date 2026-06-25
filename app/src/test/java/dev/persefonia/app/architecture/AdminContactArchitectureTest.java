package dev.persefonia.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AdminContactArchitectureTest {
    @Test
    void adminContactRoutesAreExactAndDoNotOpenAnalyticsOrApiRoutes() throws Exception {
        String routes = routeAnnotations(joinedSources(List.of(
                Path.of("../web-admin/src/main/java"),
                Path.of("../web-public/src/main/java"))));

        assertThat(routes)
                .contains("/admin/contact")
                .contains("/admin/contact/{messageId}")
                .contains("/admin/contact/{messageId}/read")
                .contains("/admin/contact/{messageId}/replied")
                .contains("/admin/contact/{messageId}/spam")
                .contains("/admin/contact/{messageId}/archive")
                .doesNotContain("/api/admin/contact")
                .doesNotContain("/admin/insights")
                .doesNotContain("/admin/analytics")
                .doesNotContain("/admin/contact/{messageId}/" + "delete")
                .doesNotContain("/admin/contact/{messageId}/" + "reply")
                .doesNotContain("/admin/contact/{messageId}/" + "resend")
                .doesNotContain("/admin/contact/bulk")
                .doesNotContain("/admin/contact/export");
    }

    @Test
    void webAdminContactUsesQueryAndCommandServicesWithoutInfrastructureShortcuts() throws Exception {
        String sources = joinedSources(List.of(Path.of("../web-admin/src/main/java/dev/persefonia/webadmin/contact")));

        assertThat(sources)
                .contains("ContactMessageAdminQueryService")
                .contains("UpdateContactMessageStatusCommandService")
                .doesNotContain("ContactMessageRepository")
                .doesNotContain("JdbcTemplate")
                .doesNotContain("NamedParameterJdbcTemplate")
                .doesNotContain("RedisTemplate")
                .doesNotContain("StringRedisTemplate")
                .doesNotContain("JavaMailSender")
                .doesNotContain("MailNotificationPort")
                .doesNotContain("PostCommitTaskExecutor")
                .doesNotContain("RecordAnalytics")
                .doesNotContain("AnalyticsCounter")
                .doesNotContain("Audit");
    }

    @Test
    void communicationApplicationDoesNotDependOnWebTemplateRedisMailOrJdbc() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.communication.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.security..",
                        "gg.jte..",
                        "org.springframework.data.redis..",
                        "org.springframework.mail..",
                        "java.sql..",
                        "javax.sql..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void adminContactTemplatesAvoidUnsafeRenderingAndOutOfScopeActions() throws Exception {
        String templates = joinedSources(List.of(Path.of("src/main/jte/admin/contact")));

        assertThat(templates)
                .contains("csrfField")
                .contains("<pre>${page.message().body()}</pre>")
                .doesNotContain("${raw")
                .doesNotContain("gg.jte.html")
                .doesNotContain("href=\"\"")
                .doesNotContain("<iframe")
                .doesNotContain("<embed")
                .doesNotContain("<object")
                .doesNotContain("/delete")
                .doesNotContain("/reply\"")
                .doesNotContain("/resend")
                .doesNotContain("/bulk")
                .doesNotContain("/export")
                .doesNotContain("/admin/analytics")
                .doesNotContain("/admin/insights");
    }

    private static String routeAnnotations(String source) {
        return Pattern.compile("@(?:GetMapping|PostMapping|RequestMapping)\\s*\\(([^)]*)\\)", Pattern.DOTALL)
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
                        .map(AdminContactArchitectureTest::read)
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
