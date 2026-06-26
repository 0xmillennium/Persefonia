package dev.persefonia.audit.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditChangeAndRecordTest {
    private static final Instant OCCURRED_AT = Instant.parse("2026-06-25T10:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-06-25T10:00:01Z");

    @Test
    void changeWithOldValueOnlyIsAccepted() {
        AuditChange change = AuditChange.of(FieldPath.of("title"), SafeAuditValue.of("Old title"), null);

        assertThat(change.oldValueOptional()).isPresent();
        assertThat(change.newValueOptional()).isEmpty();
    }

    @Test
    void changeWithNewValueOnlyIsAccepted() {
        AuditChange change = AuditChange.of(FieldPath.of("title"), null, SafeAuditValue.of("New title"));

        assertThat(change.oldValueOptional()).isEmpty();
        assertThat(change.newValueOptional()).isPresent();
    }

    @Test
    void changeWithOldAndNewValuesIsAccepted() {
        AuditChange change = AuditChange.of(
                FieldPath.of("title"), SafeAuditValue.of("Old title"), SafeAuditValue.of("New title"));

        assertThat(change.oldValueOptional()).isPresent();
        assertThat(change.newValueOptional()).isPresent();
    }

    @Test
    void changeWithNeitherValueIsRejected() {
        assertThatThrownBy(() -> AuditChange.of(FieldPath.of("title"), null, null))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("old value or a new value");
    }

    @Test
    void duplicateChangeFieldPathIsRejected() {
        List<AuditChange> changes = List.of(
                AuditChange.of(FieldPath.of("title"), null, SafeAuditValue.of("First")),
                AuditChange.of(FieldPath.of("title"), null, SafeAuditValue.of("Second")));

        assertThatThrownBy(() -> record(changes, List.of()))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("duplicate change field paths");
    }

    @Test
    void duplicateMetadataKeyIsRejected() {
        List<AuditMetadataEntry> metadata = List.of(
                AuditMetadataEntry.of(MetadataKey.of("reason"), SafeMetadataValue.of("first")),
                AuditMetadataEntry.of(MetadataKey.of("reason"), SafeMetadataValue.of("second")));

        assertThatThrownBy(() -> record(List.of(), metadata))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("duplicate metadata keys");
    }

    @Test
    void recordDefensivelyCopiesChanges() {
        List<AuditChange> mutable = new ArrayList<>();
        mutable.add(AuditChange.of(FieldPath.of("title"), null, SafeAuditValue.of("Title")));
        AuditRecord record = record(mutable, List.of());

        mutable.add(AuditChange.of(FieldPath.of("summary"), null, SafeAuditValue.of("Summary")));

        assertThat(record.changes()).hasSize(1);
    }

    @Test
    void recordDefensivelyCopiesMetadata() {
        List<AuditMetadataEntry> mutable = new ArrayList<>();
        mutable.add(AuditMetadataEntry.of(MetadataKey.of("reason"), SafeMetadataValue.of("manual")));
        AuditRecord record = record(List.of(), mutable);

        mutable.add(AuditMetadataEntry.of(MetadataKey.of("channel"), SafeMetadataValue.of("admin")));

        assertThat(record.metadata()).hasSize(1);
    }

    @Test
    void exposedChangesAreImmutable() {
        AuditRecord record = record(
                List.of(AuditChange.of(FieldPath.of("title"), null, SafeAuditValue.of("Title"))), List.of());

        assertThatThrownBy(() -> record.changes().add(
                AuditChange.of(FieldPath.of("summary"), null, SafeAuditValue.of("Summary"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recordExposesNoBusinessUpdateOrDeleteWorkflow() {
        List<String> mutatingMethods = new ArrayList<>();
        for (Method method : AuditRecord.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            if (name.startsWith("update")
                    || name.startsWith("delete")
                    || name.startsWith("remove")
                    || name.startsWith("archive")
                    || name.startsWith("replace")
                    || name.startsWith("set")) {
                mutatingMethods.add(method.getName());
            }
        }

        assertThat(mutatingMethods).isEmpty();
    }

    private static AuditRecord record(List<AuditChange> changes, List<AuditMetadataEntry> metadata) {
        return AuditRecord.create(
                AuditRecordId.newId(),
                AuditAction.of("content.published"),
                AuditActorRef.system(DisplayName.of("System")),
                AuditedEntityRef.of("publishing", "content_item", UUID.randomUUID()),
                null,
                OCCURRED_AT,
                CREATED_AT,
                changes,
                metadata);
    }
}
