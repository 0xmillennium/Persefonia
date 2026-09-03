package dev.persefonia.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.audit.application.service.AuditAppendService;
import dev.persefonia.audit.application.service.AuditRecordFactory;
import dev.persefonia.audit.application.service.AuditSafeValuePolicy;
import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.AuditRecordId;
import dev.persefonia.audit.domain.record.AuditValidationException;
import dev.persefonia.audit.domain.record.port.AuditRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuditAppendServiceTest {
    private final RecordingAuditRecordRepository repository = new RecordingAuditRecordRepository();
    private final AuditAppendService service = new AuditAppendService(
            new AuditRecordFactory(
                    new AuditSafeValuePolicy(),
                    Clock.fixed(Instant.parse("2026-06-25T10:00:05Z"), ZoneOffset.UTC)),
            repository);

    @Test
    void appendsSafeCommandToRepository() {
        service.append(AuditCommands.safeAdminCommand());

        assertThat(repository.appended).hasSize(1);
    }

    @Test
    void rejectsUnsafeCommandBeforeRepositoryAppend() {
        assertThatThrownBy(() -> service.append(AuditCommands.unsafeValueCommand()))
                .isInstanceOf(AuditValidationException.class);

        assertThat(repository.appended).isEmpty();
    }

    @Test
    void rejectsSensitiveKeyBeforeRepositoryAppend() {
        assertThatThrownBy(() -> service.append(AuditCommands.unsafeKeyCommand()))
                .isInstanceOf(AuditValidationException.class);

        assertThat(repository.appended).isEmpty();
    }

    @Test
    void rejectsRawIpBeforeRepositoryAppend() {
        assertThatThrownBy(() -> service.append(AuditCommands.rawIpValueCommand()))
                .isInstanceOf(AuditValidationException.class);

        assertThat(repository.appended).isEmpty();
    }

    @Test
    void propagatesRepositoryFailureWithoutSwallowing() {
        AuditAppendService failing = new AuditAppendService(
                new AuditRecordFactory(
                        new AuditSafeValuePolicy(),
                        Clock.fixed(Instant.parse("2026-06-25T10:00:05Z"), ZoneOffset.UTC)),
                new FailingAuditRecordRepository());

        assertThatThrownBy(() -> failing.append(AuditCommands.safeAdminCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("append failed");
    }

    private static final class FailingAuditRecordRepository implements AuditRecordRepository {
        @Override
        public void append(AuditRecord record) {
            throw new IllegalStateException("append failed");
        }

        @Override
        public Optional<AuditRecord> findById(AuditRecordId id) {
            return Optional.empty();
        }

        @Override
        public List<AuditRecord> findRecent(int limit) {
            return List.of();
        }
    }
}
