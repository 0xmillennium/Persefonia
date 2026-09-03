package dev.persefonia.webadmin.audit;

import dev.persefonia.audit.application.query.AuditRecordDetail;
import java.util.Objects;

public record AdminAuditDetailPage(AdminAuditPageChrome chrome, AuditRecordDetail record) {
    public AdminAuditDetailPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(record, "record");
    }
}
