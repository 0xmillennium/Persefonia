package dev.persefonia.app.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.communication.mail.ContactMailNotificationAttemptRecorder;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitKeyFactory;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitProperties;
import dev.persefonia.app.transaction.SpringTransactionSynchronizationPostCommitTaskExecutor;
import dev.persefonia.communication.application.command.SubmitContactMessageCommandService;
import dev.persefonia.communication.application.port.ContactMessageNotification;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.application.port.MailNotificationPort;
import dev.persefonia.communication.application.port.MailNotificationResult;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.MailDeliveryStatus;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptResult;
import dev.persefonia.platformoperations.application.port.RateLimitDecision;
import dev.persefonia.webpublic.contact.PublicContactSubmissionRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ContactMailPostCommitTransactionTest {
    private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");
    private static final PublicContactSubmissionRequest REQUEST = new PublicContactSubmissionRequest(
            "Ada",
            "ada@example.test",
            "Hello",
            "Body",
            "203.0.113.10");

    private InMemoryContactMessages messages;
    private RecordingMailNotificationPort mailNotifications;
    private PublicContactSubmissionService service;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        messages = new InMemoryContactMessages();
        mailNotifications = new RecordingMailNotificationPort(MailNotificationResult.sent());
        service = new PublicContactSubmissionService(
                request -> RateLimitDecision.allowed(4),
                new ContactRateLimitKeyFactory("secret-value"),
                new ContactRateLimitProperties("secret-value", 5, Duration.ofMinutes(15), "persefonia:rate-limit"),
                new SubmitContactMessageCommandService(messages),
                new SpringTransactionSynchronizationPostCommitTaskExecutor(),
                mailNotifications,
                new ContactMailNotificationAttemptRecorder(messages, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        transactions = new TransactionTemplate(new InMemoryTransactionManager());
    }

    @Test
    void mailIsNotSentBeforeCommitAndSentAfterCommit() {
        transactions.executeWithoutResult(status -> {
            service.submit(REQUEST);

            assertThat(mailNotifications.notifications()).isEmpty();
            assertThat(messages.onlyMessage().mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.NOT_ATTEMPTED);
        });

        ContactMessage message = messages.onlyMessage();
        assertThat(mailNotifications.notifications()).hasSize(1);
        assertThat(message.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.SENT);
        assertThat(message.mailNotificationAttempts())
                .extracting(attempt -> attempt.result())
                .containsExactly(MailNotificationAttemptResult.SENT);
    }

    @Test
    void mailIsNotSentAfterRollback() {
        transactions.executeWithoutResult(status -> {
            service.submit(REQUEST);
            status.setRollbackOnly();
        });

        assertThat(mailNotifications.notifications()).isEmpty();
        assertThat(messages.onlyMessage().mailNotificationAttempts()).isEmpty();
        assertThat(messages.onlyMessage().mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.NOT_ATTEMPTED);
    }

    @Test
    void failedMailRecordsFailedAttemptAfterCommit() {
        mailNotifications.result = MailNotificationResult.failed("mail_transport_unavailable");

        transactions.executeWithoutResult(status -> service.submit(REQUEST));

        ContactMessage message = messages.onlyMessage();
        assertThat(message.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.FAILED);
        assertThat(message.mailNotificationAttempts())
                .extracting(attempt -> attempt.result())
                .containsExactly(MailNotificationAttemptResult.FAILED);
    }

    private static final class RecordingMailNotificationPort implements MailNotificationPort {
        private final List<ContactMessageNotification> notifications = new ArrayList<>();
        private MailNotificationResult result;

        private RecordingMailNotificationPort(MailNotificationResult result) {
            this.result = result;
        }

        @Override
        public MailNotificationResult notifyOwner(ContactMessageNotification notification) {
            notifications.add(notification);
            return result;
        }

        List<ContactMessageNotification> notifications() {
            return notifications;
        }
    }

    private static final class InMemoryContactMessages implements ContactMessageRepository {
        private final Map<ContactMessageId, ContactMessage> saved = new LinkedHashMap<>();

        @Override
        public void save(ContactMessage message) {
            saved.put(message.id(), message);
        }

        @Override
        public Optional<ContactMessage> findById(ContactMessageId id) {
            return Optional.ofNullable(saved.get(id));
        }

        ContactMessage onlyMessage() {
            assertThat(saved).hasSize(1);
            return saved.values().iterator().next();
        }
    }

    private static final class InMemoryTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
