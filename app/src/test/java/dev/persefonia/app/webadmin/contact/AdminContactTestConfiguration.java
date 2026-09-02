package dev.persefonia.app.webadmin.contact;

import dev.persefonia.communication.application.command.ContactMessageStatusCommandGateway;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommandService;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.application.query.ContactMessageAdminDetail;
import dev.persefonia.communication.application.query.ContactMessageAdminListItem;
import dev.persefonia.communication.application.query.ContactMessageAdminListPage;
import dev.persefonia.communication.application.query.ContactMessageAdminListRequest;
import dev.persefonia.communication.application.query.ContactMessageAdminMailAttemptItem;
import dev.persefonia.communication.application.query.ContactMessageAdminQueryService;
import dev.persefonia.communication.application.query.ContactMessageAdminStatusChangeItem;
import dev.persefonia.communication.domain.contact.ContactBody;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactSubject;
import dev.persefonia.communication.domain.contact.MailNotificationAttempt;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptId;
import dev.persefonia.communication.domain.contact.SafeFailureReason;
import dev.persefonia.communication.domain.contact.SenderEmail;
import dev.persefonia.communication.domain.contact.SenderName;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class AdminContactTestConfiguration {
    static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");
    static final ContactMessageId MESSAGE_ID =
            ContactMessageId.from(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    static final ContactMessageId SECOND_MESSAGE_ID =
            ContactMessageId.from(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    static final ContactMessageId MISSING_ID =
            ContactMessageId.from(UUID.fromString("99999999-9999-9999-9999-999999999999"));

    @Bean
    @Primary
    ContactMessageStore contactMessageStore() {
        return new ContactMessageStore();
    }

    @Bean
    @Primary
    Clock adminContactClock() {
        return Clock.fixed(NOW.plusSeconds(300), ZoneOffset.UTC);
    }

    @Bean
    @Primary
    ContactMessageStatusCommandGateway adminContactStatusCommandGateway(
            UpdateContactMessageStatusCommandService service) {
        return service::update;
    }

    static ContactMessage message() {
        return ContactMessage.create(
                MESSAGE_ID,
                SenderName.of("Ada Lovelace"),
                SenderEmail.of("ada@example.test"),
                ContactSubject.of("Hello from the contact form"),
                ContactBody.of("Private <b>body</b>\nSecond line"),
                NOW);
    }

    static ContactMessage secondMessage() {
        return ContactMessage.create(
                SECOND_MESSAGE_ID,
                SenderName.of("Grace Hopper"),
                SenderEmail.of("grace@example.test"),
                ContactSubject.of("Another message"),
                ContactBody.of("Another private body"),
                NOW.minusSeconds(60));
    }

    static final class ContactMessageStore implements ContactMessageRepository, ContactMessageAdminQueryService {
        private final Map<ContactMessageId, ContactMessage> messages = new LinkedHashMap<>();

        void reset() {
            messages.clear();
        }

        @Override
        public void save(ContactMessage message) {
            messages.put(message.id(), message);
        }

        @Override
        public Optional<ContactMessage> findById(ContactMessageId id) {
            return Optional.ofNullable(messages.get(id));
        }

        @Override
        public ContactMessageAdminListPage list(ContactMessageAdminListRequest request) {
            List<ContactMessageAdminListItem> all = messages.values().stream()
                    .filter(message -> request.statusFilterOptional()
                            .map(status -> message.status() == status)
                            .orElse(true))
                    .sorted(Comparator.comparing((ContactMessage message) -> message.submittedAt()).reversed())
                    .map(AdminContactTestConfiguration::listItem)
                    .toList();
            List<ContactMessageAdminListItem> page = all.stream()
                    .skip(request.offset())
                    .limit(request.pageSize())
                    .toList();
            return new ContactMessageAdminListPage(page, request.page(), request.pageSize(), all.size());
        }

        @Override
        public Optional<ContactMessageAdminDetail> findDetail(ContactMessageId id) {
            return findById(id).map(AdminContactTestConfiguration::detail);
        }
    }

    private static ContactMessageAdminListItem listItem(ContactMessage message) {
        MailNotificationAttempt latest = message.mailNotificationAttempts().isEmpty()
                ? null
                : message.mailNotificationAttempts().getLast();
        return new ContactMessageAdminListItem(
                message.id(),
                message.senderName().value(),
                message.senderEmail().value(),
                message.subject().value(),
                message.status(),
                message.mailDeliveryStatus(),
                message.submittedAt(),
                message.updatedAt(),
                message.mailNotificationAttempts().size(),
                latest == null ? null : latest.result());
    }

    private static ContactMessageAdminDetail detail(ContactMessage message) {
        return new ContactMessageAdminDetail(
                message.id(),
                message.senderName().value(),
                message.senderEmail().value(),
                message.subject().value(),
                message.body().value(),
                message.status(),
                message.mailDeliveryStatus(),
                message.submittedAt(),
                message.updatedAt(),
                message.mailNotificationAttempts().stream()
                        .map(attempt -> new ContactMessageAdminMailAttemptItem(
                                attempt.id(),
                                attempt.result(),
                                attempt.failureReasonOptional().map(reason -> reason.value()).orElse(null),
                                attempt.attemptedAt()))
                        .toList(),
                message.statusChanges().stream()
                        .map(change -> new ContactMessageAdminStatusChangeItem(
                                change.id(),
                                change.previousStatus(),
                                change.newStatus(),
                                change.changedBy(),
                                change.changedAt()))
                        .toList());
    }

    static ContactMessage withMailAttempt(ContactMessage message) {
        message.recordMailFailed(
                MailNotificationAttemptId.from(UUID.fromString("33333333-3333-3333-3333-333333333333")),
                SafeFailureReason.of("SMTP unavailable"),
                NOW.plusSeconds(30));
        return message;
    }
}
