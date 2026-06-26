package dev.persefonia.app.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.communication.mail.ContactMailNotificationAttemptRecorder;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitKeyFactory;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitProperties;
import dev.persefonia.app.transaction.PostCommitTaskExecutor;
import dev.persefonia.communication.application.command.SubmitContactMessageCommandService;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.application.port.ContactMessageNotification;
import dev.persefonia.communication.application.port.MailNotificationPort;
import dev.persefonia.communication.application.port.MailNotificationResult;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.MailDeliveryStatus;
import dev.persefonia.platformoperations.application.port.RateLimitDecision;
import dev.persefonia.platformoperations.application.port.RateLimitPort;
import dev.persefonia.platformoperations.application.port.RateLimitRejectionReason;
import dev.persefonia.platformoperations.application.port.RateLimitRequest;
import dev.persefonia.webpublic.contact.PublicContactSubmissionRequest;
import dev.persefonia.webpublic.contact.PublicContactSubmissionResult;
import dev.persefonia.webpublic.insights.PublicInsightSurface;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubmitContactMessageGatewayTest {
    private static final PublicContactSubmissionRequest VALID_REQUEST = new PublicContactSubmissionRequest(
            "Ada",
            "ada@example.test",
            "Hello",
            "Body",
            "203.0.113.10");
    private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

    private final InMemoryContactMessages messages = new InMemoryContactMessages();
    private final StubRateLimitPort rateLimits = new StubRateLimitPort();
    private final RecordingPostCommitTaskExecutor postCommitTasks = new RecordingPostCommitTaskExecutor();
    private final StubMailNotificationPort mailNotifications = new StubMailNotificationPort();
    private final RecordingPublicInsightsObservationGateway insights = new RecordingPublicInsightsObservationGateway();
    private final ContactMailNotificationAttemptRecorder mailAttempts = new ContactMailNotificationAttemptRecorder(
            messages,
            Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
    private final PublicContactSubmissionService service = new PublicContactSubmissionService(
            rateLimits,
            new ContactRateLimitKeyFactory("secret-value"),
            new ContactRateLimitProperties("secret-value", 5, Duration.ofMinutes(15), "persefonia:rate-limit"),
            new SubmitContactMessageCommandService(messages),
            postCommitTasks,
            mailNotifications,
            mailAttempts,
            insights,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void validSubmissionChecksRateLimitBeforePersisting() {
        PublicContactSubmissionResult result = service.submit(VALID_REQUEST);

        assertThat(result.status()).isEqualTo(PublicContactSubmissionResult.Status.SUCCESS);
        assertThat(rateLimits.requests()).hasSize(1);
        assertThat(rateLimits.requests().getFirst().maxAttempts()).isEqualTo(5);
        assertThat(rateLimits.requests().getFirst().window()).isEqualTo(Duration.ofMinutes(15));
        assertThat(rateLimits.requests().getFirst().key().value()).doesNotContain("203.0.113.10");
        assertThat(messages.saved()).hasSize(1);
        assertThat(postCommitTasks.tasks()).hasSize(2);
        assertThat(mailNotifications.notifications()).isEmpty();
        assertThat(insights.contactSubmittedCalls()).isZero();
    }

    @Test
    void validSubmissionSendsMailOnlyWhenAfterCommitTaskRuns() {
        service.submit(VALID_REQUEST);
        ContactMessage message = messages.saved().getFirst();

        assertThat(mailNotifications.notifications()).isEmpty();
        assertThat(message.mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.NOT_ATTEMPTED);

        postCommitTasks.runAll();

        assertThat(mailNotifications.notifications()).hasSize(1);
        assertThat(messages.findById(message.id()).orElseThrow().mailDeliveryStatus()).isEqualTo(MailDeliveryStatus.SENT);
        assertThat(insights.contactSubmittedCalls()).isEqualTo(1);
    }

    @Test
    void invalidSubmissionAfterAllowedRateLimitPersistsNothing() {
        PublicContactSubmissionResult result = service.submit(new PublicContactSubmissionRequest(
                " ",
                "invalid",
                " ",
                " ",
                "203.0.113.10"));

        assertThat(result.status()).isEqualTo(PublicContactSubmissionResult.Status.VALIDATION_FAILED);
        assertThat(rateLimits.requests()).hasSize(1);
        assertThat(messages.saved()).isEmpty();
        assertThat(postCommitTasks.tasks()).isEmpty();
        assertThat(mailNotifications.notifications()).isEmpty();
    }

    @Test
    void rateLimitedSubmissionPersistsNothing() {
        rateLimits.nextDecision = RateLimitDecision.rejected(
                RateLimitRejectionReason.LIMIT_EXCEEDED,
                Duration.ofMinutes(15));

        PublicContactSubmissionResult result = service.submit(VALID_REQUEST);

        assertThat(result.status()).isEqualTo(PublicContactSubmissionResult.Status.RATE_LIMITED);
        assertThat(messages.saved()).isEmpty();
        assertThat(postCommitTasks.tasks()).isEmpty();
        assertThat(mailNotifications.notifications()).isEmpty();
    }

    @Test
    void unavailableRateLimitSubmissionPersistsNothing() {
        rateLimits.nextDecision = RateLimitDecision.rejected(
                RateLimitRejectionReason.TEMPORARILY_UNAVAILABLE,
                Duration.ZERO);

        PublicContactSubmissionResult result = service.submit(VALID_REQUEST);

        assertThat(result.status()).isEqualTo(PublicContactSubmissionResult.Status.TEMPORARILY_UNAVAILABLE);
        assertThat(messages.saved()).isEmpty();
        assertThat(postCommitTasks.tasks()).isEmpty();
        assertThat(mailNotifications.notifications()).isEmpty();
    }

    private static final class StubRateLimitPort implements RateLimitPort {
        private final List<RateLimitRequest> requests = new ArrayList<>();
        private RateLimitDecision nextDecision = RateLimitDecision.allowed(4);

        @Override
        public RateLimitDecision checkAndConsume(RateLimitRequest request) {
            requests.add(request);
            return nextDecision;
        }

        List<RateLimitRequest> requests() {
            return requests;
        }
    }

    private static final class RecordingPostCommitTaskExecutor implements PostCommitTaskExecutor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void afterCommit(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            tasks.forEach(task -> task.run());
        }

        List<Runnable> tasks() {
            return tasks;
        }
    }

    private static final class RecordingPublicInsightsObservationGateway implements PublicInsightsObservationGateway {
        private int contactSubmittedCalls;

        @Override
        public void recordPageView(PublicInsightSurface surface) {
        }

        @Override
        public void recordSearchSubmitted() {
        }

        @Override
        public void recordCvViewed() {
        }

        @Override
        public void recordCvDownloaded() {
        }

        @Override
        public void recordContactSubmitted() {
            contactSubmittedCalls++;
        }

        @Override
        public void recordNotFound() {
        }

        int contactSubmittedCalls() {
            return contactSubmittedCalls;
        }
    }

    private static final class StubMailNotificationPort implements MailNotificationPort {
        private final List<ContactMessageNotification> notifications = new ArrayList<>();

        @Override
        public MailNotificationResult notifyOwner(ContactMessageNotification notification) {
            notifications.add(notification);
            return MailNotificationResult.sent();
        }

        List<ContactMessageNotification> notifications() {
            return notifications;
        }
    }

    private static final class InMemoryContactMessages implements ContactMessageRepository {
        private final List<ContactMessage> saved = new ArrayList<>();

        @Override
        public void save(ContactMessage message) {
            saved.removeIf(existing -> existing.id().equals(message.id()));
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
