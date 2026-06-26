package dev.persefonia.audit.application.service;

import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.port.AuditRecordRepository;
import java.util.Objects;

/**
 * Validates an append command through the factory and policy, then appends the
 * resulting record to the repository. Validation and repository failures
 * propagate unchanged: this service never swallows exceptions, starts a
 * transaction, schedules post-commit work, retries, or publishes events.
 */
public final class AuditAppendService implements AppendAuditRecordPort {
    private final AuditRecordFactory factory;
    private final AuditRecordRepository repository;

    public AuditAppendService(AuditRecordFactory factory, AuditRecordRepository repository) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void append(AppendAuditRecordCommand command) {
        Objects.requireNonNull(command, "command");
        AuditRecord record = factory.create(command);
        repository.append(record);
    }
}
