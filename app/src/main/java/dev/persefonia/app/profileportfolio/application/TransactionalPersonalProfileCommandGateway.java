package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.profileportfolio.application.command.PersonalProfileUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpsertActivePersonalProfileCommand;
import dev.persefonia.profileportfolio.application.service.PersonalProfileCommandGateway;
import dev.persefonia.profileportfolio.application.service.PersonalProfileCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalPersonalProfileCommandGateway implements PersonalProfileCommandGateway {
    private final PersonalProfileCommandService service;

    public TransactionalPersonalProfileCommandGateway(PersonalProfileCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public PersonalProfileUpdateResult upsertActive(UpsertActivePersonalProfileCommand command) {
        return service.upsertActive(command);
    }
}
