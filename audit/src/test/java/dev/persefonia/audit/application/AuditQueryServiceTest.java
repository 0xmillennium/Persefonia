package dev.persefonia.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.audit.application.query.AuditRecordDetail;
import dev.persefonia.audit.application.service.AuditQueryService;
import dev.persefonia.audit.application.service.AuditRecordFactory;
import dev.persefonia.audit.application.service.AuditSafeValuePolicy;
import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.AuditValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuditQueryServiceTest {
    private final RecordingAuditRecordRepository repository = new RecordingAuditRecordRepository();
    private final AuditRecordFactory factory = new AuditRecordFactory(
            new AuditSafeValuePolicy(),
            Clock.fixed(Instant.parse("2026-06-25T10:00:05Z"), ZoneOffset.UTC));
    private final AuditQueryService service = new AuditQueryService(repository);

    @Test
    void findByIdReturnsSafeDetail() {
        AuditRecord record = factory.create(AuditCommands.safeAdminCommand());
        repository.append(record);

        Optional<AuditRecordDetail> detail = service.findById(record.id());

        assertThat(detail).isPresent();
        assertThat(detail.get().action()).isEqualTo("content.published");
        assertThat(detail.get().changes())
                .extracting(view -> view.fieldPath())
                .containsExactly("status", "title");
        assertThat(detail.get().metadata())
                .extracting(view -> view.key())
                .containsExactly("reason");
    }

    @Test
    void findByIdReturnsEmptyForMissingRecord() {
        assertThat(service.findById(dev.persefonia.audit.domain.record.AuditRecordId.newId())).isEmpty();
    }

    @Test
    void findRecentRejectsLimitBelowOne() {
        assertThatThrownBy(() -> service.findRecent(0))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("at least 1");
    }

    @Test
    void findRecentCapsLimitAboveHundred() {
        CapturingRepository capturing = new CapturingRepository();
        AuditQueryService capped = new AuditQueryService(capturing);

        capped.findRecent(5000);

        assertThat(capturing.requestedLimit).isEqualTo(100);
    }

    private static final class CapturingRepository extends RecordingAuditRecordRepository {
        private int requestedLimit;

        @Override
        public java.util.List<AuditRecord> findRecent(int limit) {
            this.requestedLimit = limit;
            return java.util.List.of();
        }
    }
}
