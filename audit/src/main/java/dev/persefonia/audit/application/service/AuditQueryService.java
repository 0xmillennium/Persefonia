package dev.persefonia.audit.application.service;

import dev.persefonia.audit.application.port.AuditQueryPort;
import dev.persefonia.audit.application.port.AuditRecordReadPort;
import dev.persefonia.audit.application.query.AuditRecordDetail;
import dev.persefonia.audit.application.query.AuditRecordListPage;
import dev.persefonia.audit.application.query.AuditSearchRequest;
import dev.persefonia.audit.domain.record.AuditRecordId;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordinates validated application queries with the dedicated Audit read side.
 */
public final class AuditQueryService implements AuditQueryPort {
    private final AuditRecordReadPort records;

    public AuditQueryService(AuditRecordReadPort records) {
        this.records = Objects.requireNonNull(records, "records");
    }

    @Override
    public Optional<AuditRecordDetail> findById(AuditRecordId id) {
        Objects.requireNonNull(id, "id");
        return records.findById(id);
    }

    @Override
    public AuditRecordListPage search(AuditSearchRequest request) {
        return records.search(Objects.requireNonNull(request, "request"));
    }
}
