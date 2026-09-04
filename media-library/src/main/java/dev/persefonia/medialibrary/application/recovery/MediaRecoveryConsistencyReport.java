package dev.persefonia.medialibrary.application.recovery;

import java.util.List;
import java.util.Objects;

public record MediaRecoveryConsistencyReport(
        long totalObjects,
        long verifiedObjects,
        long unavailableObjects,
        long sizeMismatchObjects,
        long checksumMismatchObjects,
        long issueCount,
        List<MediaRecoveryIssue> reportedIssues,
        boolean reportedIssuesTruncated) {
    public MediaRecoveryConsistencyReport {
        reportedIssues = List.copyOf(Objects.requireNonNull(reportedIssues, "reportedIssues"));
        if (totalObjects < 0 || verifiedObjects < 0 || unavailableObjects < 0
                || sizeMismatchObjects < 0 || checksumMismatchObjects < 0 || issueCount < 0) {
            throw new IllegalArgumentException("recovery counts cannot be negative");
        }
        if (totalObjects != verifiedObjects + unavailableObjects + sizeMismatchObjects + checksumMismatchObjects
                || issueCount != unavailableObjects + sizeMismatchObjects + checksumMismatchObjects
                || reportedIssues.size() > issueCount
                || reportedIssuesTruncated != (issueCount > reportedIssues.size())) {
            throw new IllegalArgumentException("recovery counts are inconsistent");
        }
    }
}
