package dev.persefonia.platformoperations.application.recovery;

import java.util.List;
import java.util.Objects;

public record RecoveryMediaIntegritySummary(
        long totalObjects,
        long verifiedObjects,
        long unavailableObjects,
        long sizeMismatchObjects,
        long checksumMismatchObjects,
        long issueCount,
        List<RecoveryMediaIssue> reportedIssues,
        boolean reportedIssuesTruncated) {
    public RecoveryMediaIntegritySummary {
        reportedIssues = List.copyOf(Objects.requireNonNull(reportedIssues, "reportedIssues"));
        if (totalObjects < 0 || verifiedObjects < 0 || unavailableObjects < 0
                || sizeMismatchObjects < 0 || checksumMismatchObjects < 0 || issueCount < 0
                || totalObjects != verifiedObjects + unavailableObjects + sizeMismatchObjects + checksumMismatchObjects
                || issueCount != unavailableObjects + sizeMismatchObjects + checksumMismatchObjects
                || reportedIssues.size() > issueCount
                || reportedIssuesTruncated != (issueCount > reportedIssues.size())) {
            throw new IllegalArgumentException("recovery Media counts are inconsistent");
        }
    }
}
