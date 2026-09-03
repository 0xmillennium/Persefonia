package dev.persefonia.audit.application.port;

import dev.persefonia.audit.application.query.AuditRecordDetail;
import dev.persefonia.audit.application.query.AuditRecordListPage;
import dev.persefonia.audit.application.query.AuditSearchRequest;
import dev.persefonia.audit.domain.record.AuditRecordId;
import java.util.Optional;

/** Purpose-built read side for Audit reporting without aggregate hydration. */
public interface AuditRecordReadPort {
    AuditRecordListPage search(AuditSearchRequest request);

    Optional<AuditRecordDetail> findById(AuditRecordId id);
}
