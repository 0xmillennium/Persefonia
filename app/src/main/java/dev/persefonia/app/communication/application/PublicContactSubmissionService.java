package dev.persefonia.app.communication.application;

import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitKeyFactory;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitProperties;
import dev.persefonia.communication.application.command.SubmitContactMessageCommand;
import dev.persefonia.communication.application.command.SubmitContactMessageCommandService;
import dev.persefonia.platformoperations.application.port.RateLimitPort;
import dev.persefonia.platformoperations.application.port.RateLimitRejectionReason;
import dev.persefonia.platformoperations.application.port.RateLimitRequest;
import dev.persefonia.platformoperations.application.port.RateLimitScope;
import dev.persefonia.webpublic.contact.PublicContactSubmissionGateway;
import dev.persefonia.webpublic.contact.PublicContactSubmissionRequest;
import dev.persefonia.webpublic.contact.PublicContactSubmissionResult;
import java.time.Clock;
import java.util.Objects;

public final class PublicContactSubmissionService implements PublicContactSubmissionGateway {
    private final RateLimitPort rateLimits;
    private final ContactRateLimitKeyFactory keyFactory;
    private final ContactRateLimitProperties properties;
    private final SubmitContactMessageCommandService commands;
    private final Clock clock;

    public PublicContactSubmissionService(
            RateLimitPort rateLimits,
            ContactRateLimitKeyFactory keyFactory,
            ContactRateLimitProperties properties,
            SubmitContactMessageCommandService commands,
            Clock clock) {
        this.rateLimits = Objects.requireNonNull(rateLimits, "rateLimits must not be null");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.commands = Objects.requireNonNull(commands, "commands must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public PublicContactSubmissionResult submit(PublicContactSubmissionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        var decision = rateLimits.checkAndConsume(new RateLimitRequest(
                RateLimitScope.CONTACT_FORM_SUBMISSION,
                keyFactory.derive(RateLimitScope.CONTACT_FORM_SUBMISSION, request.transientClientSignal()),
                properties.maxAttempts(),
                properties.window()));

        if (!decision.allowed()) {
            return decision.rejectionReason() == RateLimitRejectionReason.LIMIT_EXCEEDED
                    ? PublicContactSubmissionResult.rateLimited()
                    : PublicContactSubmissionResult.temporarilyUnavailable();
        }

        var result = commands.submit(new SubmitContactMessageCommand(
                request.senderName(),
                request.senderEmail(),
                request.subject(),
                request.body(),
                clock.instant()));

        if (!result.successful()) {
            return PublicContactSubmissionResult.invalid(result.fieldErrors());
        }
        return PublicContactSubmissionResult.success();
    }
}
