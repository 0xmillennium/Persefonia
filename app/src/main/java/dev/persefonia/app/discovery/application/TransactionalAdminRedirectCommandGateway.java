package dev.persefonia.app.discovery.application;

import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.application.service.AdminRedirectCommandGateway;
import dev.persefonia.discovery.application.service.AdminRedirectCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalAdminRedirectCommandGateway implements AdminRedirectCommandGateway {
    private final AdminRedirectCommandService service;

    public TransactionalAdminRedirectCommandGateway(AdminRedirectCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public RedirectRuleCreationResult create(CreateManualRedirectCommand command) {
        return service.create(command);
    }

    @Override
    @Transactional
    public DeactivateRedirectRuleResult deactivate(DeactivateManualRedirectCommand command) {
        return service.deactivate(command);
    }
}
