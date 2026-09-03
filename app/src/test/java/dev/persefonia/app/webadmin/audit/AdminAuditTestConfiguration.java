package dev.persefonia.app.webadmin.audit;

import dev.persefonia.audit.application.port.AuditQueryPort;
import dev.persefonia.audit.application.query.AuditChangeView;
import dev.persefonia.audit.application.query.AuditMetadataView;
import dev.persefonia.audit.application.query.AuditRecordDetail;
import dev.persefonia.audit.application.query.AuditRecordListItem;
import dev.persefonia.audit.application.query.AuditRecordListPage;
import dev.persefonia.audit.application.query.AuditSearchRequest;
import dev.persefonia.audit.domain.record.AuditRecordId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("admin-audit-mvc-test")
class AdminAuditTestConfiguration {
    static final UUID RECORD_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID ENTITY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Bean
    @Primary
    CapturingAuditQueryPort testAuditQueryPort() {
        return new CapturingAuditQueryPort();
    }

    static final class CapturingAuditQueryPort implements AuditQueryPort {
        AuditSearchRequest request;

        @Override
        public AuditRecordListPage search(AuditSearchRequest request) {
            this.request = request;
            return new AuditRecordListPage(List.of(new AuditRecordListItem(
                    RECORD_ID, "content.published", "ADMIN", "<script>alert(1)</script>",
                    "publishing", "content_item", ENTITY_ID,
                    Instant.parse("2026-09-03T17:00:00Z"))), request.page(), request.pageSize(), 30);
        }

        @Override
        public Optional<AuditRecordDetail> findById(AuditRecordId id) {
            if (!id.value().equals(RECORD_ID)) {
                return Optional.empty();
            }
            return Optional.of(new AuditRecordDetail(
                    RECORD_ID, "content.published", "ADMIN", "iam", "admin_account", ACTOR_ID,
                    "Jane Admin", "publishing", "content_item", ENTITY_ID, "request-123",
                    Instant.parse("2026-09-03T17:00:00Z"), Instant.parse("2026-09-03T17:00:01Z"),
                    List.of(new AuditChangeView("status", "DRAFT", "PUBLISHED")),
                    List.of(new AuditMetadataView("reason", "manual review"))));
        }
    }
}
