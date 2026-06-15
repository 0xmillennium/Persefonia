package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.contentpublishing.application.command.AddTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.CreateTranslationGroupCommand;
import dev.persefonia.contentpublishing.application.command.RemoveTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.TranslationGroupResult;
import dev.persefonia.contentpublishing.application.service.TranslationGroupCommandGateway;
import dev.persefonia.contentpublishing.application.service.TranslationGroupCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalTranslationGroupCommandGateway implements TranslationGroupCommandGateway {
    private final TranslationGroupCommandService service;

    public TransactionalTranslationGroupCommandGateway(TranslationGroupCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public TranslationGroupResult create(CreateTranslationGroupCommand command) {
        return service.create(command);
    }

    @Override
    @Transactional
    public TranslationGroupResult addEntry(AddTranslationEntryCommand command) {
        return service.addEntry(command);
    }

    @Override
    @Transactional
    public TranslationGroupResult removeEntry(RemoveTranslationEntryCommand command) {
        return service.removeEntry(command);
    }
}
