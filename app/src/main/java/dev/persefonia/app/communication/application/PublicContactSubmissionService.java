package dev.persefonia.app.communication.application;

import dev.persefonia.app.communication.mail.ContactMailNotificationAttemptRecorder;
import dev.persefonia.app.communication.mail.ContactOwnerMailNotificationTask;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitKeyFactory;
import dev.persefonia.app.platformoperations.ratelimit.ContactRateLimitProperties;
import dev.persefonia.app.transaction.PostCommitTaskExecutor;
import dev.persefonia.communication.application.command.SubmitContactMessageCommand;
import dev.persefonia.communication.application.command.SubmitContactMessageCommandService;
import dev.persefonia.communication.application.port.MailNotificationPort;
import dev.persefonia.platformoperations.application.port.RateLimitPort;
import dev.persefonia.platformoperations.application.port.RateLimitRejectionReason;
import dev.persefonia.platformoperations.application.port.RateLimitRequest;
import dev.persefonia.platformoperations.application.port.RateLimitScope;
import dev.persefonia.webpublic.contact.PublicContactSubmissionGateway;
import dev.persefonia.webpublic.contact.PublicContactSubmissionRequest;
import dev.persefonia.webpublic.contact.PublicContactSubmissionResult;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class PublicContactSubmissionService implements PublicContactSubmissionGateway {
    private final RateLimitPort rateLimits;
    private final ContactRateLimitKeyFactory keyFactory;
    private final ContactRateLimitProperties properties;
    private final SubmitContactMessageCommandService commands;
    private final PostCommitTaskExecutor postCommitTasks;
    private final MailNotificationPort mailNotifications;
    private final ContactMailNotificationAttemptRecorder mailAttempts;
    private final Clock clock;

    public PublicContactSubmissionService(
            RateLimitPort rateLimits,
            ContactRateLimitKeyFactory keyFactory,
            ContactRateLimitProperties properties,
            SubmitContactMessageCommandService commands,
            PostCommitTaskExecutor postCommitTasks,
            MailNotificationPort mailNotifications,
            ContactMailNotificationAttemptRecorder mailAttempts,
            Clock clock) {
        this.rateLimits = Objects.requireNonNull(rateLimits, "rateLimits must not be null");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.commands = Objects.requireNonNull(commands, "commands must not be null");
        this.postCommitTasks = Objects.requireNonNull(postCommitTasks, "postCommitTasks must not be null");
        this.mailNotifications = Objects.requireNonNull(mailNotifications, "mailNotifications must not be null");
        this.mailAttempts = Objects.requireNonNull(mailAttempts, "mailAttempts must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
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
        postCommitTasks.afterCommit(new ContactOwnerMailNotificationTask(
                result.notification(),
                mailNotifications,
                mailAttempts));
        return PublicContactSubmissionResult.success();
    }
}
