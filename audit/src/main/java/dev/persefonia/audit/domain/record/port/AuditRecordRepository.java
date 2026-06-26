package dev.persefonia.audit.domain.record.port;

import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.AuditRecordId;
import java.util.List;
import java.util.Optional;

/**
 * Append-only persistence contract for the {@link AuditRecord} aggregate root.
 * There is no save, update, delete, remove, replace, or archive operation, and
 * there is no separate contract for audit changes or metadata.
 */
public interface AuditRecordRepository {
    void append(AuditRecord record);

    Optional<AuditRecord> findById(AuditRecordId id);

    List<AuditRecord> findRecent(int limit);
}
