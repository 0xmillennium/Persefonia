package dev.persefonia.audit.application.port;

import dev.persefonia.audit.application.query.AuditRecordDetail;
import dev.persefonia.audit.application.query.AuditRecordListItem;
import dev.persefonia.audit.domain.record.AuditRecordId;
import java.util.List;
import java.util.Optional;

/**
 * Minimal read surface over audit records. It supports a single record lookup and
 * a bounded recent listing. It deliberately offers no advanced filtering,
 * pagination, full-text search, or export.
 */
public interface AuditQueryPort {
    Optional<AuditRecordDetail> findById(AuditRecordId id);

    List<AuditRecordListItem> findRecent(int limit);
}
