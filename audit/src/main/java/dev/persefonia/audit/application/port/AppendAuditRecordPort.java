package dev.persefonia.audit.application.port;

import dev.persefonia.audit.application.command.AppendAuditRecordCommand;

/**
 * Application port for appending an audit record. This is the only audit write
 * surface exposed to source command integrations; it exposes no JDBC, framework,
 * child-table, or repository internals.
 */
public interface AppendAuditRecordPort {
    void append(AppendAuditRecordCommand command);
}
