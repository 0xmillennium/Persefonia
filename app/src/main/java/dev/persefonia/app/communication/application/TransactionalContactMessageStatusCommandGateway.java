package dev.persefonia.app.communication.application;

import dev.persefonia.communication.application.command.ContactMessageStatusCommandGateway;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommand;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommandService;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusResult;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalContactMessageStatusCommandGateway implements ContactMessageStatusCommandGateway {
    private final UpdateContactMessageStatusCommandService service;

    public TransactionalContactMessageStatusCommandGateway(UpdateContactMessageStatusCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public UpdateContactMessageStatusResult update(UpdateContactMessageStatusCommand command) {
        return service.update(command);
    }
}
