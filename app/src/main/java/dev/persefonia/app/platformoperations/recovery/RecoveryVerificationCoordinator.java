package dev.persefonia.app.platformoperations.recovery;

import dev.persefonia.medialibrary.application.recovery.MediaRecoveryConsistencyReport;
import dev.persefonia.medialibrary.application.recovery.MediaRecoveryConsistencyService;
import dev.persefonia.platformoperations.application.operations.*;
import dev.persefonia.platformoperations.application.recovery.*;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class RecoveryVerificationCoordinator implements RecoveryVerificationQueryPort {
    private final ApplicationReleaseInfoQueryPort release;
    private final OperationsHealthQueryPort health;
    private final ObjectProvider<MediaRecoveryConsistencyService> mediaVerifier;
    private final DurableAssetReferenceIntegrityReadPort references;
    private final Clock clock;

    public RecoveryVerificationCoordinator(
            ApplicationReleaseInfoQueryPort release,
            OperationsHealthQueryPort health,
            ObjectProvider<MediaRecoveryConsistencyService> mediaVerifier,
            DurableAssetReferenceIntegrityReadPort references,
            Clock clock) {
        this.release = Objects.requireNonNull(release, "release");
        this.health = Objects.requireNonNull(health, "health");
        this.mediaVerifier = Objects.requireNonNull(mediaVerifier, "mediaVerifier");
        this.references = Objects.requireNonNull(references, "references");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RecoveryVerificationContext context() {
        OperationsHealthSnapshot snapshot = health.snapshot();
        return new RecoveryVerificationContext(release.releaseInfo(), snapshot.migrations(), snapshot.mediaStorage());
    }

    @Override
    public RecoveryVerificationReport verify() {
        RecoveryVerificationContext context;
        try {
            context = context();
        } catch (RuntimeException failure) {
            return unknownContextReport();
        }
        if (context.migrations().status() == MigrationStatus.UNKNOWN
                || context.mediaStorage() == OperationsComponentStatus.UNKNOWN) {
            return unknownReport(context);
        }
        MediaRecoveryConsistencyService verifier = mediaVerifier.getIfAvailable();
        if (verifier == null || context.mediaStorage() == OperationsComponentStatus.DOWN) {
            return inconsistentUnavailableReport(context);
        }
        try {
            RecoveryMediaIntegritySummary media = map(verifier.verify());
            DurableAssetReferenceIntegritySummary referenceSummary = references.verify();
            boolean inconsistent = context.migrations().status() != MigrationStatus.UP_TO_DATE
                    || context.mediaStorage() != OperationsComponentStatus.UP
                    || media.issueCount() > 0
                    || referenceSummary.danglingReferences() > 0;
            return new RecoveryVerificationReport(context,
                    inconsistent ? RecoveryVerificationStatus.INCONSISTENT : RecoveryVerificationStatus.CONSISTENT,
                    clock.instant(), media, referenceSummary);
        } catch (RuntimeException failure) {
            return unknownReport(context);
        }
    }

    private RecoveryVerificationReport unknownContextReport() {
        var context = new RecoveryVerificationContext(
                release.releaseInfo(), new MigrationStatusSummary(null, null, 0, MigrationStatus.UNKNOWN),
                OperationsComponentStatus.UNKNOWN);
        return unknownReport(context);
    }

    private RecoveryVerificationReport unknownReport(RecoveryVerificationContext context) {
        return new RecoveryVerificationReport(context, RecoveryVerificationStatus.UNKNOWN, clock.instant(),
                emptyMedia(), emptyReferences());
    }

    private RecoveryVerificationReport inconsistentUnavailableReport(RecoveryVerificationContext context) {
        return new RecoveryVerificationReport(context, RecoveryVerificationStatus.INCONSISTENT, clock.instant(),
                emptyMedia(), emptyReferences());
    }

    private static RecoveryMediaIntegritySummary map(MediaRecoveryConsistencyReport report) {
        List<RecoveryMediaIssue> issues = report.reportedIssues().stream()
                .map(issue -> new RecoveryMediaIssue(
                        RecoveryMediaIssueCategory.valueOf(issue.category().name()),
                        RecoveryMediaObjectKind.valueOf(issue.objectKind().name()),
                        issue.assetId().value(), issue.variantName()))
                .toList();
        return new RecoveryMediaIntegritySummary(
                report.totalObjects(), report.verifiedObjects(), report.unavailableObjects(),
                report.sizeMismatchObjects(), report.checksumMismatchObjects(), report.issueCount(),
                issues, report.reportedIssuesTruncated());
    }

    private static RecoveryMediaIntegritySummary emptyMedia() {
        return new RecoveryMediaIntegritySummary(0, 0, 0, 0, 0, 0, List.of(), false);
    }
    private static DurableAssetReferenceIntegritySummary emptyReferences() {
        return new DurableAssetReferenceIntegritySummary(0, 0, List.of(), false);
    }
}
