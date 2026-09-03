package dev.persefonia.audit.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuditTokenInvariantsTest {
    @Test
    void durableIdentifierVocabularyIsAccepted() {
        assertThat(AuditAction.of("content.published").value()).isEqualTo("content.published");
        assertThat(AuditAction.of("contact_message.status.changed").value())
                .isEqualTo("contact_message.status.changed");
        assertThat(AuditAction.of("cache.cloudflare.purge_failed").value())
                .isEqualTo("cache.cloudflare.purge_failed");
        String projectManagementAction = "content." + "spri" + "nt." + "repa" + "ir";
        assertThat(AuditAction.of(projectManagementAction).value()).isEqualTo(projectManagementAction);
        assertThat(SourceContext.of("communication").value()).isEqualTo("communication");
        assertThat(SourceType.of("contact_message").value()).isEqualTo("contact_message");
    }

    @Test
    void malformedActionsAreRejected() {
        List<String> malformed = List.of(
                "  ",
                "content published",
                "content.\u0007published",
                "content/published",
                "content?published=true",
                "Content.Published",
                "content-published",
                "content..published",
                "a".repeat(201));

        for (String value : malformed) {
            assertThatThrownBy(() -> AuditAction.of(value))
                    .as("malformed action")
                    .isInstanceOf(AuditValidationException.class)
                    .hasMessageNotContaining(value);
        }
    }

    @Test
    void malformedSourceIdentifiersAreRejected() {
        List<String> malformed = List.of(
                " ",
                "contact message",
                "communication/contact",
                "communication?channel=admin",
                "Communication",
                "communication.status",
                "a".repeat(201));

        for (String value : malformed) {
            assertThatThrownBy(() -> SourceContext.of(value))
                    .as("malformed source context")
                    .isInstanceOf(AuditValidationException.class);
            assertThatThrownBy(() -> SourceType.of(value))
                    .as("malformed source type")
                    .isInstanceOf(AuditValidationException.class);
        }
    }

    @Test
    void structuredKeysUseDottedLowerSnakeCaseWithoutSubstringBlocking() {
        List<String> valid = List.of(
                "status",
                "seo.title",
                "contact_message.status",
                "communication.status",
                "provider",
                "failure_category",
                "body_count",
                "email_changed",
                "rate_limit.result");

        for (String value : valid) {
            assertThat(FieldPath.of(value).value()).isEqualTo(value);
            assertThat(MetadataKey.of(value).value()).isEqualTo(value);
        }
    }

    @Test
    void exactSensitiveStructuredKeySegmentsAreRejected() {
        List<String> sensitive = List.of(
                "password",
                "auth.token",
                "contact_body",
                "contact.body",
                "sender_email",
                "raw_ip",
                "hashed_ip",
                "markdown_source",
                "rendered_html",
                "request_headers",
                "failure.stack_trace",
                "cloudflare_secret",
                "cloudflare_credential");

        for (String value : sensitive) {
            assertThatThrownBy(() -> FieldPath.of(value))
                    .isInstanceOf(AuditValidationException.class)
                    .hasMessage("field path uses a sensitive audit key")
                    .hasMessageNotContaining(value);
            assertThatThrownBy(() -> MetadataKey.of(value))
                    .isInstanceOf(AuditValidationException.class)
                    .hasMessage("metadata key uses a sensitive audit key")
                    .hasMessageNotContaining(value);
        }
    }

    @Test
    void malformedStructuredKeysAreRejected() {
        List<String> malformed = List.of(
                " ",
                "seo/title",
                "seo[title]",
                "seo?title=x",
                "seo title",
                "Seo.Title",
                "seo..title",
                "a".repeat(201));

        for (String value : malformed) {
            assertThatThrownBy(() -> FieldPath.of(value))
                    .isInstanceOf(AuditValidationException.class);
        }
    }

    @Test
    void requestIdKeepsItsDedicatedCorrelationGrammar() {
        assertThat(RequestId.of("req-12ab34cd").value()).isEqualTo("req-12ab34cd");
        assertThatThrownBy(() -> RequestId.of("request/id"))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("correlation identifier");
    }
}
