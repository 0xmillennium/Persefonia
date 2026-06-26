package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.audit.application.service.AuditSafeValuePolicy;
import dev.persefonia.audit.domain.record.AuditValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the audit foundation against persisting or modelling forbidden,
 * privacy-sensitive data. The forbidden literals are assembled from fragments so
 * this guard never embeds the very columns it forbids; it scans only main
 * sources, never tests.
 */
class AuditPrivacyArchitectureTest {
    private static final List<String> FORBIDDEN_AUDIT_LITERALS = List.of(
            "to" + "ken",
            "se" + "ssion",
            "pass" + "word",
            "creden" + "tial",
            "raw" + "_ip",
            "hashed" + "_ip",
            "ip" + "_address",
            "user" + "_ag" + "ent",
            "finger" + "print",
            "rate" + "_limit_key",
            "redis" + "_abuse_key",
            "contact" + "_body",
            "message" + "_body",
            "sender" + "_email",
            "sender" + "_name",
            "email" + "_address",
            "markdown" + "_source",
            "rendered" + "_html",
            "request" + "_uri",
            "query" + "_string",
            "head" + "ers",
            "request" + "_headers",
            "smtp" + "_secret",
            "smtp" + "_credential",
            "cloudflare" + "_secret",
            "cloudflare" + "_credential",
            "private" + "_config",
            "private" + "_runtime_config",
            "private" + "_host",
            "internal" + "_host",
            "private" + "_hostname",
            "internal" + "_hostname",
            "request" + "_payload",
            "response" + "_payload",
            "principal" + "_payload",
            "cla" + "ims",
            "coo" + "kie",
            "author" + "ization");

    private static final List<String> FORBIDDEN_LIFECYCLE_COLUMNS = List.of(
            "updated" + "_at",
            "version",
            "active",
            "status",
            "deleted" + "_at",
            "archived" + "_at");

    private static final List<String> FORBIDDEN_FIRST_CLASS_FIELDS = List.of(
            "bo" + "dy",
            "message" + "Body",
            "contact" + "Body",
            "html" + "Body",
            "markdown" + "Source",
            "rendered" + "Html",
            "sender" + "Email",
            "sender" + "Name",
            "request" + "Headers",
            "query" + "String",
            "request" + "Uri",
            "raw" + "RequestUri",
            "request" + "Payload",
            "response" + "Payload",
            "principal" + "Payload");

    @Test
    void auditMigrationDoesNotIntroduceForbiddenColumns() {
        String migration = read(Path.of("src/main/resources/db/migration/V19__audit_foundation.sql"));

        assertThat(migration).doesNotContain(FORBIDDEN_AUDIT_LITERALS.toArray(String[]::new));
        assertThat(migration).doesNotContain(FORBIDDEN_LIFECYCLE_COLUMNS.toArray(String[]::new));
    }

    @Test
    void auditMainSourceDoesNotEmbedForbiddenLiterals() {
        String auditSource = joinedMainSources(Path.of("../audit/src/main/java"));

        assertThat(auditSource).doesNotContain(FORBIDDEN_AUDIT_LITERALS.toArray(String[]::new));
        assertThat(auditSource).doesNotContain(FORBIDDEN_FIRST_CLASS_FIELDS.toArray(String[]::new));
    }

    @Test
    void auditAppSourceDoesNotEmbedForbiddenLiterals() {
        String appAuditSource = joinedMainSources(Path.of("src/main/java/dev/persefonia/app/audit"));

        assertThat(appAuditSource).doesNotContain(FORBIDDEN_AUDIT_LITERALS.toArray(String[]::new));
        assertThat(appAuditSource).doesNotContain(FORBIDDEN_FIRST_CLASS_FIELDS.toArray(String[]::new));
    }

    @Test
    void auditSafeValuePolicyRejectsRequiredUnsafeCategories() {
        AuditSafeValuePolicy policy = new AuditSafeValuePolicy();

        for (String unsafe : requiredUnsafeCategories()) {
            assertThatThrownBy(() -> policy.auditValue(unsafe))
                    .isInstanceOf(AuditValidationException.class)
                    .hasMessageNotContaining(unsafe);
            assertThatThrownBy(() -> policy.metadataKey(unsafe))
                    .isInstanceOf(AuditValidationException.class)
                    .hasMessageNotContaining(unsafe);
        }
    }

    private static String joinedMainSources(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(AuditPrivacyArchitectureTest::read)
                    .reduce("", (left, right) -> left.concat(right));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan " + root, exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static List<String> requiredUnsafeCategories() {
        return List.of(
                "pass" + "word",
                "se" + "cret",
                "to" + "ken",
                "ses" + "sion",
                "coo" + "kie",
                "author" + "ization",
                "creden" + "tial",
                "SMTP credential",
                "SMTP secret",
                "Cloudflare credential",
                "Cloudflare secret",
                "raw IP",
                "hashed IP",
                "IP address",
                "user-" + "ag" + "ent",
                "user-" + "ag" + "ent fingerprint",
                "browser fingerprint",
                "rate-limit key",
                "Redis abuse key",
                "contact",
                "contact body",
                "contact message body",
                "message body",
                "sender",
                "sender email",
                "sender name",
                "email address",
                "body",
                "markdown",
                "markdown source",
                "rendered HTML",
                "HTML",
                "HTML body",
                "raw request URI",
                "request URI",
                "query string",
                "request header",
                "request headers",
                "request payload",
                "response payload",
                "principal payload",
                "OIDC claims",
                "private runtime config",
                "private host",
                "internal host",
                "private hostname",
                "internal hostname",
                "private IP",
                "internal IP",
                "full exception message",
                "stack trace");
    }
}
