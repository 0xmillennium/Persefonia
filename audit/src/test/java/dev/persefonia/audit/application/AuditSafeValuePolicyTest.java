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
    private static final Instant OCCURRED_AT = Instant.parse("2026-06-25T10:00:00Z");
    private final AuditSafeValuePolicy policy = new AuditSafeValuePolicy();

    @Test
    void safeGenericValuesAreAcceptedAcrossPersistedValueTypes() {
        List<String> safeValues = List.of(
                "PUBLISHED", "PRIVATE", "UNLISTED", "ARCHIVED", "true", "false", "3",
                "communication", "contact_message", "CLOUDFLARE", "body", "markdown", "HTML", "token",
                "manual review", "/writing/old-slug", "550e8400-e29b-41d4-a716-446655440000");

        for (String value : safeValues) {
            assertThat(policy.auditValue(value).value()).isEqualTo(value);
            assertThat(policy.metadataValue(value).value()).isEqualTo(value);
        }
    }

    @Test
    void unsafePersistedValueShapesAreRejectedWithoutRawValueEcho() {
        List<String> unsafeValues = List.of(
                "a".repeat(501), "   ", "draft\u0007title", "line one\nline two",
                "person@example.com", "127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1",
                "203.0.113.42", "8.8.8.8", "2001:db8::1", "::1",
                "localhost", "db.internal", "service.local", "<p>hello</p>",
                "<div>private content</div>", "<script>alert(1)</script>", "# Heading",
                "**authored emphasis**", "[private](relative-target)", "https://example.com/foo",
                "/foo?token=x", "RuntimeException: failure", "java.lang.RuntimeException: failure",
                "at dev.persefonia...", "at dev.persefonia.audit.Sample.run(Sample.java:42)", "Foo.java:42",
                "password=abc", "password: hunter2", "secret=abc", "token=abc123", "access_token=abc",
                "session_id=abc", "api_key=abc", "client_secret=abc", "Bearer abc.def.ghi",
                "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==", "Authorization: Bearer abc.def.ghi", "Cookie: sid=abc",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
                "-----BEGIN PRIVATE KEY-----",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        for (String value : unsafeValues) {
            assertThatThrownBy(() -> policy.auditValue(value))
                    .as("unsafe audit value shape")
                    .isInstanceOf(AuditValidationException.class)
                    .hasMessageNotContaining(value);
            assertThatThrownBy(() -> policy.metadataValue(value))
                    .as("unsafe metadata value shape")
                    .isInstanceOf(AuditValidationException.class)
                    .hasMessageNotContaining(value);
        }
    }

    @Test
    void completeContactStatusCommandIsAccepted() {
        policy.validate(AuditCommands.contactStatusChangedCommand());
    }

    @Test
    void completeCommandValidationChecksActionEntityActorAndRequestId() {
        assertThatThrownBy(() -> policy.validate(command(
                        "Contact.Changed", "communication", "contact_message", "iam", "admin_account",
                        "Jane Admin", "req-123", List.of(), List.of())))
                .isInstanceOf(AuditValidationException.class);
        assertThatThrownBy(() -> policy.validate(command(
                        "contact_message.status.changed", "Communication", "contact_message", "iam", "admin_account",
                        "Jane Admin", "req-123", List.of(), List.of())))
                .isInstanceOf(AuditValidationException.class);
        assertThatThrownBy(() -> policy.validate(command(
                        "contact_message.status.changed", "communication", "contact_message", "IAM", "admin_account",
                        "Jane Admin", "req-123", List.of(), List.of())))
                .isInstanceOf(AuditValidationException.class);
        assertThatThrownBy(() -> policy.validate(command(
                        "contact_message.status.changed", "communication", "contact_message", "iam", "admin_account",
                        "Jane\nAdmin", "req-123", List.of(), List.of())))
                .isInstanceOf(AuditValidationException.class);
        assertThatThrownBy(() -> policy.validate(command(
                        "contact_message.status.changed", "communication", "contact_message", "iam", "admin_account",
                        "Jane Admin", "request/id", List.of(), List.of())))
                .isInstanceOf(AuditValidationException.class);
    }

    @Test
    void completeCommandValidationChecksEntityIdentityAndActorShape() {
        AppendAuditRecordCommand missingEntityId = new AppendAuditRecordCommand(
                "contact_message.status.changed", AuditActorType.SYSTEM, null, null, null, "System",
                "communication", "contact_message", null, null, OCCURRED_AT, List.of(), List.of());
        assertThatThrownBy(() -> policy.validate(missingEntityId))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("entity requires an id");

        AppendAuditRecordCommand incompleteAdmin = new AppendAuditRecordCommand(
                "contact_message.status.changed", AuditActorType.ADMIN, "iam", "admin_account", null, "Jane Admin",
                "communication", "contact_message", UUID.randomUUID(), null, OCCURRED_AT, List.of(), List.of());
        assertThatThrownBy(() -> policy.validate(incompleteAdmin))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("admin actor requires");

        AppendAuditRecordCommand referencedSystem = new AppendAuditRecordCommand(
                "contact_message.status.changed", AuditActorType.SYSTEM, "iam", "admin_account", UUID.randomUUID(),
                "System", "communication", "contact_message", UUID.randomUUID(), null, OCCURRED_AT,
                List.of(), List.of());
        assertThatThrownBy(() -> policy.validate(referencedSystem))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("system actor must not carry");
    }

    @Test
    void completeCommandValidationRejectsSensitiveKeyUnsafeIdentityAndRawIp() {
        assertThatThrownBy(() -> policy.validate(command(
                        "contact_message.status.changed", "communication", "contact_message", "iam", "admin_account",
                        "Jane Admin", null,
                        List.of(new AppendAuditChangeCommand("sender_email", null, "changed")), List.of())))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("sensitive audit key");
        assertThatThrownBy(() -> policy.validate(command(
                        "contact_message.status.changed", "communication", "contact_message", "iam", "admin_account",
                        "Jane Admin", null,
                        List.of(new AppendAuditChangeCommand("status", "NEW", "person@example.com")), List.of())))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageNotContaining("person@example.com");
        assertThatThrownBy(() -> policy.validate(command(
                        "contact_message.status.changed", "communication", "contact_message", "iam", "admin_account",
                        "Jane Admin", null,
                        List.of(new AppendAuditChangeCommand("status", "NEW", "203.0.113.42")), List.of())))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageNotContaining("203.0.113.42");
    }

    @Test
    void genericPolicyStillExposesTypedChildValueConstruction() {
        assertThat(policy.fieldPath("contact_message.status").value()).isEqualTo("contact_message.status");
        assertThat(policy.metadataKey("source.channel").value()).isEqualTo("source.channel");
    }

    private static AppendAuditRecordCommand command(
            String action, String entityContext, String entityType, String actorContext,
            String actorSourceType, String actorDisplay, String requestId,
            List<AppendAuditChangeCommand> changes, List<AppendAuditMetadataCommand> metadata) {
        return new AppendAuditRecordCommand(
                action, AuditActorType.ADMIN, actorContext, actorSourceType, UUID.randomUUID(), actorDisplay,
                entityContext, entityType, UUID.randomUUID(), requestId, OCCURRED_AT, changes, metadata);
    }
}
