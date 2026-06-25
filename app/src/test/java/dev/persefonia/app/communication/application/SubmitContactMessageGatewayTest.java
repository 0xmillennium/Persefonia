package dev.persefonia.app.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitKeyFactory;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitProperties;
import dev.persefonia.communication.application.command.SubmitContactMessageCommandService;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.platformoperations.application.port.RateLimitDecision;
import dev.persefonia.platformoperations.application.port.RateLimitPort;
import dev.persefonia.platformoperations.application.port.RateLimitRejectionReason;
import dev.persefonia.platformoperations.application.port.RateLimitRequest;
import dev.persefonia.webpublic.contact.PublicContactSubmissionRequest;
import dev.persefonia.webpublic.contact.PublicContactSubmissionResult;
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

    private final InMemoryContactMessages messages = new InMemoryContactMessages();
    private final StubRateLimitPort rateLimits = new StubRateLimitPort();
    private final PublicContactSubmissionService service = new PublicContactSubmissionService(
            rateLimits,
            new ContactRateLimitKeyFactory("secret-value"),
            new ContactRateLimitProperties("secret-value", 5, Duration.ofMinutes(15), "persefonia:rate-limit"),
            new SubmitContactMessageCommandService(messages),
            Clock.fixed(Instant.parse("2026-06-25T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void validSubmissionChecksRateLimitBeforePersisting() {
        PublicContactSubmissionResult result = service.submit(VALID_REQUEST);

        assertThat(result.status()).isEqualTo(PublicContactSubmissionResult.Status.SUCCESS);
        assertThat(rateLimits.requests()).hasSize(1);
        assertThat(rateLimits.requests().getFirst().maxAttempts()).isEqualTo(5);
        assertThat(rateLimits.requests().getFirst().window()).isEqualTo(Duration.ofMinutes(15));
        assertThat(rateLimits.requests().getFirst().key().value()).doesNotContain("203.0.113.10");
        assertThat(messages.saved()).hasSize(1);
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
    }

    @Test
    void rateLimitedSubmissionPersistsNothing() {
        rateLimits.nextDecision = RateLimitDecision.rejected(
                RateLimitRejectionReason.LIMIT_EXCEEDED,
                Duration.ofMinutes(15));

        PublicContactSubmissionResult result = service.submit(VALID_REQUEST);

        assertThat(result.status()).isEqualTo(PublicContactSubmissionResult.Status.RATE_LIMITED);
        assertThat(messages.saved()).isEmpty();
    }

    @Test
    void unavailableRateLimitSubmissionPersistsNothing() {
        rateLimits.nextDecision = RateLimitDecision.rejected(
                RateLimitRejectionReason.TEMPORARILY_UNAVAILABLE,
                Duration.ZERO);

        PublicContactSubmissionResult result = service.submit(VALID_REQUEST);

        assertThat(result.status()).isEqualTo(PublicContactSubmissionResult.Status.TEMPORARILY_UNAVAILABLE);
        assertThat(messages.saved()).isEmpty();
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
