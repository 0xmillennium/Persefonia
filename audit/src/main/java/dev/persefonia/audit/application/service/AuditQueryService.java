package dev.persefonia.audit.application.service;

import dev.persefonia.audit.application.port.AuditQueryPort;
import dev.persefonia.audit.application.query.AuditChangeView;
import dev.persefonia.audit.application.query.AuditMetadataView;
import dev.persefonia.audit.application.query.AuditRecordDetail;
import dev.persefonia.audit.application.query.AuditRecordListItem;
import dev.persefonia.audit.domain.record.AuditChange;
import dev.persefonia.audit.domain.record.AuditMetadataEntry;
import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.AuditRecordId;
import dev.persefonia.audit.domain.record.AuditValidationException;
import dev.persefonia.audit.domain.record.port.AuditRecordRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal read service over audit records. It enforces the bounded recent limit
 * and maps aggregates into safe read models. It implements no filtering,
 * pagination, or export.
 */
public final class AuditQueryService implements AuditQueryPort {
    private static final int MAX_RECENT_LIMIT = 100;

    private final AuditRecordRepository repository;

    public AuditQueryService(AuditRecordRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<AuditRecordDetail> findById(AuditRecordId id) {
        Objects.requireNonNull(id, "id");
        return repository.findById(id).map(AuditQueryService::toDetail);
    }

    @Override
    public List<AuditRecordListItem> findRecent(int limit) {
        if (limit < 1) {
            throw new AuditValidationException("recent limit must be at least 1");
        }
        int bounded = Math.min(limit, MAX_RECENT_LIMIT);
        return repository.findRecent(bounded).stream()
                .map(AuditQueryService::toListItem)
                .toList();
    }

    private static AuditRecordListItem toListItem(AuditRecord record) {
        return new AuditRecordListItem(
                record.id().value(),
                record.action().value(),
                record.actor().type().name(),
                record.actor().display().value(),
                record.entity().context().value(),
                record.entity().type().value(),
                record.entity().id().value(),
                record.occurredAt());
    }

    private static AuditRecordDetail toDetail(AuditRecord record) {
        return new AuditRecordDetail(
                record.id().value(),
                record.action().value(),
                record.actor().type().name(),
                record.actor().context().map(context -> context.value()).orElse(null),
                record.actor().sourceType().map(sourceType -> sourceType.value()).orElse(null),
                record.actor().id().map(id -> id.value()).orElse(null),
                record.actor().display().value(),
                record.entity().context().value(),
                record.entity().type().value(),
                record.entity().id().value(),
                record.requestId().map(requestId -> requestId.value()).orElse(null),
                record.occurredAt(),
                record.createdAt(),
                record.changes().stream().map(AuditQueryService::toChangeView).toList(),
                record.metadata().stream().map(AuditQueryService::toMetadataView).toList());
    }

    private static AuditChangeView toChangeView(AuditChange change) {
        return new AuditChangeView(
                change.fieldPath().value(),
                change.oldValueOptional().map(value -> value.value()).orElse(null),
                change.newValueOptional().map(value -> value.value()).orElse(null));
    }

    private static AuditMetadataView toMetadataView(AuditMetadataEntry entry) {
        return new AuditMetadataView(entry.key().value(), entry.value().value());
    }
}
