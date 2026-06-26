package dev.persefonia.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.audit.application.command.AppendAuditChangeCommand;
import dev.persefonia.audit.application.command.AppendAuditMetadataCommand;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.audit.application.service.AuditSafeValuePolicy;
import dev.persefonia.audit.domain.record.AuditActorType;
import dev.persefonia.audit.domain.record.AuditValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditSafeValuePolicyTest {
    private final AuditSafeValuePolicy policy = new AuditSafeValuePolicy();

    @Test
    void safeBoundedAuditValueIsAccepted() {
        assertThat(policy.auditValue("Draft title").value()).isEqualTo("Draft title");
    }

    @Test
    void safeBoundedMetadataValueIsAccepted() {
        assertThat(policy.metadataValue("manual review").value()).isEqualTo("manual review");
    }

    @Test
    void overlongAuditValueIsRejected() {
        assertThatThrownBy(() -> policy.auditValue("a".repeat(501)))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void overlongMetadataValueIsRejected() {
        assertThatThrownBy(() -> policy.metadataValue("b".repeat(501)))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void blankRequiredValueIsRejected() {
        assertThatThrownBy(() -> policy.auditValue("   "))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void controlCharacterValueIsRejected() {
        assertThatThrownBy(() -> policy.auditValue("draft" + '\u0007' + "title"))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("control");
    }

    @Test
    void multilineValueIsRejected() {
        assertThatThrownBy(() -> policy.auditValue("line one\nline two"))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("multiline");
    }

    @Test
    void stackTraceLikeValueIsRejected() {
        String stackFrame = "at dev.persefonia.audit.Sample.run(Sample.java:42)";
        assertThatThrownBy(() -> policy.auditValue(stackFrame))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("stack-trace-like");
    }

    @Test
    void rawRequestTargetWithQueryStringIsRejected() {
        assertThatThrownBy(() -> policy.auditValue("/admin/content?status=draft"))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("unsafe request data");
    }

    @Test
    void unsafeFieldPathCategoryIsRejected() {
        String unsafe = "to" + "ken";
        assertThatThrownBy(() -> policy.fieldPath(unsafe))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("unsafe semantic class");
    }

    @Test
    void unsafeMetadataKeyCategoryIsRejected() {
        String unsafe = "coo" + "kie";
        assertThatThrownBy(() -> policy.metadataKey(unsafe))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("unsafe semantic class");
    }

    @Test
    void unsafeValueCategoryIsRejected() {
        String unsafe = "pass" + "word leaked";
        assertThatThrownBy(() -> policy.auditValue(unsafe))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("unsafe semantic class");
    }

    @Test
    void exceptionMessagesDoNotEchoRejectedRawValues() {
        String secretValue = "super" + "secret-" + "pass" + "word-1234567890";

        assertThatThrownBy(() -> policy.auditValue(secretValue))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageNotContaining(secretValue)
                .hasMessageNotContaining("1234567890");
    }

    @Test
    void requiredUnsafeCategoriesAreRejectedAcrossAuditSurfaces() {
        for (String unsafe : requiredUnsafeCategories()) {
            assertThatThrownBy(() -> policy.fieldPath(unsafe))
                    .as("field path category should be rejected")
                    .isInstanceOf(AuditValidationException.class);
            assertThatThrownBy(() -> policy.metadataKey(unsafe))
                    .as("metadata key category should be rejected")
                    .isInstanceOf(AuditValidationException.class);
            assertThatThrownBy(() -> policy.auditValue(unsafe))
                    .as("audit value category should be rejected")
                    .isInstanceOf(AuditValidationException.class);
            assertThatThrownBy(() -> policy.metadataValue(unsafe))
                    .as("metadata value category should be rejected")
                    .isInstanceOf(AuditValidationException.class);
        }
    }

    @Test
    void unsafeCategoryNamingFormsAreRejected() {
        List<String> unsafeForms = List.of(
                "contact" + "Bo" + "dy",
                "contact_" + "bo" + "dy",
                "contact-" + "bo" + "dy",
                "contact." + "bo" + "dy",
                "contact " + "bo" + "dy");

        for (String unsafe : unsafeForms) {
            assertThatThrownBy(() -> policy.fieldPath(unsafe))
                    .isInstanceOf(AuditValidationException.class)
                    .hasMessageContaining("unsafe semantic class");
        }
    }

    @Test
    void unsafePayloadShapesAreRejectedWithoutEchoingRawValues() {
        List<String> unsafeValues = List.of(
                "<p>published</p>",
                "<div>content</div>",
                "<script>alert(1)</script>",
                "# Heading",
                "**bold**",
                "[link](https://example.test)",
                "person@example.com",
                "/admin/content?status=draft",
                "https://example.test/path?x=y",
                "local" + "host",
                "127.0.0.1",
                "10.0.0.1",
                "172.16.0.1",
                "192.168.1.1",
                "service.internal",
                "service.local",
                "java.lang.RuntimeException: failure",
                "at dev.persefonia...");

        for (String unsafe : unsafeValues) {
            assertThatThrownBy(() -> policy.auditValue(unsafe))
                    .isInstanceOf(AuditValidationException.class)
                    .hasMessageNotContaining(unsafe);
        }
    }

    @Test
    void appendCommandValidationRejectsUnsafeChangesAndMetadata() {
        AppendAuditRecordCommand command = new AppendAuditRecordCommand(
                "content.published",
                AuditActorType.SYSTEM,
                null,
                null,
                null,
                "System",
                "publishing",
                "content_item",
                UUID.randomUUID(),
                null,
                Instant.parse("2026-06-25T10:00:00Z"),
                List.of(new AppendAuditChangeCommand("title", null, "person@example.com")),
                List.of(new AppendAuditMetadataCommand("reason", "scheduled release")));

        assertThatThrownBy(() -> policy.validate(command))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("unsafe identity data")
                .hasMessageNotContaining("person@example.com");
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
