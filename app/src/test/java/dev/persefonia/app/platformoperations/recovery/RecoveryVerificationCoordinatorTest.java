package dev.persefonia.app.platformoperations.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.persefonia.medialibrary.application.recovery.MediaRecoveryConsistencyReport;
import dev.persefonia.medialibrary.application.recovery.MediaRecoveryConsistencyService;
import dev.persefonia.platformoperations.application.operations.*;
import dev.persefonia.platformoperations.application.recovery.*;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RecoveryVerificationCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Test
    void coherentDurableStateIsConsistentEvenWhenRedisIsDown() {
        RecoveryVerificationCoordinator coordinator = coordinator(
                MigrationStatus.UP_TO_DATE, OperationsComponentStatus.UP,
                new MediaRecoveryConsistencyReport(2, 2, 0, 0, 0, 0, List.of(), false),
                new DurableAssetReferenceIntegritySummary(3, 0, List.of(), false));

        RecoveryVerificationReport report = coordinator.verify();

        assertThat(report.status()).isEqualTo(RecoveryVerificationStatus.CONSISTENT);
        assertThat(report.generatedAt()).isEqualTo(NOW);
        assertThat(report.context().release().applicationVersion()).isEqualTo("0.1.0-SNAPSHOT");
    }

    @Test
    void durableFailuresAndMigrationDriftAreInconsistentWhileIndeterminateInputsAreUnknown() {
        var mediaFailure = new MediaRecoveryConsistencyReport(1, 0, 0, 0, 1, 1,
                List.of(new dev.persefonia.medialibrary.application.recovery.MediaRecoveryIssue(
                        dev.persefonia.medialibrary.application.recovery.MediaRecoveryIssueCategory.CHECKSUM_MISMATCH,
                        dev.persefonia.medialibrary.application.recovery.MediaRecoveryObjectKind.ORIGINAL,
                        dev.persefonia.medialibrary.domain.asset.AssetId.newId(), null)), false);
        assertThat(coordinator(MigrationStatus.UP_TO_DATE, OperationsComponentStatus.UP, mediaFailure,
                new DurableAssetReferenceIntegritySummary(0, 0, List.of(), false)).verify().status())
                .isEqualTo(RecoveryVerificationStatus.INCONSISTENT);
        assertThat(coordinator(MigrationStatus.PENDING, OperationsComponentStatus.UP,
                emptyMedia(), new DurableAssetReferenceIntegritySummary(0, 0, List.of(), false)).verify().status())
                .isEqualTo(RecoveryVerificationStatus.INCONSISTENT);
        assertThat(coordinator(MigrationStatus.FAILED, OperationsComponentStatus.UP,
                emptyMedia(), new DurableAssetReferenceIntegritySummary(0, 0, List.of(), false)).verify().status())
                .isEqualTo(RecoveryVerificationStatus.INCONSISTENT);
        assertThat(coordinator(MigrationStatus.UP_TO_DATE, OperationsComponentStatus.DOWN,
                emptyMedia(), new DurableAssetReferenceIntegritySummary(0, 0, List.of(), false)).verify().status())
                .isEqualTo(RecoveryVerificationStatus.INCONSISTENT);
        assertThat(coordinator(MigrationStatus.UNKNOWN, OperationsComponentStatus.UP,
                emptyMedia(), new DurableAssetReferenceIntegritySummary(0, 0, List.of(), false)).verify().status())
                .isEqualTo(RecoveryVerificationStatus.UNKNOWN);
        assertThat(coordinator(MigrationStatus.UP_TO_DATE, OperationsComponentStatus.UNKNOWN,
                emptyMedia(), new DurableAssetReferenceIntegritySummary(0, 0, List.of(), false)).verify().status())
                .isEqualTo(RecoveryVerificationStatus.UNKNOWN);
    }

    private static RecoveryVerificationCoordinator coordinator(
            MigrationStatus migrationStatus,
            OperationsComponentStatus mediaStatus,
            MediaRecoveryConsistencyReport mediaReport,
            DurableAssetReferenceIntegritySummary references) {
        ApplicationReleaseInfoQueryPort release = () -> new ApplicationReleaseInfo("persefonia", "0.1.0-SNAPSHOT");
        OperationsHealthQueryPort health = () -> new OperationsHealthSnapshot(
                OperationsComponentStatus.UP, OperationsComponentStatus.UP, OperationsComponentStatus.DOWN,
                mediaStatus, CachePurgeProvider.CLOUDFLARE, OperationsComponentStatus.DOWN,
                new MigrationStatusSummary("21", "21", migrationStatus == MigrationStatus.PENDING ? 1 : 0,
                        migrationStatus));
        MediaRecoveryConsistencyService verifier = mock(MediaRecoveryConsistencyService.class);
        when(verifier.verify()).thenReturn(mediaReport);
        @SuppressWarnings("unchecked")
        ObjectProvider<MediaRecoveryConsistencyService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(verifier);
        DurableAssetReferenceIntegrityReadPort referencePort = () -> references;
        return new RecoveryVerificationCoordinator(
                release, health, provider, referencePort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MediaRecoveryConsistencyReport emptyMedia() {
        return new MediaRecoveryConsistencyReport(0, 0, 0, 0, 0, 0, List.of(), false);
    }
}
