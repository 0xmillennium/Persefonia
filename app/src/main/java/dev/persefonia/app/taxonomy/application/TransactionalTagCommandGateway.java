package dev.persefonia.app.taxonomy.application;

import dev.persefonia.taxonomy.application.command.ArchiveTagCommand;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.TagCommandResult;
import dev.persefonia.taxonomy.application.command.UpdateTagCommand;
import dev.persefonia.taxonomy.application.service.TagCommandGateway;
import dev.persefonia.taxonomy.application.service.TagCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalTagCommandGateway implements TagCommandGateway {
    private final TagCommandService service;

    public TransactionalTagCommandGateway(TagCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public TagCommandResult create(CreateTagCommand command) {
        return service.create(command);
    }

    @Override
    @Transactional
    public TagCommandResult update(UpdateTagCommand command) {
        return service.update(command);
    }

    @Override
    @Transactional
    public TagCommandResult archive(ArchiveTagCommand command) {
        return service.archive(command);
    }
}
