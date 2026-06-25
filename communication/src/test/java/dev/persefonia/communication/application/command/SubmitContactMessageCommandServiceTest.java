package dev.persefonia.communication.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.MailDeliveryStatus;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubmitContactMessageCommandServiceTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-06-25T10:00:00Z");

    private final InMemoryContactMessages messages = new InMemoryContactMessages();
    private final SubmitContactMessageCommandService service = new SubmitContactMessageCommandService(messages);

    @Test
    void validCommandCreatesNewContactMessage() {
        SubmitContactMessageResult result = service.submit(validCommand());

        assertThat(result.successful()).isTrue();
        assertThat(messages.saved()).hasSize(1);
        ContactMessage saved = messages.saved().getFirst();
        assertThat(saved.id()).isEqualTo(result.messageId());
        assertThat(saved.senderName().value()).isEqualTo("Ada Lovelace");
        assertThat(saved.senderEmail().value()).isEqualTo("ada@example.test");
        assertThat(saved.subject().value()).isEqualTo("Hello");
        assertThat(saved.body().value()).isEqualTo("First line\nSecond line");
        assertThat(saved.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(saved.status()).isEqualTo(ContactMessageStatus.NEW);
        assertThat(saved.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.NOT_ATTEMPTED);
        assertThat(saved.mailNotificationAttempts()).isEmpty();
        assertThat(saved.statusChanges()).isEmpty();
    }

    @Test
    void invalidCommandReturnsSafeFieldErrorsAndPersistsNothing() {
        SubmitContactMessageResult result = service.submit(new SubmitContactMessageCommand(
                " ",
                "not-an-email",
                " ",
                "private body\u0000must not be echoed",
                SUBMITTED_AT));

        assertThat(result.successful()).isFalse();
        assertThat(result.fieldErrors()).containsKeys("senderName", "senderEmail", "subject", "body");
        assertThat(result.fieldErrors().get("body"))
                .doesNotContain("private body")
                .doesNotContain("must not be echoed");
        assertThat(messages.saved()).isEmpty();
    }

    @Test
    void commandTypeDoesNotAcceptRequestMetadata() {
        assertThat(SubmitContactMessageCommand.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("senderName", "senderEmail", "subject", "body", "submittedAt")
                .doesNotContain(
                        "rawIp",
                        "hashedIp",
                        "ipAddress",
                        "userAgent",
                        "userAgentSummary",
                        "userAgentHash",
                        "rateLimitKey",
                        "sessionId",
                        "visitorId",
                        "trackingCookieId",
                        "countryCode",
                        "request");
    }

    private static SubmitContactMessageCommand validCommand() {
        return new SubmitContactMessageCommand(
                " Ada Lovelace ",
                " ADA@EXAMPLE.TEST ",
                " Hello ",
                " First line\nSecond line ",
                SUBMITTED_AT);
    }

    private static final class InMemoryContactMessages implements ContactMessageRepository {
        private final List<ContactMessage> saved = new ArrayList<>();

        @Override
        public void save(ContactMessage message) {
            saved.add(message);
        }

        @Override
        public Optional<ContactMessage> findById(ContactMessageId id) {
            return saved.stream().filter(message -> message.id().equals(id)).findFirst();
        }

        List<ContactMessage> saved() {
            return saved;
        }
    }
}
