package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentGateway;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalContentTagAssignmentGateway implements ContentTagAssignmentGateway {
    private final ContentTagAssignmentService service;

    public TransactionalContentTagAssignmentGateway(ContentTagAssignmentService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public void assign(AssignContentTagsCommand command) {
        service.assign(command);
    }
}
