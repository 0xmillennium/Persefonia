package dev.persefonia.audit.application;

import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.AuditRecordId;
import dev.persefonia.audit.domain.record.port.AuditRecordRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class RecordingAuditRecordRepository implements AuditRecordRepository {
    final List<AuditRecord> appended = new ArrayList<>();

    @Override
    public void append(AuditRecord record) {
        appended.add(record);
    }

    @Override
    public Optional<AuditRecord> findById(AuditRecordId id) {
        return appended.stream().filter(record -> record.id().equals(id)).findFirst();
    }

    @Override
    public List<AuditRecord> findRecent(int limit) {
        return appended.stream().limit(limit).toList();
    }
}
