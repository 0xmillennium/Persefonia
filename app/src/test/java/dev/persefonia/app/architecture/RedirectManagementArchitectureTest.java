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

class RedirectManagementArchitectureTest {
    // The exact Atom feed route (@GetMapping("/feed.xml")) is an accepted public surface; RSS/Atom
    // aliases, feed wildcards/collections, and listing routes remain forbidden.
    private static final Pattern FORBIDDEN_PUBLIC_ROUTE =
            Pattern.compile(
                    "@(?:GetMapping|PostMapping|RequestMapping)\\s*\\([^\\n]*(rss|atom|listing|/feeds|/feed\\.xml/|\"/feed\")",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern REDIRECT_RULE_CONSTRUCTION =
            Pattern.compile("\\b(?:new\\s+RedirectRule\\s*\\(|RedirectRule\\.(?:create|createManual|createSlugChanged)\\s*\\()");

    @Test
    void adminRedirectControllerUsesApplicationPortsNotRepositoriesOrPersistence() {
        noClasses()
                .that().haveSimpleName("AdminRedirectController")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app.discovery.persistence..",
                        "org.springframework.jdbc..",
                        "java.sql..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void adminRedirectControllerDoesNotConstructRedirectRuleAggregate() throws IOException {
        Path source = Path.of("../web-admin/src/main/java/dev/persefonia/webadmin/discovery/AdminRedirectController.java");
        assertThat(REDIRECT_RULE_CONSTRUCTION.matcher(Files.readString(source)).find()).isFalse();
    }

    @Test
    void webPublicDoesNotImportDiscoveryRepositories() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.webpublic..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.discovery.domain..",
                        "dev.persefonia.app.discovery.persistence..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void contentPublishingDoesNotImportRedirectAdminPackages() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.contentpublishing..")
                .should().dependOnClassesThat().resideInAPackage("dev.persefonia.webadmin.discovery..")
                .allowEmptyShould(true)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void discoveryDoesNotImportAppOrWebAdmin() {
        noClasses()
                .that().resideInAPackage("dev.persefonia.discovery..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.persefonia.app..",
                        "dev.persefonia.webadmin..")
                .allowEmptyShould(false)
                .check(ArchitectureTestSupport.PRODUCTION_CLASSES);
    }

    @Test
    void noEventOutboxAnalyticsOrForbiddenPublicRoutesIntroduced() throws IOException {
        try (Stream<Path> sources = Files.walk(Path.of(".."))) {
            List<Path> forbidden = sources
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(RedirectManagementArchitectureTest::containsForbiddenRedirectManagementText)
                    .toList();

            assertThat(forbidden).isEmpty();
        }
    }

    private static boolean containsForbiddenRedirectManagementText(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("@TransactionalEventListener")
                    || containsPostCommitMechanicsOutsideFoundation(path, source)
                    || source.toLowerCase().contains("outbox")
                    || source.contains("hitCount")
                    || source.toLowerCase().contains("redirect analytics")
                    || FORBIDDEN_PUBLIC_ROUTE.matcher(source).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static boolean containsPostCommitMechanicsOutsideFoundation(Path path, String source) {
        if (path.toString().contains("/app/src/main/java/dev/persefonia/app/transaction/")) {
            return false;
        }
        return source.contains("TransactionSynchronization") || source.contains("afterCommit");
    }
}
