package dev.persefonia.platformoperations.application.recovery;

import java.util.List;
import java.util.Objects;

public record DurableAssetReferenceIntegritySummary(
        long totalReferences,
        long danglingReferences,
        List<DurableAssetReferenceIssue> reportedIssues,
        boolean reportedIssuesTruncated) {
    public DurableAssetReferenceIntegritySummary {
        reportedIssues = List.copyOf(Objects.requireNonNull(reportedIssues, "reportedIssues"));
        if (totalReferences < 0 || danglingReferences < 0 || danglingReferences > totalReferences
                || reportedIssues.size() > danglingReferences
                || reportedIssuesTruncated != (danglingReferences > reportedIssues.size())) {
            throw new IllegalArgumentException("durable reference counts are inconsistent");
        }
    }
}
