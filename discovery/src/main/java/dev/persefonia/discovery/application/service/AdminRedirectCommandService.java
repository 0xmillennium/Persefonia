package dev.persefonia.discovery.application.service;

import dev.persefonia.discovery.application.authorization.AdminRedirectCommandAuthorizationPolicy;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.DeactivateRedirectRulePort;
import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import java.util.Objects;

public final class AdminRedirectCommandService {
    private static final String CREATE_COMMAND = "discovery.redirect.create";
    private static final String DEACTIVATE_COMMAND = "discovery.redirect.deactivate";

    private final CreateRedirectRulePort creates;
    private final DeactivateRedirectRulePort deactivates;
    private final AdminRedirectCommandAuthorizationPolicy authorization;

    public AdminRedirectCommandService(
            CreateRedirectRulePort creates,
            DeactivateRedirectRulePort deactivates,
            AdminRedirectCommandAuthorizationPolicy authorization) {
        this.creates = Objects.requireNonNull(creates, "creates");
        this.deactivates = Objects.requireNonNull(deactivates, "deactivates");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public RedirectRuleCreationResult create(CreateManualRedirectCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), CREATE_COMMAND);
        return creates.create(new CreateRedirectRuleCommand(
                command.sourceUrl(),
                command.targetUrl(),
                command.statusCode(),
                RedirectReason.MANUAL,
                null,
                null,
                null));
    }

    public DeactivateRedirectRuleResult deactivate(DeactivateManualRedirectCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), DEACTIVATE_COMMAND);
        return deactivates.deactivate(new DeactivateRedirectRuleCommand(command.redirectRuleId()));
    }
}
