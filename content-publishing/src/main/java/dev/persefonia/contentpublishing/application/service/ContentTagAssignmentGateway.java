package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;

public interface ContentTagAssignmentGateway {
    void assign(AssignContentTagsCommand command);
}
