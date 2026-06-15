package dev.persefonia.discovery.application.service;

import dev.persefonia.discovery.application.port.DeactivateRedirectRulePort;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class RedirectRuleLifecycleService implements DeactivateRedirectRulePort {
    private final RedirectRuleRepository repository;
    private final Clock clock;

    public RedirectRuleLifecycleService(RedirectRuleRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DeactivateRedirectRuleResult deactivate(DeactivateRedirectRuleCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }

        var existing = repository.findById(command.redirectRuleId());
        if (existing.isEmpty()) {
            return new DeactivateRedirectRuleResult.NotFound();
        }
        if (!existing.get().active()) {
            return new DeactivateRedirectRuleResult.AlreadyInactive();
        }

        repository.deactivate(command.redirectRuleId(), Instant.now(clock));
        return new DeactivateRedirectRuleResult.Deactivated();
    }
}
