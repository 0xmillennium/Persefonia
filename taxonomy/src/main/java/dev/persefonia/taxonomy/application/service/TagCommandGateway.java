package dev.persefonia.taxonomy.application.service;

import dev.persefonia.taxonomy.application.command.ArchiveTagCommand;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.TagCommandResult;
import dev.persefonia.taxonomy.application.command.UpdateTagCommand;

public interface TagCommandGateway {
    TagCommandResult create(CreateTagCommand command);
    TagCommandResult update(UpdateTagCommand command);
    TagCommandResult archive(ArchiveTagCommand command);
}
