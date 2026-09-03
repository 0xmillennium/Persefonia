package dev.persefonia.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.audit.application.port.AuditRecordReadPort;
import dev.persefonia.audit.application.query.AuditRecordDetail;
import dev.persefonia.audit.application.query.AuditRecordListItem;
import dev.persefonia.audit.application.query.AuditRecordListPage;
import dev.persefonia.audit.application.query.AuditSearchRequest;
import dev.persefonia.audit.application.service.AuditQueryService;
import dev.persefonia.audit.domain.record.AuditAction;
import dev.persefonia.audit.domain.record.AuditActorType;
import dev.persefonia.audit.domain.record.AuditRecordId;
import dev.persefonia.audit.domain.record.AuditValidationException;
import dev.persefonia.audit.domain.record.SourceContext;
import dev.persefonia.audit.domain.record.SourceEntityId;
import dev.persefonia.audit.domain.record.SourceType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditQueryServiceTest {
    private final RecordingReadPort records = new RecordingReadPort();
    private final AuditQueryService service = new AuditQueryService(records);

    @Test
    void noFilterRequestUsesFirstPageDefaultsAndIsDelegated() {
        AuditSearchRequest request = AuditSearchRequest.firstPage();
        AuditRecordListPage result = service.search(request);
        assertThat(records.lastRequest).isSameAs(request);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(25);
    }

    @Test
    void validTypedFiltersAreRetained() {
        UUID actorId = UUID.randomUUID();
        AuditSearchRequest request = request(
                AuditAction.of("content.published"), AuditActorType.ADMIN, SourceEntityId.from(actorId),
                SourceContext.of("publishing"), SourceType.of("content_item"),
                SourceEntityId.from(UUID.randomUUID()),
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"), 2, 50);
        service.search(request);
        assertThat(records.lastRequest.action().value()).isEqualTo("content.published");
        assertThat(records.lastRequest.actorId().value()).isEqualTo(actorId);
        assertThat(records.lastRequest.page()).isEqualTo(2);
        assertThat(records.lastRequest.pageSize()).isEqualTo(50);
    }

    @Test
    void systemActorWithIdIsInvalid() {
        assertThatThrownBy(() -> request(
                null, AuditActorType.SYSTEM, SourceEntityId.from(UUID.randomUUID()),
                null, null, null, null, null, 1, 25))
                .isInstanceOf(AuditValidationException.class);
    }

    @Test
    void entityFiltersMustBeHierarchical() {
        assertThatThrownBy(() -> request(null, null, null, null, SourceType.of("content_item"), null,
                null, null, 1, 25)).isInstanceOf(AuditValidationException.class);
        assertThatThrownBy(() -> request(null, null, null, SourceContext.of("publishing"), null,
                SourceEntityId.from(UUID.randomUUID()), null, null, 1, 25))
                .isInstanceOf(AuditValidationException.class);
    }

    @Test
    void timeRangeMustBeStrictlyIncreasing() {
        Instant time = Instant.parse("2026-09-01T00:00:00Z");
        assertThatThrownBy(() -> request(null, null, null, null, null, null, time, time, 1, 25))
                .isInstanceOf(AuditValidationException.class);
        assertThatThrownBy(() -> request(null, null, null, null, null, null, time, time.minusSeconds(1), 1, 25))
                .isInstanceOf(AuditValidationException.class);
    }

    @Test
    void paginationIsStrictInApplicationRequest() {
        assertThatThrownBy(() -> request(null, null, null, null, null, null, null, null, 0, 25))
                .isInstanceOf(AuditValidationException.class);
        assertThatThrownBy(() -> request(null, null, null, null, null, null, null, null, 1, 0))
                .isInstanceOf(AuditValidationException.class);
        assertThatThrownBy(() -> request(null, null, null, null, null, null, null, null, 1, 101))
                .isInstanceOf(AuditValidationException.class);
    }

    @Test
    void listPageDefensivelyCopiesItems() {
        List<AuditRecordListItem> mutable = new ArrayList<>();
        AuditRecordListPage page = new AuditRecordListPage(mutable, 1, 25, 0);
        mutable.add(new AuditRecordListItem(
                UUID.randomUUID(), "content.published", "SYSTEM", "System", "publishing",
                "content_item", UUID.randomUUID(), Instant.EPOCH));
        assertThat(page.items()).isEmpty();
        assertThatThrownBy(() -> page.items().add(mutable.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findByIdDelegatesToReadPort() {
        AuditRecordId id = AuditRecordId.newId();
        service.findById(id);
        assertThat(records.lastId).isEqualTo(id);
    }

    private static AuditSearchRequest request(
            AuditAction action, AuditActorType actorType, SourceEntityId actorId,
            SourceContext entityContext, SourceType entityType, SourceEntityId entityId,
            Instant from, Instant to, int page, int pageSize) {
        return new AuditSearchRequest(
                action, actorType, actorId, entityContext, entityType, entityId, from, to, page, pageSize);
    }

    private static final class RecordingReadPort implements AuditRecordReadPort {
        private AuditSearchRequest lastRequest;
        private AuditRecordId lastId;

        @Override
        public AuditRecordListPage search(AuditSearchRequest request) {
            lastRequest = request;
            return new AuditRecordListPage(List.of(), request.page(), request.pageSize(), 0);
        }

        @Override
        public Optional<AuditRecordDetail> findById(AuditRecordId id) {
            lastId = id;
            return Optional.empty();
        }
    }
}
