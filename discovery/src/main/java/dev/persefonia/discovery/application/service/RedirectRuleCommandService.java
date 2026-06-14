package dev.persefonia.discovery.application.service;

import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.domain.RedirectRule;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import dev.persefonia.discovery.domain.SourceEntityRef;
import dev.persefonia.discovery.domain.Version;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class RedirectRuleCommandService implements CreateRedirectRulePort {
    private final RedirectRuleRepository repository;
    private final Clock clock;

    public RedirectRuleCommandService(RedirectRuleRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RedirectRuleCreationResult create(CreateRedirectRuleCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        SourceEntityRef sourceRef = sourceRef(command);
        Instant createdAt = Instant.now(clock);
        RedirectRule rule = RedirectRule.create(
                RedirectRuleId.random(),
                command.sourceUrl(),
                command.targetUrl(),
                command.statusCode(),
                command.reason(),
                sourceRef,
                true,
                createdAt,
                createdAt,
                Version.initial());

        var existing = repository.findActiveBySourceUrl(command.sourceUrl());
        if (existing.isPresent()) {
            return identical(existing.get(), rule)
                    ? new RedirectRuleCreationResult.Noop()
                    : new RedirectRuleCreationResult.Rejected(
                            RedirectRuleCreationResult.Reason.DUPLICATE_ACTIVE_SOURCE);
        }

        boolean directLoop = repository.findActiveBySourceUrl(command.targetUrl())
                .map(reverse -> reverse.targetUrl().equals(command.sourceUrl()))
                .orElse(false);
        if (directLoop) {
            return new RedirectRuleCreationResult.Rejected(RedirectRuleCreationResult.Reason.LOOP_DETECTED);
        }

        repository.save(rule);
        return new RedirectRuleCreationResult.Created();
    }

    private static SourceEntityRef sourceRef(CreateRedirectRuleCommand command) {
        return command.sourceContext() == null
                ? null
                : new SourceEntityRef(command.sourceContext(), command.sourceType(), command.sourceEntityId());
    }

    private static boolean identical(RedirectRule existing, RedirectRule candidate) {
        return existing.sourceUrl().equals(candidate.sourceUrl())
                && existing.targetUrl().equals(candidate.targetUrl())
                && existing.statusCode() == candidate.statusCode()
                && existing.reason() == candidate.reason()
                && existing.sourceRef().equals(candidate.sourceRef());
    }
}
