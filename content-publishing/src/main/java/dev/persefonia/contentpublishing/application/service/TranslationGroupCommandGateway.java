package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.command.AddTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.CreateTranslationGroupCommand;
import dev.persefonia.contentpublishing.application.command.RemoveTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.TranslationGroupResult;

public interface TranslationGroupCommandGateway {
    TranslationGroupResult create(CreateTranslationGroupCommand command);

    TranslationGroupResult addEntry(AddTranslationEntryCommand command);

    TranslationGroupResult removeEntry(RemoveTranslationEntryCommand command);
}
